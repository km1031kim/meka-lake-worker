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
        if (jobId == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "VECTORS_NOT_READY");
        }
        var vectorOutput = outputs.findByProcessingJob_Id(jobId).stream()
                .filter(o -> "VECTORS".equals(o.getOutputType()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT, "VECTORS_NOT_READY"));
        if (vectorOutput.getCatalogName() == null || vectorOutput.getNamespaceName() == null
                || vectorOutput.getObjectName() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "LANCE_TABLE_NOT_REGISTERED");
        }
        // TODO: enforce ownership/tenant ACL before allowing source access in production.
        return vectors.search(sourceId, jobId,
                vectorOutput.getCatalogName(), vectorOutput.getNamespaceName(),
                vectorOutput.getObjectName(), request.query(),
                request.topK() == null ? 5 : request.topK());
    }
}
