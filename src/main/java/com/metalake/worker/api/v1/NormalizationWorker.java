package com.metalake.worker.api.v1;

import com.metalake.worker.application.SourceFileReadService;
import com.metalake.worker.infrastructure.iceberg.IcebergDocumentTableService;
import com.metalake.worker.infrastructure.iceberg.IcebergDocumentWriter;
import com.metalake.worker.processing.document.CanonicalDocument;
import com.metalake.worker.processing.document.DoclingNormalizer;
import com.metalake.worker.processing.document.PdfExtractionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.iceberg.Table;
import org.apache.iceberg.catalog.Catalog;
import org.apache.iceberg.catalog.Namespace;
import org.apache.iceberg.catalog.TableIdentifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
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
    private final VectorBridgeClient vectors;

    public void run(Long jobId, List<String> requestedPaths) {
        run(jobId, requestedPaths, false);
    }

    public void run(Long jobId, List<String> requestedPaths, boolean embed) {
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
            List<CanonicalDocument> documentsToEmbed = new ArrayList<>();
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
                    if (embed) documentsToEmbed.add(document);
                    success++;
                } catch (Exception error) {
                    log.error("Document processing failed. jobId={}, path={}", jobId, path, error);
                    state.failFile(fileId, error);
                    failures.add(path);
                }
            }
            // Phase 1 policy: no partial publication even when some rows committed.
            if (!failures.isEmpty()) {
                throw new IllegalStateException("Failed files: " + failures.size());
            }
            // Only invoke BGE-M3 and Lance after the complete document batch succeeded.
            VectorBridgeClient.VectorManifest vectorManifest = embed
                    ? vectors.ingest(sourceId, jobId, documentsToEmbed) : null;
            Table table = icebergCatalog.loadTable(DOCUMENTS);
            table.refresh();
            // Publish DOCUMENTS and VECTORS in one PostgreSQL transaction.
            state.publish(jobId, success, lastSnapshotId, table.location(),
                    icebergCatalog.name(), "ai_ready_poc", "documents", vectorManifest);
        } catch (Exception fatal) {
            log.error("Normalization failed. jobId={}", jobId, fatal);
            state.fail(jobId, fatal);
        }
    }
}
