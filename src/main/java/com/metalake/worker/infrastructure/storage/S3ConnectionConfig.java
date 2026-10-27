package com.metalake.worker.infrastructure.storage;

import java.net.URI;

public record S3ConnectionConfig(
        URI endpoint,
        String region,
        boolean pathStyleAccess
) {}