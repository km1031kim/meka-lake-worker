
package com.metalake.worker.processing.document;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class PdfExtractionServiceIntegrationTest {

    @Autowired
    private PdfExtractionService extractionService;

    @Autowired
    private DoclingNormalizer normalizer;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void extractPdf() throws Exception {

        var result = extractionService.extract(
                8L,
                "TeraStream_형상관리가이드.pdf"
        );

        assertEquals("application/pdf", result.mimeType());
        assertTrue(result.sizeBytes() > 0);
        assertNotNull(result.doclingJson());

        System.out.println("Source ID: " + result.sourceId());
        System.out.println("PDF: " + result.sourcePath());
        System.out.println("MIME: " + result.mimeType());
        System.out.println("Bytes: " + result.sizeBytes());
        System.out.println(
                "Docling JSON type: "
                        + result.doclingJson().getClass().getSimpleName()
        );
    }


    @Test
    void normalizePdf() throws Exception {

        // 1. OpenDAL → Tika → Docling
        var extracted = extractionService.extract(
                8L,
                "TeraStream_형상관리가이드.pdf"
        );

        CanonicalDocument document =
                normalizer.normalize(extracted);

        assertFalse(document.blocks().isEmpty());
        assertTrue(document.pageCount() > 0);

        long headingCount = document.blocks().stream()
                .filter(b -> "section_header".equals(b.type()))
                .count();

        long tableCount = document.blocks().stream()
                .filter(b -> "table".equals(b.type()))
                .count();

        long pictureCount = document.blocks().stream()
                .filter(b -> "picture".equals(b.type()))
                .count();

        System.out.println("Document ID: " + document.documentId());
        System.out.println("Title: " + document.title());
        System.out.println("Pages: " + document.pageCount());
        System.out.println("Total blocks: " + document.blocks().size());
        System.out.println("Headings: " + headingCount);
        System.out.println("Tables: " + tableCount);
        System.out.println("Pictures: " + pictureCount);

        // 섹션 경로 검증
        document.blocks().stream()
                .filter(b -> "text".equals(b.type()))
                .limit(5)
                .forEach(block -> {
                    System.out.println("Section: " + block.sectionPath());
                    System.out.println("Page: " + block.pageNo());
                    System.out.println("Text: " + block.text());
                });

        // JSON 직렬화 검증
        String json = objectMapper.writeValueAsString(document);
        assertFalse(json.isBlank());
    }
}
