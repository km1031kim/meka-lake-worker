
package com.metalake.worker.application;

import com.metalake.worker.domain.source.service.SourcePersistenceService;
import com.metalake.worker.infrastructure.gravitino.GravitinoClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class SourceRegistrationService {

    private static final String METALAKE = "ax";
    private static final String CATALOG = "source";
    private static final String SCHEMA = "object-storage";

    private final SourcePersistenceService persistenceService;
    private final GravitinoClient gravitinoClient;

    public RegisteredSource registerMinio(
            RegisterMinioSourceCommand command
    ) {
        validate(command);

        String storageLocation = buildStorageLocation(
                command.bucket(),
                command.path()
        );

        // 1. PostgreSQL에 소스 등록 (INACTIVE)
        Long sourceId = persistenceService.createInactive(
                command.name()
        );

        // 등록 건마다 고유한 Fileset 생성
        String filesetName = "source_" + sourceId;

        // 2. Gravitino Fileset 설정
        Map<String, String> properties =
                buildProperties(command, filesetName);

        // 3. Gravitino Fileset 생성
        try {
            gravitinoClient.createExternalFileset(
                    CATALOG,
                    SCHEMA,
                    filesetName,
                    storageLocation,
                    properties
            );
        } catch (RuntimeException e) {
            throw new IllegalStateException(
                    "Fileset creation failed. sourceId=" + sourceId,
                    e
            );
        }

        // 4. PostgreSQL 소스 활성화
        persistenceService.activate(
                sourceId,
                METALAKE,
                CATALOG,
                SCHEMA,
                filesetName
        );

        return new RegisteredSource(
                sourceId,
                METALAKE,
                CATALOG,
                SCHEMA,
                filesetName
        );
    }

    private Map<String, String> buildProperties(
            RegisterMinioSourceCommand command,
            String filesetName
    ) {
        Map<String, String> properties = new HashMap<>();

        properties.put("default-location-name", filesetName);
        properties.put("credential-providers", "s3-secret-key");
        properties.put("s3-endpoint", command.endpoint());
        properties.put("s3-access-key-id", command.accessKey());
        properties.put("s3-secret-access-key", command.secretKey());
        properties.put("s3-region", "us-east-1");
        properties.put("s3-path-style-access", "true");

        return properties;
    }

    private String buildStorageLocation(
            String bucket,
            String path
    ) {
        String normalizedPath = path == null
                ? ""
                : path.replaceAll("^/+|/+$", "");

        if (!normalizedPath.isEmpty()) {
            for (String segment : normalizedPath.split("/", -1)) {
                if (segment.isEmpty()
                        || segment.equals(".")
                        || segment.equals("..")) {
                    throw new IllegalArgumentException(
                            "Invalid object storage path"
                    );
                }
            }
        }

        return "s3a://" + bucket
                + (normalizedPath.isEmpty()
                ? "/"
                : "/" + normalizedPath);
    }

    private void validate(RegisterMinioSourceCommand command) {
        Objects.requireNonNull(command, "command");

        requireText(command.name(), "name");
        requireText(command.endpoint(), "endpoint");
        requireText(command.bucket(), "bucket");
        requireText(command.accessKey(), "accessKey");
        requireText(command.secretKey(), "secretKey");

        URI uri = URI.create(command.endpoint());

        if (!("http".equalsIgnoreCase(uri.getScheme())
                || "https".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null
                || uri.getRawUserInfo() != null
                || uri.getRawQuery() != null
                || uri.getRawFragment() != null
                || (uri.getPath() != null
                && !uri.getPath().isEmpty()
                && !"/".equals(uri.getPath()))) {
            throw new IllegalArgumentException(
                    "Invalid MinIO endpoint"
            );
        }

        if (!command.bucket().matches(
                "[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]"
        )) {
            throw new IllegalArgumentException(
                    "Invalid S3 bucket name"
            );
        }
    }

    private void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    field + " must not be blank"
            );
        }
    }
}
