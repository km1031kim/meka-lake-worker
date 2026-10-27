package com.metalake.worker.application;

import static org.junit.jupiter.api.Assertions.*;

import com.metalake.worker.domain.source.entity.SourceStatus;
import com.metalake.worker.domain.source.repository.SourceResourceRepository;
import com.metalake.worker.infrastructure.gravitino.GravitinoClient;

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
    void registerMinio() {

        String accessKey = System.getenv("TEST_MINIO_ACCESS_KEY");
        String secretKey = System.getenv("TEST_MINIO_SECRET_KEY");

        assumeTrue(
                accessKey != null && !accessKey.isBlank()
                        && secretKey != null && !secretKey.isBlank(),
                "Test credentials are required"
        );

        RegisterMinioSourceCommand command =
                new RegisterMinioSourceCommand(
                        "AI MinIO Source",
                        "http://10.194.1.7:9000",
                        "ds-ai-platform",
                        "test_dr",
                        accessKey,
                        secretKey
                );

        // 소스 등록
        RegisteredSource result =
                registrationService.registerMinio(command);

        // PostgreSQL 검증
        var source = sourceRepository
                .findById(result.sourceId())
                .orElseThrow();

        assertEquals(SourceStatus.ACTIVE, source.getStatus());
        assertEquals("source", source.getGravitinoCatalog());
        assertEquals("object-storage", source.getGravitinoSchema());
        assertEquals(result.fileset(), source.getGravitinoFileset());

        // Gravitino 검증
        var fileset = gravitinoClient.loadFileset(
                result.catalog(),
                result.schema(),
                result.fileset()
        );

        assertEquals(result.fileset(), fileset.name());
        assertEquals(
                "s3a://ds-ai-platform/test_dr",
                fileset.resolvedStorageLocation()
        );

        System.out.println(
                "Source ID: " + result.sourceId()
        );
        System.out.println(
                "Fileset: " + result.fileset()
        );
    }
}
