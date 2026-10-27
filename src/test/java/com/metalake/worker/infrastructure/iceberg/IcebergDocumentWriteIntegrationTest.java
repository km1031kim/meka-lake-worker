
package com.metalake.worker.infrastructure.iceberg;

import com.metalake.worker.processing.document.CanonicalDocument;
import com.metalake.worker.processing.document.DoclingNormalizer;
import com.metalake.worker.processing.document.PdfExtractionService;
import org.apache.iceberg.Table;
import org.apache.iceberg.catalog.Catalog;
import org.apache.iceberg.catalog.Namespace;
import org.apache.iceberg.catalog.TableIdentifier;
import org.apache.iceberg.data.IcebergGenerics;
import org.apache.iceberg.data.Record;
import org.apache.iceberg.expressions.Expressions;
import org.apache.iceberg.io.CloseableIterable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class IcebergDocumentWriteIntegrationTest {

    @Autowired
    private PdfExtractionService extractionService;

    @Autowired
    private DoclingNormalizer normalizer;

    @Autowired
    private IcebergDocumentWriter documentWriter;

    @Autowired
    private Catalog icebergCatalog;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void writeAndReadCanonicalDocument() throws Exception {

        // 1. 원본 PDF -> Docling
        var extracted = extractionService.extract(
                8L,
                "TeraStream_형상관리가이드.pdf"
        );

        // 2. Docling -> CanonicalDocument
        CanonicalDocument document =
                normalizer.normalize(extracted);

        assertFalse(document.blocks().isEmpty());

        // 저장 데이터와 비교할 JSON
        String expectedJson =
                objectMapper.writeValueAsString(document);

        // 3. Iceberg에 INSERT
        var result = documentWriter.append(document);

        System.out.println(
                "Document ID: " + result.documentId()
        );
        System.out.println(
                "Data File: " + result.dataFileLocation()
        );
        System.out.println(
                "Snapshot ID: " + result.snapshotId()
        );

        // 4. Iceberg 테이블 로드
        TableIdentifier identifier = TableIdentifier.of(
                Namespace.of("ai_ready_poc"),
                "documents"
        );

        Table table = icebergCatalog.loadTable(identifier);
        table.refresh();

        // 5. INSERT한 문서 조회
        boolean found = false;
        int matchedRows = 0;

        try (CloseableIterable<Record> rows =
                     IcebergGenerics.read(table)
                             .where(Expressions.equal(
                                     "document_id",
                                     document.documentId()
                             ))
                             .build()) {

            for (Record row : rows) {
                matchedRows++;

                String actualJson =
                        (String) row.getField("content_json");

                if (expectedJson.equals(actualJson)) {
                    found = true;
                }
            }
        }

        System.out.println("Matched rows: " + matchedRows);

        // 6. 검증
        assertTrue(
                found,
                "Newly written CanonicalDocument was not found"
        );

        assertNotNull(result.snapshotId());
    }
}
