package com.metalake.worker.api.v1;

import com.metalake.worker.api.v1.persistence.ApiNormalizedOutputRepository;
import com.metalake.worker.domain.job.entity.ProcessingJob;
import com.metalake.worker.domain.job.repository.ProcessingJobRepository;
import com.metalake.worker.domain.output.entity.NormalizedOutput;
import com.metalake.worker.domain.source.entity.SourceResource;
import com.metalake.worker.domain.source.repository.SourceResourceRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;

import static com.metalake.worker.api.v1.ApiModels.*;

@RestController
@RequiredArgsConstructor
public class NormalizationController {
    private final NormalizationJobStateService states;
    private final NormalizationWorker worker;
    @Qualifier("normalizationExecutor")
    private final ThreadPoolTaskExecutor executor;
    private final ProcessingJobRepository jobs;
    private final SourceResourceRepository sources;
    private final ApiNormalizedOutputRepository outputs;

    @PostMapping("/api/sources/{sourceId}/normalizations")
    public ResponseEntity<JobAccepted> normalize(@PathVariable Long sourceId,
                @Valid @RequestBody(required = false) NormalizeRequest request) {
        List<String> paths = request == null || request.relativePaths() == null
                ? List.of() : List.copyOf(request.relativePaths());
        if (paths.size() != new LinkedHashSet<>(paths).size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Duplicate relativePaths");
        }
        for (String path : paths) {
            if (path == null || path.isBlank() || path.startsWith("/") || path.contains("\\")
                    || !path.toLowerCase(java.util.Locale.ROOT).endsWith(".pdf")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid PDF path");
            }
            for (String segment : path.split("/", -1)) {
                if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid PDF path");
                }
            }
        }
        boolean embed = request != null && request.embedding() != null
                && request.embedding().isEnabled();
        Long jobId = states.create(sourceId);
        try {
            executor.execute(() -> worker.run(jobId, paths, embed));
        } catch (RejectedExecutionException e) {
            states.reject(jobId);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Normalization queue is full");
        }
        String statusUrl = "/api/normalizations/" + jobId;
        return ResponseEntity.accepted().body(new JobAccepted(sourceId, jobId, "PENDING", statusUrl));
    }

    @GetMapping("/api/normalizations/{jobId}")
    @Transactional(readOnly = true)
    public JobResponse job(@PathVariable Long jobId) {
        ProcessingJob job = jobs.findById(jobId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found"));
        return new JobResponse(job.getSourceResource().getId(), jobId, job.getStatus().name(),
                job.getErrorMessage(), outputInfo(jobId));
    }

    @GetMapping("/api/sources/{sourceId}/normalized-results")
    @Transactional(readOnly = true)
    public PublishedResults published(@PathVariable Long sourceId) {
        SourceResource source = sources.findById(sourceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Source not found"));
        if (source.getSourceType() == com.metalake.worker.domain.source.entity.SourceType.DATABASE) {
            return new PublishedResults(sourceId, "NOT_APPLICABLE", null, List.of());
        }
        Long jobId = source.getCurrentPublishedJobId();
        return jobId == null
                ? new PublishedResults(sourceId, "NOT_READY", null, List.of())
                : new PublishedResults(sourceId, "AVAILABLE", jobId, outputInfo(jobId));
    }

    private List<OutputInfo> outputInfo(Long jobId) {
        return outputs.findByProcessingJob_Id(jobId).stream()
                .map(output -> new OutputInfo(output.getOutputType(), output.getStorageType().name(),
                        output.getCatalogName(), output.getNamespaceName(), output.getObjectName(),
                        jobId, output.getRowCount(), output.getProperties().get("snapshotId"),
                        (String) output.getProperties().get("datasetUri")))
                .toList();
    }
}
