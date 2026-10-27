
package com.metalake.worker.infrastructure.gravitino;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@SpringBootTest
class   GravitinoCatalogIntegrationTest {

    @Autowired
    private GravitinoClient gravitinoClient;

    @Test
    void createCatalogAndSchema() {

        String accessKey =
                System.getenv("TEST_MINIO_ACCESS_KEY");

        String secretKey =
                System.getenv("TEST_MINIO_SECRET_KEY");

        assumeTrue(
                accessKey != null && !accessKey.isBlank()
                        && secretKey != null && !secretKey.isBlank(),
                "MinIO credentials are required"
        );

        String catalog = "source_poc_"
                + UUID.randomUUID().toString()
                .substring(0, 8);

        String schema = "object-storage";

        // 1. Catalog 설정
        Map<String, String> properties = Map.of(
                "s3-endpoint", "http://10.194.1.7:9000",
                "s3-access-key-id", accessKey,
                "s3-secret-access-key", secretKey,
                "s3-region", "us-east-1",
                "s3-path-style-access", "true",
                "credential-providers", "s3-secret-key"
        );

        // 2. Catalog 생성
        gravitinoClient.createFilesetCatalog(
                catalog,
                properties
        );

        // 3. Schema 생성
        gravitinoClient.createSchema(
                catalog,
                schema
        );

        // 4. Schema 조회 검증
        List<String> schemas =
                gravitinoClient.listSchemas(catalog);

        assertTrue(schemas.contains(schema));

        System.out.println(
                "Created Catalog: ax." + catalog
        );

        System.out.println(
                "Created Schema: ax." + catalog + "." + schema
        );
    }
}
