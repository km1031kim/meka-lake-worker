package com.metalake.worker.api.v1;

import com.metalake.worker.api.v1.persistence.ApiNormalizedOutputRepository;
import com.metalake.worker.domain.output.entity.NormalizedOutput;
import com.metalake.worker.domain.source.repository.SourceResourceRepository;
import lombok.RequiredArgsConstructor;
import org.apache.iceberg.Table;
import org.apache.iceberg.catalog.Catalog;
import org.apache.iceberg.catalog.Namespace;
import org.apache.iceberg.catalog.TableIdentifier;
import org.apache.iceberg.data.IcebergGenerics;
import org.apache.iceberg.expressions.Expressions;
import org.apache.iceberg.io.CloseableIterable;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

/**
 * Published Iceberg outputs only. The injected Catalog MUST be the existing
 * RESTCatalog pointing to Gravitino IRC (port 9001).
 *
 * Do not turn this into a generic client-supplied SQL/table/file-path proxy.
 * Before public exposure add tenant ownership and source ACL validation.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/sources/{sourceId}/iceberg-outputs")
public class IcebergCatalogAccessController {
    private final SourceResourceRepository sources;
    private final ApiNormalizedOutputRepository outputs;
    private final Catalog icebergCatalog;

    public record IcebergDescriptor(Long sourceId, Long processingJobId, String kind,
                                    String catalog, String namespace, String table,
                                    Object snapshotId, long recordCount) {}
    public record IcebergRows(IcebergDescriptor dataset, int returned,
                              List<Map<String, Object>> rows) {}

    private record Published(IcebergDescriptor descriptor, NormalizedOutput output) {}

    private Published published(Long sourceId, String kind) {
        var source = sources.findById(sourceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Source not found"));
        // Add per-tenant authorization BEFORE accessing the catalog in production.
        Long jobId = source.getCurrentPublishedJobId();
        if (jobId == null) throw new ResponseStatusException(HttpStatus.CONFLICT, "No published job");
        String expectedKind = kind.toUpperCase(Locale.ROOT);
        NormalizedOutput output = outputs.findByProcessingJob_Id(jobId).stream()
                .filter(o -> expectedKind.equals(o.getOutputType())
                        && "ICEBERG".equals(o.getStorageType().name()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No published Iceberg dataset of requested kind"));
        String ns = output.getNamespaceName();
        String tableName = output.getObjectName();
        // Trust the published descriptor from PostgreSQL, not client-supplied identifiers.
        if (ns == null || tableName == null || !ns.matches("[A-Za-z_][A-Za-z_0-9]*")
                || !tableName.matches("[A-Za-z_][A-Za-z_0-9]*")) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Invalid published table descriptor");
        }
        Object snapshot = output.getProperties() == null ? null : output.getProperties().get("snapshotId");
        return new Published(new IcebergDescriptor(sourceId, jobId, expectedKind,
                output.getCatalogName(), ns, tableName, snapshot, output.getRowCount()), output);
    }

    @GetMapping("/{kind}")
    @Transactional(readOnly = true)
    public IcebergDescriptor describe(@PathVariable Long sourceId, @PathVariable String kind) {
        var dataset = published(sourceId, kind).descriptor();
        // Exercise IRC metadata lookup, rather than only returning the PostgreSQL descriptor.
        icebergCatalog.loadTable(TableIdentifier.of(Namespace.of(dataset.namespace()), dataset.table()));
        return dataset;
    }

    @GetMapping("/{kind}/rows")
    @Transactional(readOnly = true)
    public IcebergRows rows(@PathVariable Long sourceId,
                            @PathVariable String kind,
                            @RequestParam(defaultValue = "5") int limit,
                            @RequestParam(defaultValue = "false") boolean includeContent) throws Exception {
        if (limit < 1 || limit > 50) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit must be 1..50");
        var published = published(sourceId, kind);
        var d = published.descriptor();
        Table t = icebergCatalog.loadTable(TableIdentifier.of(Namespace.of(d.namespace()), d.table()));
        if (t.schema().findField("source_id") == null || t.schema().findField("processing_job_id") == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Iceberg table lacks source/job scope columns");
        }
        var names = t.schema().columns().stream()
                .map(f -> f.name())
                .filter(name -> includeContent || !isLargeContent(name))
                .toList();
        // Query only the published source/job, even though the table is shared.
        var predicate = Expressions.and(
                Expressions.equal("source_id", sourceId),
                Expressions.equal("processing_job_id", d.processingJobId()));
        var scan = IcebergGenerics.read(t).where(predicate).select(names);
        // If a historical snapshot was recorded, pin reads to that publication.
        if (d.snapshotId() instanceof Number snapshot) scan = scan.useSnapshot(snapshot.longValue());
        List<Map<String, Object>> records = new ArrayList<>();
        try (CloseableIterable<org.apache.iceberg.data.Record> results = scan.build()) {
            for (org.apache.iceberg.data.Record record : results) {
                Map<String, Object> item = new LinkedHashMap<>();
                for (String name : names) {
                    Object value = record.getField(name);
                    item.put(name, safeValue(value));
                }
                records.add(item);
                if (records.size() == limit) break;
            }
        }
        return new IcebergRows(d, records.size(), records);
    }

    private static boolean isLargeContent(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        return n.equals("content") || n.equals("content_json") || n.equals("source_code")
                || n.equals("raw_content");
    }

    private static Object safeValue(Object value) {
        if (value == null || value instanceof Number || value instanceof Boolean || value instanceof String) return value;
        return value.toString();
    }
}
