package com.metalake.worker.api.v1;

import com.metalake.worker.domain.source.entity.SourceResource;
import com.metalake.worker.domain.source.entity.SourceStatus;
import com.metalake.worker.domain.source.entity.SourceType;
import com.metalake.worker.domain.source.repository.SourceResourceRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static com.metalake.worker.api.v1.ApiModels.*;

@RestController
@RequestMapping("/api/sources")
public class SourceController {
    private final List<SourceRegistrationHandler> handlers;
    private final SourceResourceRepository sourceRepository;

    public SourceController(List<SourceRegistrationHandler> handlers,
                            SourceResourceRepository sourceRepository) {
        this.handlers = List.copyOf(handlers);
        this.sourceRepository = sourceRepository;
    }

    @PostMapping
    public ResponseEntity<SourceResponse> register(@Valid @RequestBody RegisterSourceRequest request) {
        SourceRegistrationHandler handler = handlers.stream()
                .filter(candidate -> candidate.supports(request.sourceType()))
                .findFirst().orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED,
                                "Source registration is not available for this type"));
        return ResponseEntity.status(HttpStatus.CREATED).body(handler.register(request));
    }

    @GetMapping("/{sourceId}")
    public SourceResponse get(@PathVariable Long sourceId) {
        SourceResource source = sourceRepository.findById(sourceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Source not found"));
        return toResponse(source);
    }

    static SourceResponse toResponse(SourceResource source) {
        boolean isFileset = source.getSourceType() == SourceType.FILESET;
        boolean isGit = source.getSourceType() == SourceType.GIT;
        boolean active = source.getStatus() == SourceStatus.ACTIVE;
        FilesetRef fileset = source.getGravitinoFileset() == null ? null
                : new FilesetRef(source.getGravitinoMetalake(), source.getGravitinoCatalog(),
                        source.getGravitinoSchema(), source.getGravitinoFileset());
        return new SourceResponse(source.getId(), source.getName(), source.getSourceType().name(),
                source.getAccessMode().name(), source.getStatus().name(),
                new Capabilities((isFileset || isGit) && active, isFileset && active && fileset != null,
                        source.getSourceType() == SourceType.DATABASE && active), fileset);
    }
}
