package com.metalake.worker.domain.job.repository;

import com.metalake.worker.domain.job.entity.ProcessingJob;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessingJobRepository
        extends JpaRepository<ProcessingJob, Long> {
}