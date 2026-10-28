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

    @Transactional
    public Long create(Long sourceId) {
        SourceResource source = sources.findById(sourceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Source not found"));
        if (source.getStatus() != SourceStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Source is not active");
        }
        if (source.getSourceType() != SourceType.FILESET) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "SOURCE_NOT_NORMALIZABLE");
        }
        return jobs.saveAndFlush(new ProcessingJob(source, "NORMALIZE", null, null)).getId();
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
