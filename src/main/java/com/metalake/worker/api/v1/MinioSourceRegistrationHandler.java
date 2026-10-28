package com.metalake.worker.api.v1;

import com.metalake.worker.application.RegisterMinioSourceCommand;
import com.metalake.worker.application.RegisteredSource;
import com.metalake.worker.application.SourceRegistrationService;
import com.metalake.worker.domain.source.entity.SourceResource;
import com.metalake.worker.domain.source.repository.SourceResourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static com.metalake.worker.api.v1.ApiModels.*;

@Component
@RequiredArgsConstructor
public class MinioSourceRegistrationHandler implements SourceRegistrationHandler {
    private final SourceRegistrationService existingRegistration;
    private final SourceResourceRepository sourceRepository;

    @Override
    public boolean supports(String sourceType) {
        return "FILESET".equalsIgnoreCase(sourceType) || "S3".equalsIgnoreCase(sourceType);
    }

    @Override
    public SourceResponse register(RegisterSourceRequest request) {
        Connection c = request.connection();
        if (c.provider() != null && !c.provider().equalsIgnoreCase("MINIO")
                && !c.provider().equalsIgnoreCase("S3")) {
            throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED,
                    "Only MinIO/S3-compatible storage is implemented in phase 1");
        }
        RegisteredSource registered = existingRegistration.registerMinio(
                new RegisterMinioSourceCommand(request.name(), c.endpoint(), c.bucket(),
                        c.prefix(), c.accessKey(), c.secretKey()));
        SourceResource source = sourceRepository.findById(registered.sourceId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Registered source not found"));
        return SourceController.toResponse(source);
    }
}
