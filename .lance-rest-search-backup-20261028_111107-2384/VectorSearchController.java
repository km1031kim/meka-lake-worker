package com.metalake.worker.api.v1;

import com.metalake.worker.api.v1.persistence.ApiNormalizedOutputRepository;
import com.metalake.worker.domain.source.repository.SourceResourceRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** A stable public search API: callers never pass arbitrary S3/Lance URIs. */
@RestController
@RequiredArgsConstructor
public class VectorSearchController {
    private final SourceResourceRepository sources;
    private final ApiNormalizedOutputRepository outputs;
    private final VectorBridgeClient vectors;

    public record SearchRequest(@NotBlank String query,
                                @Min(1) @Max(30) Integer topK) {}

    @PostMapping("/api/sources/{sourceId}/vector-search")
    @Transactional(readOnly = true)
    public VectorBridgeClient.SearchResponse search(@PathVariable Long sourceId,
                                                    @Valid @RequestBody SearchRequest request) {
        var source = sources.findById(sourceId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Source not found"));
        Long jobId = source.getCurrentPublishedJobId();
        if (jobId == null || outputs.findByProcessingJob_Id(jobId).stream()
                .noneMatch(o -> "VECTORS".equals(o.getOutputType()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "VECTORS_NOT_READY");
        }
        // Access control by Source owner/tenant should be enforced here before production use.
        return vectors.search(sourceId, jobId, request.query(),
                request.topK() == null ? 5 : request.topK());
    }
}
