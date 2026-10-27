package com.metalake.worker.domain.file.entity;

public enum FileProcessingStatus {
    PENDING,
    RUNNING,
    SUCCEEDED,
    FAILED,
    SKIPPED
}