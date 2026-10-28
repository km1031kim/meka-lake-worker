package com.metalake.worker.api.v1;

import com.metalake.worker.domain.source.entity.SourceResource;
import com.metalake.worker.domain.source.entity.SourceStatus;
import com.metalake.worker.domain.source.entity.SourceType;
import com.metalake.worker.domain.source.repository.SourceResourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/** Read-only metadata browser; row preview requires separate JDBC execution with vended credentials. */
@RestController
@RequestMapping("/api/sources/{sourceId}/schemas")
@RequiredArgsConstructor
public class DatabaseMetadataController {
    private static final Pattern NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private final SourceResourceRepository sources;
    private final GravitinoDatabaseCatalogClient catalogs;

    private static void checkIdentifier(String value) {
        if (!NAME.matcher(value).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported schema/table identifier");
        }
    }

    private SourceResource resolve(Long sourceId) {
        SourceResource source = sources.findById(sourceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Source not found"));
        if (source.getSourceType() != SourceType.DATABASE || source.getStatus() != SourceStatus.ACTIVE
                || source.getGravitinoCatalog() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Source is not an active DATABASE source");
        }
        // Apply the same current-user/tenant ownership checks as SourceController before exposing metadata.
        return source;
    }

    private Map<String, Object> result(Long sourceId, String catalog, Map<String, Object> raw) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("sourceId", sourceId);
        out.put("catalog", catalog);
        out.putAll(raw); // Gravitino's native response ('schemas', 'tables', 'table', ...).
        return out;
    }

    @GetMapping
    public Map<String, Object> schemas(@PathVariable Long sourceId) {
        var s = resolve(sourceId);
        return result(sourceId, s.getGravitinoCatalog(), catalogs.schemas(s.getGravitinoCatalog()));
    }

    @GetMapping("/{schema}/tables")
    public Map<String, Object> tables(@PathVariable Long sourceId, @PathVariable String schema) {
        checkIdentifier(schema);
        var s = resolve(sourceId);
        return result(sourceId, s.getGravitinoCatalog(), catalogs.tables(s.getGravitinoCatalog(), schema));
    }

    @GetMapping("/{schema}/tables/{table}")
    public Map<String, Object> table(@PathVariable Long sourceId,
                                     @PathVariable String schema, @PathVariable String table) {
        checkIdentifier(schema);
        checkIdentifier(table);
        var s = resolve(sourceId);
        return result(sourceId, s.getGravitinoCatalog(), catalogs.table(s.getGravitinoCatalog(), schema, table));
    }
}
