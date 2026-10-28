package com.metalake.worker.infrastructure.iceberg;

import org.apache.iceberg.rest.RESTCatalog;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class IcebergCatalogConfig {

    @Bean(destroyMethod = "close")
    public RESTCatalog icebergCatalog() {

        Map<String, String> properties = new HashMap<>();

        // Gravitino Iceberg REST Catalog
        properties.put("uri", "http://10.194.1.140:9001/iceberg");

        // Iceberg S3 FileIO
        properties.put("io-impl",
                "org.apache.iceberg.aws.s3.S3FileIO");

        // MinIO 연결에 필요한 비밀이 아닌 설정
        properties.put("s3.endpoint", "http://10.194.1.140:9000");
        properties.put("s3.path-style-access", "true");
        properties.put("client.region", "us-east-1");

        // Gravitino Credential Vending 요청
        properties.put(
                "header.X-Iceberg-Access-Delegation",
                "vended-credentials"
        );

        RESTCatalog catalog = new RESTCatalog();
        catalog.initialize("ai-ready-catalog", properties);

        return catalog;
    }
}