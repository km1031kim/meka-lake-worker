
package com.metalake.worker.application;


import com.metalake.worker.domain.source.service.SourcePersistenceService;
import com.metalake.worker.infrastructure.gravitino.GravitinoClient;
import com.metalake.worker.infrastructure.gravitino.GravitinoFileset;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SourceRegistrationService {

    private static final String METALAKE = "ax";
    private static final String SCHEMA = "object-storage";

    private final SourcePersistenceService persistenceService;
    private final GravitinoClient gravitinoClient;

    public RegisteredSource registerMinio(
            RegisterMinioSourceCommand command
    ) {
        // 기존 입력값 검증 메서드 재사용
        validate(command);

        String storageLocation = buildStorageLocation(
                command.bucket(),
                command.path()
        );

        Long sourceId = persistenceService.createInactive(
                command.name()
        );

        String catalogName = "source_" + sourceId;
        String filesetName = "fileset_" + sourceId;

        try {
            // 2. Catalog 생성 (인증 정보 관리)
            gravitinoClient.createFilesetCatalog(
                    catalogName,
                    buildCatalogProperties(command)
            );

            // 3. Schema 생성
            gravitinoClient.createSchema(
                    catalogName,
                    SCHEMA
            );

            // 4. EXTERNAL Fileset 생성 (원본 경로만 등록)
            gravitinoClient.createExternalFileset(
                    catalogName,
                    SCHEMA,
                    filesetName,
                    storageLocation,
                    Map.of(
                            "default-location-name",
                            filesetName
                    )
            );

            // 5. 등록된 Fileset 확인
            GravitinoFileset fileset =
                    gravitinoClient.loadFileset(
                            catalogName,
                            SCHEMA,
                            filesetName
                    );

            if (!filesetName.equals(fileset.name())
                    || !storageLocation.equals(
                    fileset.resolvedStorageLocation())) {
                throw new IllegalStateException(
                        "Created Fileset metadata mismatch"
                );
            }

            // 6. JPA ACTIVE 전환
            persistenceService.activate(
                    sourceId,
                    METALAKE,
                    catalogName,
                    SCHEMA,
                    filesetName
            );

            return new RegisteredSource(
                    sourceId,
                    METALAKE,
                    catalogName,
                    SCHEMA,
                    filesetName
            );

        } catch (RuntimeException e) {
            // 별도 트랜잭션으로 생성된 INACTIVE 행은 유지
            throw new IllegalStateException(
                    "Source registration failed. sourceId="
                            + sourceId
                            + ", catalog=" + catalogName
                            + ". Reconciliation may be required.",
                    e
            );
        }
    }

    private Map<String, String> buildCatalogProperties(
            RegisterMinioSourceCommand command
    ) {
        return Map.of(
                "s3-endpoint", command.endpoint(),
                "s3-access-key-id", command.accessKey(),
                "s3-secret-access-key", command.secretKey(),
                "s3-region", "us-east-1",
                "s3-path-style-access", "true",
                "credential-providers", "s3-secret-key"
        );
    }


    private void validate(RegisterMinioSourceCommand command) {
        if (command == null
                || isBlank(command.name())
                || isBlank(command.endpoint())
                || isBlank(command.bucket())
                || isBlank(command.accessKey())
                || isBlank(command.secretKey())) {
            throw new IllegalArgumentException(
                    "Required MinIO source information is missing"
            );
        }

        URI endpoint = URI.create(command.endpoint());

        if ((!endpoint.getScheme().equalsIgnoreCase("http")
                && !endpoint.getScheme().equalsIgnoreCase("https"))
                || endpoint.getHost() == null
                || endpoint.getRawUserInfo() != null
                || endpoint.getRawQuery() != null
                || endpoint.getRawFragment() != null) {
            throw new IllegalArgumentException("Invalid MinIO endpoint");
        }

        if (!command.bucket().matches("[a-z0-9][a-z0-9.-]*")) {
            throw new IllegalArgumentException("Invalid bucket name");
        }
    }

    private String buildStorageLocation(String bucket, String path) {
        String prefix = path == null
                ? ""
                : path.replaceAll("^/+|/+$", "");

        if (!prefix.isEmpty()) {
            for (String segment : prefix.split("/", -1)) {
                if (segment.isBlank()
                        || segment.equals(".")
                        || segment.equals("..")
                        || segment.contains("\\")) {
                    throw new IllegalArgumentException("Invalid source path");
                }
            }
        }

        return "s3a://" + bucket
                + (prefix.isEmpty() ? "" : "/" + prefix);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
