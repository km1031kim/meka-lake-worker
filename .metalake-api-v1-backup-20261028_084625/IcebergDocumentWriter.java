package com.metalake.worker.infrastructure.iceberg;

import com.metalake.worker.processing.document.CanonicalDocument;
import lombok.RequiredArgsConstructor;
import org.apache.iceberg.DataFile;
import org.apache.iceberg.Table;
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
    private final IcebergDocumentTableService tables;
    private final ObjectMapper objectMapper;

    /** Preserves the old POC call path for integration tests or local experiments. */
    public WriteResult append(CanonicalDocument document) throws IOException {
        return append(document, null, null);
    }

    /** Writes result scope into each Iceberg row; both IDs are required for API jobs. */
    public WriteResult append(CanonicalDocument document, Long jobId, Long fileId) throws IOException {
        if ((jobId == null) != (fileId == null)) {
            throw new IllegalArgumentException("jobId and fileId must be supplied together");
        }
        Table table = tables.createTableIfAbsent();
        String canonicalJson = objectMapper.writeValueAsString(document);
        Record record = GenericRecord.create(table.schema());
        record.setField("document_id", document.documentId());
        record.setField("source_id", document.sourceId());
        record.setField("source_path", document.sourcePath());
        record.setField("title", document.title());
        record.setField("mime_type", document.mimeType());
        record.setField("page_count", document.pageCount());
        record.setField("block_count", document.blocks().size());
        record.setField("extraction_engine", document.extractionEngine());
        record.setField("content_json", canonicalJson);
        record.setField("extracted_at", document.extractedAt().atOffset(ZoneOffset.UTC));
        record.setField("processing_job_id", jobId);
        record.setField("file_processing_id", fileId);

        String location = table.locationProvider().newDataLocation(UUID.randomUUID() + ".parquet");
        OutputFile outputFile = table.io().newOutputFile(location);
        DataWriter<Record> writer = Parquet.writeData(outputFile)
                .forTable(table)
                .createWriterFunc(GenericParquetWriter::create)
                .build();
        try (writer) {
            writer.write(record);
        }
        DataFile dataFile = writer.toDataFile();
        table.newAppend().appendFile(dataFile).commit();
        table.refresh();
        return new WriteResult(document.documentId(), dataFile.location().toString(),
                table.currentSnapshot().snapshotId());
    }

    public record WriteResult(String documentId, String dataFileLocation, long snapshotId) {}
}
