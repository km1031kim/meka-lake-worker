package com.metalake.worker.api.v1.persistence;

import com.metalake.worker.domain.file.entity.FileProcessing;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApiFileProcessingRepository extends JpaRepository<FileProcessing, Long> {}
