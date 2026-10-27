
package com.metalake.worker.processing.document;

import java.time.Instant;
import java.util.List;

public record CanonicalDocument(
        String documentId,
        Long sourceId,
        String sourcePath,
        String title,
        String mimeType,
        long sizeBytes,
        int pageCount,
        String extractionEngine,
        String extractionVersion,
        Instant extractedAt,
        List<CanonicalBlock> blocks
) {
    public record CanonicalBlock(
            String blockId,
            String sourceRef,
            int order,
            String type,
            Integer pageNo,
            String text,
            List<String> sectionPath,
            BoundingBox bbox,
            TableContent table
    ) {}

    public record BoundingBox(
            Double left,
            Double top,
            Double right,
            Double bottom
    ) {}

    public record TableContent(
            int rows,
            int columns,
            List<List<String>> cells
    ) {}
}
