package com.metalake.worker.application;

import static org.junit.jupiter.api.Assertions.*;

import com.metalake.worker.domain.source.entity.SourceResource;
import com.metalake.worker.domain.source.entity.SourceStatus;
import com.metalake.worker.domain.source.repository.SourceResourceRepository;
import com.metalake.worker.infrastructure.gravitino.GravitinoClient;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.Path;

import java.io.IOException;
import java.util.Arrays;
import com.metalake.worker.infrastructure.gravitino.GravitinoFileset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assumptions.assumeTrue;


@SpringBootTest
class SourceRegistrationServiceIntegrationTest {

    @Autowired
    private SourceRegistrationService registrationService;

    @Autowired
    private SourceResourceRepository sourceRepository;

    @Autowired
    private GravitinoClient gravitinoClient;

    @Test
    void registerMinioWithDedicatedCatalog() throws IOException {

        String accessKey =
                System.getenv("TEST_MINIO_ACCESS_KEY");

        String secretKey =
                System.getenv("TEST_MINIO_SECRET_KEY");

        assumeTrue(
                accessKey != null && !accessKey.isBlank()
                        && secretKey != null && !secretKey.isBlank()
        );

        // 1. 소스 등록
        RegisteredSource result =
                registrationService.registerMinio(
                        new RegisterMinioSourceCommand(
                                "AI MinIO Source",
                                "http://10.194.1.7:9000",
                                "ds-ai-platform",
                                "test_dr",
                                accessKey,
                                secretKey
                        )
                );

        // 2. JPA 검증
        SourceResource source = sourceRepository
                .findById(result.sourceId())
                .orElseThrow();

        assertEquals(
                SourceStatus.ACTIVE,
                source.getStatus()
        );

        assertEquals(
                "source_" + result.sourceId(),
                source.getGravitinoCatalog()
        );

        assertEquals(
                "object-storage",
                source.getGravitinoSchema()
        );

        assertEquals(
                "fileset_" + result.sourceId(),
                source.getGravitinoFileset()
        );

        // 3. Schema 생성 확인
        assertTrue(
                gravitinoClient
                        .listSchemas(result.catalog())
                        .contains("object-storage")
        );

        // 4. Fileset 생성 확인
        GravitinoFileset fileset =
                gravitinoClient.loadFileset(
                        result.catalog(),
                        result.schema(),
                        result.fileset()
                );

        assertEquals(
                "s3a://ds-ai-platform/test_dr",
                fileset.resolvedStorageLocation()
        );

        System.out.println(
                "Source ID: " + result.sourceId()
        );
        System.out.println(
                "Catalog: " + result.catalog()
        );
        System.out.println(
                "Schema: " + result.schema()
        );
        System.out.println(
                "Fileset: " + result.fileset()
        );

        String gvfsUri = String.format(
                "gvfs://fileset/%s/%s/%s/",
                result.catalog(),
                result.schema(),
                result.fileset()
        );

        Configuration conf = new Configuration();

        conf.set(
                "fs.gvfs.impl",
                "org.apache.gravitino.filesystem.hadoop.GravitinoVirtualFileSystem"
        );

        conf.set(
                "fs.AbstractFileSystem.gvfs.impl",
                "org.apache.gravitino.filesystem.hadoop.Gvfs"
        );

        conf.set(
                "fs.gravitino.server.uri",
                "http://10.194.1.140:8090"
        );

        conf.set(
                "fs.gravitino.client.metalake",
                result.metalake()
        );

        conf.setBoolean(
                "fs.gravitino.enableCredentialVending",
                true
        );

        conf.setBoolean(
                "fs.gvfs.impl.disable.cache",
                true
        );

        Path root = new Path(gvfsUri);

        try (FileSystem fs = FileSystem.newInstance(root.toUri(), conf)) {

            FileStatus[] statuses = fs.listStatus(root);

            System.out.println("GVFS: " + gvfsUri);

            FileStatus firstFile = Arrays.stream(statuses)
                    .filter(FileStatus::isFile)
                    .findFirst()
                    .orElseThrow(() ->
                            new IllegalStateException(
                                    "No files found: " + gvfsUri
                            )
                    );

            try (FSDataInputStream input = fs.open(firstFile.getPath())) {

                byte[] header = input.readNBytes(16);

                assertTrue(header.length > 0);

                System.out.println("Read: " + firstFile.getPath());
                System.out.println("Bytes: " + header.length);
            }
        }
    }
}
