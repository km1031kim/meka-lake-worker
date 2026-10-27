package com.metalake.worker.infrastructure.storage;

import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Component
public class S3ClientFactory {

    public S3Client create(
            S3ConnectionConfig config,
            AwsCredentialsProvider credentialsProvider
    ) {
        var builder = S3Client.builder()
                .region(Region.of(config.region()))
                .credentialsProvider(credentialsProvider)
                .forcePathStyle(config.pathStyleAccess());

        if (config.endpoint() != null) {
            builder.endpointOverride(config.endpoint());
        }

        return builder.build();
    }
}