package com.metalake.worker.application;

import static org.junit.jupiter.api.Assertions.*;


import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;


@SpringBootTest
class SourceFileReadServiceIntegrationTest {

    @Autowired
    private SourceFileReadService sourceFileReadService;

    @TempDir
    Path tempDir;

    @Test
    void readPdfFromRegisteredSource() throws Exception {

        Long sourceId = 8L;

        // 1. PDF 목록 조회
        List<String> files =
                sourceFileReadService.listPdfFiles(sourceId);

        assertFalse(files.isEmpty(), "No PDF found");

        String pdfPath = files.get(0);

        System.out.println("PDF: " + pdfPath);

        // 2. PDF 전체 다운로드
        Path localPdf = tempDir.resolve("source.pdf");

        long copiedBytes;

        try (OutputStream output = Files.newOutputStream(localPdf)) {
            copiedBytes = sourceFileReadService.copyPdf(
                    sourceId,
                    pdfPath,
                    output
            );
        }

        // 3. 파일 크기 확인
        assertTrue(copiedBytes > 0);
        assertEquals(copiedBytes, Files.size(localPdf));

        // 4. PDF 헤더 확인
        try (InputStream input = Files.newInputStream(localPdf)) {
            byte[] header = input.readNBytes(5);

            assertArrayEquals(
                    "%PDF-".getBytes(StandardCharsets.US_ASCII),
                    header
            );
        }

        System.out.println("Source ID: " + sourceId);
        System.out.println("Copied bytes: " + copiedBytes);
        System.out.println("PDF read success");
    }
}
