
package com.metalake.worker.infrastructure.iceberg;

import com.metalake.worker.processing.document.CanonicalDocument;
import lombok.RequiredArgsConstructor;
import org.apache.iceberg.DataFile;
import org.apache.iceberg.Table;
import org.apache.iceberg.catalog.Catalog;
import org.apache.iceberg.catalog.Namespace;
import org.apache.iceberg.catalog.TableIdentifier;
import org.apache.iceberg.data.GenericRecord;
import org.apache.iceberg.data.Record;
import org.apache.iceberg.data.parquet.GenericParquetWriter;
import org.apache.iceberg.io.DataWriter;
import org.apache.iceberg.io.OutputFile;
import org.apache.iceberg.parquet.Parquet;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IcebergDocumentWriter {

    private static final TableIdentifier DOCUMENTS =
            TableIdentifier.of(
                    Namespace.of("ai_ready_poc"),
                    "documents"
            );

    private final Catalog icebergCatalog;
    private final ObjectMapper objectMapper;

    public WriteResult append(
            CanonicalDocument document
    ) throws IOException {

        Table table = icebergCatalog.loadTable(DOCUMENTS);

        // 1. CanonicalDocument -> JSON
        String canonicalJson =
                objectMapper.writeValueAsString(document);

        // 2. Iceberg GenericRecord 생성
        Record record = GenericRecord.create(table.schema());

        record.setField("document_id", document.documentId());
        record.setField("source_id", document.sourceId());
        record.setField("source_path", document.sourcePath());
        record.setField("title", document.title());
        record.setField("mime_type", document.mimeType());
        record.setField("page_count", document.pageCount());
        record.setField("block_count", document.blocks().size());
        record.setField(
                "extraction_engine",
                document.extractionEngine()
        );
        record.setField("content_json", canonicalJson);

        // Iceberg timestamptz
        record.setField(
                "extracted_at",
                document.extractedAt().atOffset(ZoneOffset.UTC)
        );

        // 3. Parquet 저장 위치
        String location = table.locationProvider()
                .newDataLocation(
                        UUID.randomUUID() + ".parquet"
                );

        OutputFile outputFile =
                table.io().newOutputFile(location);

        // 4. 실제 Parquet 파일 기록
        DataWriter<Record> writer = Parquet.writeData(outputFile)
                .forTable(table)
                .createWriterFunc(GenericParquetWriter::create)
                .build();

        try (writer) {
            writer.write(record);
        }

        DataFile dataFile = writer.toDataFile();

        // 5. Iceberg Snapshot Commit
        table.newAppend()
                .appendFile(dataFile)
                .commit();

        table.refresh();

        return new WriteResult(
                document.documentId(),
                dataFile.location().toString(),
                table.currentSnapshot().snapshotId()
        );
    }

    public record WriteResult(
            String documentId,
            String dataFileLocation,
            long snapshotId
    ) {}
}
