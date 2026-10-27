
package com.metalake.worker.infrastructure.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;

@Configuration
public class S3ClientConfig {

    @Bean(name = "platformS3Client", destroyMethod = "close")
    public S3Client s3Client(
            @Value("${metalake.storage.s3.endpoint}") String endpoint,
            @Value("${metalake.storage.s3.region:us-east-1}") String region
    ) {
        return S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(region))
                .forcePathStyle(true)
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }
}