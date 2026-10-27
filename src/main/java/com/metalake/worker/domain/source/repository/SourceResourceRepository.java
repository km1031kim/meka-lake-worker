package com.metalake.worker.domain.source.repository;

import com.metalake.worker.domain.source.entity.SourceResource;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SourceResourceRepository
        extends JpaRepository<SourceResource, Long> {
}