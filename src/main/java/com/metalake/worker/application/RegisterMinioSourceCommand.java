package com.metalake.worker.application;

public record RegisterMinioSourceCommand(
        String name,
        String endpoint,
        String bucket,
        String path,
        String accessKey,
        String secretKey
) {
    @Override
    public String toString() {
        return "RegisterMinioSourceCommand[credentials=REDACTED]";
    }
}