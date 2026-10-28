package com.metalake.worker.api.v1;

import com.metalake.worker.application.SourceFileReadService;
import com.metalake.worker.infrastructure.iceberg.IcebergDocumentTableService;
import com.metalake.worker.infrastructure.iceberg.IcebergDocumentWriter;
import com.metalake.worker.processing.document.CanonicalDocument;
import com.metalake.worker.processing.document.DoclingNormalizer;
import com.metalake.worker.processing.document.PdfExtractionService;
import lombok.RequiredArgsConstructor;
import org.apache.iceberg.Table;
import org.apache.iceberg.catalog.Catalog;
import org.apache.iceberg.catalog.Namespace;
import org.apache.iceberg.catalog.TableIdentifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class NormalizationWorker {
    private static final TableIdentifier DOCUMENTS =
            TableIdentifier.of(Namespace.of("ai_ready_poc"), "documents");

    private final NormalizationJobStateService state;
    private final SourceFileReadService fileReader;
    private final PdfExtractionService extractionService;
    private final DoclingNormalizer normalizer;
    private final IcebergDocumentWriter writer;
    private final IcebergDocumentTableService tableService;
    private final Catalog icebergCatalog;

    public void run(Long jobId, List<String> requestedPaths) {
        try {
            Long sourceId = state.start(jobId);
            // Ensures existing Iceberg tables are evolved before job-scoped writes.
            tableService.createTableIfAbsent();
            List<String> paths = requestedPaths == null || requestedPaths.isEmpty()
                    ? fileReader.listPdfFiles(sourceId)
                    : requestedPaths;
            if (paths.isEmpty()) {
                throw new IllegalStateException("No PDF files to process");
            }
            long success = 0;
            List<String> failures = new ArrayList<>();
            long lastSnapshotId = -1;
            for (String path : paths) {
                Long fileId = state.startFile(jobId, path);
                try {
                    CanonicalDocument document = normalizer.normalize(
                            extractionService.extract(sourceId, path));
                    IcebergDocumentWriter.WriteResult result = writer.append(document, jobId, fileId);
                    lastSnapshotId = result.snapshotId();
                    state.succeedFile(fileId);
                    success++;
                } catch (Exception error) {
                    state.failFile(fileId, error);
                    failures.add(path);
                }
            }
            // Phase 1 policy: no partial publication even when some rows committed.
            if (!failures.isEmpty()) {
                throw new IllegalStateException("Failed files: " + failures.size());
            }
            Table table = icebergCatalog.loadTable(DOCUMENTS);
            table.refresh();
            state.publish(jobId, success, lastSnapshotId, table.location(),
                    icebergCatalog.name(), "ai_ready_poc", "documents");
        } catch (Exception fatal) {
            state.fail(jobId, fatal);
        }
    }
}
