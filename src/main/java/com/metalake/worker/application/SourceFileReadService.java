
package com.metalake.worker.application;

import com.metalake.worker.domain.source.entity.SourceResource;
import com.metalake.worker.domain.source.entity.SourceStatus;
import com.metalake.worker.domain.source.entity.SourceType;
import com.metalake.worker.domain.source.repository.SourceResourceRepository;
import com.metalake.worker.infrastructure.gravitino.*;
import com.metalake.worker.infrastructure.storage.OpenDalS3Factory;
import lombok.RequiredArgsConstructor;
import org.apache.opendal.Operator;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class SourceFileReadService {

    private final SourceResourceRepository sourceRepository;
    private final GravitinoClient gravitinoClient;
    private final OpenDalS3Factory openDalS3Factory;

    public List<String> listPdfFiles(Long sourceId) {

        try (Operator operator = createOperator(sourceId)) {

            return operator.list("/").stream()
                    .filter(entry -> entry.metadata.isFile())
                    .map(entry -> entry.path)
                    .filter(path -> path.toLowerCase(Locale.ROOT)
                            .endsWith(".pdf"))
                    .toList();
        }
    }

    public long copyPdf(
            Long sourceId,
            String relativePath,
            OutputStream destination
    ) throws IOException {

        validatePdfPath(relativePath);

        try (Operator operator = createOperator(sourceId);
             InputStream input = operator.createInputStream(relativePath)) {

            return input.transferTo(destination);
        }
    }

    private Operator createOperator(Long sourceId) {

        SourceResource source = sourceRepository.findById(sourceId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Source not found: " + sourceId
                        )
                );

        if (source.getStatus() != SourceStatus.ACTIVE
                || source.getSourceType() != SourceType.FILESET) {
            throw new IllegalStateException(
                    "Source is not an active Fileset"
            );
        }

        if (!"ax".equals(source.getGravitinoMetalake())) {
            throw new IllegalStateException(
                    "Unexpected metalake"
            );
        }

        String catalog = source.getGravitinoCatalog();
        String schema = source.getGravitinoSchema();
        String fileset = source.getGravitinoFileset();

        // Catalog: Endpoint, Region
        S3CatalogSettings settings =
                gravitinoClient.loadS3CatalogSettings(catalog);

        // Fileset: Bucket, Prefix
        GravitinoFileset filesetMetadata =
                gravitinoClient.loadFileset(
                        catalog, schema, fileset
                );

        // Credential Vending
        GravitinoS3Credentials credentials =
                gravitinoClient.loadS3Credentials(
                        catalog, schema, fileset
                );

        return openDalS3Factory.create(
                filesetMetadata.resolvedStorageLocation(),
                settings,
                credentials
        );
    }

    private void validatePdfPath(String path) {
        if (path == null || path.isBlank()
                || path.startsWith("/")
                || path.contains("\\")) {
            throw new IllegalArgumentException("Invalid PDF path");
        }

        for (String segment : path.split("/", -1)) {
            if (segment.isEmpty()
                    || segment.equals(".")
                    || segment.equals("..")) {
                throw new IllegalArgumentException("Invalid PDF path");
            }
        }

        if (!path.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            throw new IllegalArgumentException("Not a PDF path");
        }
    }
}
