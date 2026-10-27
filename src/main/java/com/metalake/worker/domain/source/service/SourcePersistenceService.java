package com.metalake.worker.domain.source.service;

import com.metalake.worker.domain.source.entity.AccessMode;
import com.metalake.worker.domain.source.entity.SourceResource;
import com.metalake.worker.domain.source.entity.SourceType;
import com.metalake.worker.domain.source.repository.SourceResourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class SourcePersistenceService {

    private final SourceResourceRepository sourceRepository;

    @Transactional
    public Long createInactive(String name) {

        SourceResource source = new SourceResource(
                name,
                SourceType.FILESET,
                AccessMode.DIRECT
        );

        source.deactivate();
        source.updateConfiguration(
                Map.of("storageProvider", "MINIO")
        );

        return sourceRepository.save(source).getId();
    }

    @Transactional
    public void activate(
            Long sourceId,
            String metalake,
            String catalog,
            String schema,
            String fileset
    ) {
        SourceResource source = sourceRepository.findById(sourceId)
                .orElseThrow();

        source.connectFileset(
                metalake, catalog, schema, fileset
        );

        source.activate();

        // 변경 감지로 UPDATE
    }
}