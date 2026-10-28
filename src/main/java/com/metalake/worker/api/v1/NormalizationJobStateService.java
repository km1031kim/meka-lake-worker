package com.metalake.worker.api.v1;

import com.metalake.worker.api.v1.persistence.ApiFileProcessingRepository;
import com.metalake.worker.api.v1.persistence.ApiNormalizedOutputRepository;
import com.metalake.worker.domain.file.entity.FileProcessing;
import com.metalake.worker.domain.job.entity.JobStatus;
import com.metalake.worker.domain.job.entity.ProcessingJob;
import com.metalake.worker.domain.job.repository.ProcessingJobRepository;
import com.metalake.worker.domain.output.entity.NormalizedOutput;
import com.metalake.worker.domain.output.entity.StorageType;
import com.metalake.worker.domain.source.entity.SourceResource;
import com.metalake.worker.domain.source.entity.SourceStatus;
import com.metalake.worker.domain.source.entity.SourceType;
import com.metalake.worker.domain.source.repository.SourceResourceRepository;
import jakarta.persistence.EntityManager;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.HashMap;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class NormalizationJobStateService {
    private final SourceResourceRepository sources;
    private final ProcessingJobRepository jobs;
    private final ApiFileProcessingRepository files;
    private final ApiNormalizedOutputRepository outputs;
    private final EntityManager entityManager;
    private final JdbcTemplate jdbc;

    @Transactional
    public Long create(Long sourceId) {
        SourceResource source = sources.findById(sourceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Source not found"));
        if (source.getStatus() != SourceStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Source is not active");
        }
        if (source.getSourceType() != SourceType.FILESET && source.getSourceType() != SourceType.GIT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "SOURCE_NOT_NORMALIZABLE");
        }
        return jobs.saveAndFlush(new ProcessingJob(source, source.getSourceType() == SourceType.GIT
                ? "INGEST_AND_NORMALIZE" : "NORMALIZE", null, null)).getId();
    }

    @Transactional
    public Long start(Long jobId) {
        ProcessingJob job = findJob(jobId);
        job.start();
        return job.getSourceResource().getId();
    }

    @Transactional
    public Long startFile(Long jobId, String path) {
        ProcessingJob job = findJob(jobId);
        FileProcessing file = new FileProcessing(job, path, null, null, "docling");
        file.start();
        return files.saveAndFlush(file).getId();
    }

    @Transactional
    public Long startGitFile(Long jobId, String path, String revision) {
        ProcessingJob job = findJob(jobId);
        FileProcessing file = new FileProcessing(job, path, revision, null, "git-tree-sitter");
        file.start();
        return files.saveAndFlush(file).getId();
    }

    /** All Iceberg code writes are committed before this publication transaction. */
    @Transactional
    public void publishGit(Long jobId, GitBridgeClient.GitJobResult result) {
        ProcessingJob job = findJob(jobId);
        if (job.getStatus() != JobStatus.RUNNING || result.outputs() == null ||
                result.outputs().size() != 3 || result.processedFiles() <= 0 ||
                result.sourceRevision() == null || !result.sourceRevision().matches("[a-f0-9]{40}")) {
            throw new IllegalStateException("Incomplete Git normalization output");
        }
        var kinds = result.outputs().stream().map(GitBridgeClient.GitOutput::kind).collect(java.util.stream.Collectors.toSet());
        if (!kinds.equals(java.util.Set.of("CODE_FILES", "CODE_SYMBOLS", "CODE_RELATIONS"))) {
            throw new IllegalStateException("Unexpected Git output kinds");
        }
        Long sourceId = job.getSourceResource().getId();
        SourceResource source = entityManager.find(SourceResource.class, sourceId, LockModeType.PESSIMISTIC_WRITE);
        if (source == null || source.getSourceType() != SourceType.GIT) {
            throw new IllegalStateException("Source not found or not Git");
        }
        for (var descriptor : result.outputs()) {
            NormalizedOutput output = new NormalizedOutput(job, descriptor.kind(), StorageType.ICEBERG, descriptor.tableLocation());
            output.assignCatalog(null, descriptor.catalog(), descriptor.namespace(), descriptor.table());
            var metadata = new HashMap<String, Object>();
            if (descriptor.snapshotId() != null) metadata.put("snapshotId", descriptor.snapshotId());
            metadata.put("sourceRevision", result.sourceRevision());
            metadata.put("mirrorUri", result.mirrorUri());
            if (result.fileset() != null) {
                metadata.put("mirrorFileset", result.fileset().catalog() + "." + result.fileset().schema() + "." + result.fileset().name());
            }
            output.recordResult(descriptor.recordCount(), metadata);
            outputs.save(output);
        }
        job.succeed();
        entityManager.flush();
        // Job source revision remains stable even when the configured branch later moves.
        jdbc.update("UPDATE public.processing_job SET source_revision = ? WHERE id = ?", result.sourceRevision(), jobId);
        Long prior = source.getCurrentPublishedJobId();
        if (prior == null || !jobs.findById(prior).orElseThrow().getCreatedAt().isAfter(job.getCreatedAt())) {
            source.publish(jobId);
            entityManager.flush();
            if (result.fileset() != null) {
                jdbc.update("""
                    UPDATE public.source_resource
                    SET gravitino_metalake=?, gravitino_catalog=?, gravitino_schema=?, gravitino_fileset=?
                    WHERE id=?
                    """, result.fileset().metalake(), result.fileset().catalog(),
                        result.fileset().schema(), result.fileset().name(), sourceId);
            }
        }
    }

    @Transactional
    public void succeedFile(Long fileId) {
        // Current pipeline does not create embeddings or language symbols.
        files.findById(fileId).orElseThrow().succeed(0, 0);
    }

    @Transactional
    public void failFile(Long fileId, Throwable error) {
        files.findById(fileId).orElseThrow().fail(safeError(error));
    }

    /** Iceberg commits happen BEFORE entering this database transaction. */
    @Transactional
    public void publish(Long jobId, long documentCount, long snapshotId, String tableLocation,
                        String catalogName, String namespace, String tableName) {
        publish(jobId, documentCount, snapshotId, tableLocation, catalogName, namespace,
                tableName, null);
    }

    /** DOCUMENTS and optional VECTORS are published atomically in PostgreSQL. */
    @Transactional
    public void publish(Long jobId, long documentCount, long snapshotId, String tableLocation,
                        String catalogName, String namespace, String tableName,
                        VectorBridgeClient.VectorManifest vectorManifest) {
        ProcessingJob job = findJob(jobId);
        if (job.getStatus() != JobStatus.RUNNING || documentCount == 0) {
            throw new IllegalStateException("Cannot publish an incomplete normalization job");
        }
        Long sourceId = job.getSourceResource().getId();
        SourceResource source = entityManager.find(SourceResource.class, sourceId,
                LockModeType.PESSIMISTIC_WRITE);
        if (source == null) throw new IllegalStateException("Source disappeared");

        NormalizedOutput output = new NormalizedOutput(job, "DOCUMENTS", StorageType.ICEBERG,
                tableLocation);
        output.assignCatalog(null, catalogName, namespace, tableName);
        output.recordResult(documentCount, Map.of("snapshotId", snapshotId));
        outputs.save(output);
        if (vectorManifest != null) {
            if (vectorManifest.recordCount() <= 0 || vectorManifest.dimension() != 1024) {
                throw new IllegalStateException("Invalid Lance vector output");
            }
            NormalizedOutput vectors = new NormalizedOutput(job, "VECTORS", StorageType.LANCE,
                    vectorManifest.datasetUri());
            if (vectorManifest.catalog() != null && vectorManifest.namespace() != null
                    && vectorManifest.table() != null) {
                vectors.assignCatalog(null, vectorManifest.catalog(),
                        vectorManifest.namespace(), vectorManifest.table());
            }
            vectors.recordResult(vectorManifest.recordCount(), Map.of(
                    "datasetUri", vectorManifest.datasetUri(),
                    "model", vectorManifest.model(),
                    "modelVersion", vectorManifest.modelVersion(),
                    "dimension", vectorManifest.dimension(),
                    "datasetVersion", vectorManifest.datasetVersion()));
            outputs.save(vectors);
        }
        job.succeed();

        // A slower, older job must not replace a newer published result.
        Long publishedJobId = source.getCurrentPublishedJobId();
        if (publishedJobId == null || !jobs.findById(publishedJobId).orElseThrow()
                .getCreatedAt().isAfter(job.getCreatedAt())) {
            source.publish(jobId);
        }
    }

    @Transactional
    public void reject(Long jobId) {
        ProcessingJob job = findJob(jobId);
        if (job.getStatus() == JobStatus.PENDING) {
            job.start();
            job.fail("NormalizationQueueFull");
        }
    }

    @Transactional
    public void fail(Long jobId, Throwable error) {
        ProcessingJob job = findJob(jobId);
        if (job.getStatus() == JobStatus.RUNNING) {
            job.fail(safeError(error));
        }
    }

    private ProcessingJob findJob(Long jobId) {
        return jobs.findById(jobId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found"));
    }

    static String safeError(Throwable error) {
        String type = error.getClass().getSimpleName();
        // Do not copy third-party exception messages: they may contain credentials/URLs.
        return (type == null || type.isBlank()) ? "NormalizationFailed" : type;
    }
}
