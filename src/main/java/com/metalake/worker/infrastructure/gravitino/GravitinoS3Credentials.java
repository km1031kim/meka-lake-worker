package com.metalake.worker.infrastructure.gravitino;

public record GravitinoS3Credentials(
        String accessKeyId,
        String secretAccessKey
) {
    @Override
    public String toString() {
        return "GravitinoS3Credentials[REDACTED]";
    }
}