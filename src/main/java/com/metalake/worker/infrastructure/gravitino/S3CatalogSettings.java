package com.metalake.worker.infrastructure.gravitino;

public record S3CatalogSettings(
        String endpoint,
        String region
) {}