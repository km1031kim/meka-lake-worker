
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

        properties.put("uri", "http://10.194.1.140:9001/iceberg");
        properties.put("io-impl", "org.apache.iceberg.aws.s3.S3FileIO");
        properties.put("s3.endpoint", "http://10.194.1.140:9000");
        properties.put("s3.path-style-access", "true");
        properties.put("client.region", "us-east-1");

        RESTCatalog catalog = new RESTCatalog();
        catalog.initialize("ai-ready-catalog", properties);

        return catalog;
    }
}