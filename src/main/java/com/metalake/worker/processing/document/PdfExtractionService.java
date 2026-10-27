package com.metalake.worker.processing.document;


import com.metalake.worker.application.SourceFileReadService;
import com.metalake.worker.infrastructure.docling.DoclingRestClient;
import lombok.RequiredArgsConstructor;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
@RequiredArgsConstructor
public class PdfExtractionService {

    private final SourceFileReadService sourceFileReadService;
    private final DoclingRestClient doclingRestClient;

    public PdfExtractionResult extract(
            Long sourceId,
            String relativePath
    ) throws Exception {

        Path tempFile = Files.createTempFile(
                "metalake-" + sourceId + "-", ".pdf"
        );

        try {
            // 1. OpenDAL에서 PDF 다운로드
            try (OutputStream output = Files.newOutputStream(tempFile)) {
                sourceFileReadService.copyPdf(
                        sourceId,
                        relativePath,
                        output
                );
            }

            // 2. Tika MIME 타입 감지
            String mimeType = new Tika().detect(tempFile);

            if (!"application/pdf".equals(mimeType)) {
                throw new IllegalArgumentException(
                        "Unsupported MIME type: " + mimeType
                );
            }

            // 3. Docling PDF → JSON
            String originalName = Path.of(relativePath)
                    .getFileName()
                    .toString();

            Object doclingJson = doclingRestClient.convertPdf(
                    tempFile,
                    originalName
            );

            return new PdfExtractionResult(
                    sourceId,
                    relativePath,
                    Files.size(tempFile),
                    mimeType,
                    doclingJson
            );

        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    public record PdfExtractionResult(
            Long sourceId,
            String sourcePath,
            long sizeBytes,
            String mimeType,
            Object doclingJson
    ) {}
}
