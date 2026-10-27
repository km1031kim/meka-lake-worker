package com.metalake.worker.domain.job.entity;

public enum JobStatus {
    PENDING,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED
}