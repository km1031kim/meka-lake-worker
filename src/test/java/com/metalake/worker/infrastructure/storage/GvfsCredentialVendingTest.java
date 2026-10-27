package com.metalake.worker.infrastructure.storage;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Arrays;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

class GvfsCredentialVendingTest {

    private Configuration createConfiguration() {
        Configuration conf = new Configuration();

        conf.set(
                "fs.AbstractFileSystem.gvfs.impl",
                "org.apache.gravitino.filesystem.hadoop.Gvfs"
        );

        conf.set(
                "fs.gvfs.impl",
                "org.apache.gravitino.filesystem.hadoop.GravitinoVirtualFileSystem"
        );

        conf.set(
                "fs.gravitino.server.uri",
                "http://10.194.1.140:8090"
        );

        conf.set(
                "fs.gravitino.client.metalake",
                "ax"
        );

        // 핵심: Credential Vending 활성화
        conf.setBoolean(
                "fs.gravitino.enableCredentialVending",
                true
        );

        // MinIO Path Style
        conf.setBoolean("s3-path-style-access", true);

        // GVFS 공유 FileSystem 캐시 비활성화
        conf.setBoolean("fs.gvfs.impl.disable.cache", true);

        // 클라이언트에 직접 인증 정보를 설정하지 않는다.
        return conf;
    }

    @Test
    void minioA() throws Exception {
        verifyFileRead(
                "gvfs://fileset/source/object-storage/source_5/"
        );
    }

    @Test
    void minioB() throws Exception {
        verifyFileRead(
                "gvfs://fileset/source/minio/minio_source/"
        );
    }

    @Test
    void minioBWithCatalogCredential() throws Exception {
        verifyFileRead(
                "gvfs://fileset/source_b_test/object-storage/minio_b/"
        );
    }

    @Test
    void multipleCatalogsInSameJvm() throws Exception {

        System.out.println("=== MinIO A (first) ===");
        verifyFileRead(
                "gvfs://fileset/source/object-storage/source_5/"
        );

        System.out.println("=== MinIO B ===");
        verifyFileRead(
                "gvfs://fileset/source_b_test/object-storage/minio_b/"
        );

        System.out.println("=== MinIO A (again) ===");
        verifyFileRead(
                "gvfs://fileset/source/object-storage/source_5/"
        );
    }

    private void verifyFileRead(String uri) throws Exception {
        Configuration conf = createConfiguration();
        Path root = new Path(uri);

        try (FileSystem fs =
                     FileSystem.newInstance(root.toUri(), conf)) {

            // 1. Fileset 파일 목록
            FileStatus[] statuses = fs.listStatus(root);

            System.out.println("GVFS Root: " + root);

            Arrays.stream(statuses)
                    .forEach(s -> System.out.println(
                            "Path: " + s.getPath()
                                    + ", Directory: " + s.isDirectory()
                    ));

            // 2. 파일 하나 선택
            FileStatus firstFile = Arrays.stream(statuses)
                    .filter(FileStatus::isFile)
                    .findFirst()
                    .orElseThrow(() ->
                            new IllegalStateException(
                                    "No file in Fileset root: " + uri
                            )
                    );

            // 3. 실제 파일 데이터 읽기
            try (InputStream input =
                         fs.open(firstFile.getPath())) {

                byte[] bytes = input.readNBytes(16);

                assertTrue(bytes.length > 0);

                System.out.println("Read: " + firstFile.getPath());
                System.out.println(
                        "Header: " + HexFormat.of().formatHex(bytes)
                );
            }
        }
    }
}
