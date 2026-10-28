package com.metalake.worker.infrastructure.iceberg;

import lombok.RequiredArgsConstructor;
import org.apache.iceberg.Schema;
import org.apache.iceberg.Table;
import org.apache.iceberg.catalog.Catalog;
import org.apache.iceberg.catalog.Namespace;
import org.apache.iceberg.catalog.TableIdentifier;
import org.apache.iceberg.types.Types;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class IcebergDocumentTableService {
    private final Catalog icebergCatalog;
    private static final Namespace NAMESPACE = Namespace.of("ai_ready_poc");
    private static final TableIdentifier DOCUMENTS = TableIdentifier.of(NAMESPACE, "documents");

    /** Can evolve the existing 10-column POC table without rebuilding historical data. */
    public synchronized Table createTableIfAbsent() {
        if (icebergCatalog.tableExists(DOCUMENTS)) {
            Table table = icebergCatalog.loadTable(DOCUMENTS);
            boolean needsJob = table.schema().findField("processing_job_id") == null;
            boolean needsFile = table.schema().findField("file_processing_id") == null;
            if (needsJob || needsFile) {
                var change = table.updateSchema();
                if (needsJob) change.addColumn("processing_job_id", Types.LongType.get());
                if (needsFile) change.addColumn("file_processing_id", Types.LongType.get());
                change.commit();
                table.refresh();
            }
            return table;
        }
        Schema schema = new Schema(
                Types.NestedField.required(1, "document_id", Types.StringType.get()),
                Types.NestedField.required(2, "source_id", Types.LongType.get()),
                Types.NestedField.required(3, "source_path", Types.StringType.get()),
                Types.NestedField.optional(4, "title", Types.StringType.get()),
                Types.NestedField.optional(5, "mime_type", Types.StringType.get()),
                Types.NestedField.required(6, "page_count", Types.IntegerType.get()),
                Types.NestedField.required(7, "block_count", Types.IntegerType.get()),
                Types.NestedField.optional(8, "extraction_engine", Types.StringType.get()),
                Types.NestedField.required(9, "content_json", Types.StringType.get()),
                Types.NestedField.required(10, "extracted_at", Types.TimestampType.withZone()),
                Types.NestedField.optional(11, "processing_job_id", Types.LongType.get()),
                Types.NestedField.optional(12, "file_processing_id", Types.LongType.get())
        );
        return icebergCatalog.createTable(DOCUMENTS, schema);
    }
}
