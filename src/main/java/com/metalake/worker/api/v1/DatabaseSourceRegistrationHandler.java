package com.metalake.worker.api.v1;

import com.metalake.worker.domain.source.repository.SourceResourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static com.metalake.worker.api.v1.ApiModels.*;

/** Registers a relational Gravitino catalog, then records its reference in source_resource. */
@Component
@RequiredArgsConstructor
public class DatabaseSourceRegistrationHandler implements SourceRegistrationHandler {

    private final GravitinoDatabaseCatalogClient catalogs;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final SourceResourceRepository sources;

    // Do not allow an untrusted Source registration to turn Gravitino into a network scanner.
    // Set DATABASE_SOURCE_ALLOWED_HOSTS to a comma-separated list of approved hosts/IPs.
    @Value("${DATABASE_SOURCE_ALLOWED_HOSTS:}")
    private String allowedHosts;

    @Override public boolean supports(String sourceType) {
        return "DATABASE".equalsIgnoreCase(sourceType);
    }

    @Override public SourceResponse register(RegisterSourceRequest request) {

        if (request.connection() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Database connection is required");
        }

        var c = request.connection();

        DatabaseCatalogSpec spec;

        try {
            int port = c.port() == null
                    ? ("MYSQL".equalsIgnoreCase(c.provider()) ? 3306 : 5432) : c.port();
            spec = new DatabaseCatalogSpec(c.provider(), c.host(), port, c.database(),
                    c.username(), c.password());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }

        if (allowedHosts == null || allowedHosts.isBlank() ||
                java.util.Arrays.stream(allowedHosts.split(","))
                        .map(String::trim).noneMatch(host -> host.equalsIgnoreCase(spec.host()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Database host is not in DATABASE_SOURCE_ALLOWED_HOSTS");
        }

        // Prevent a duplicate physical DB from being registered as multiple JDBC catalogs.
        Long existing = jdbc.queryForObject("""
                SELECT COUNT(*) FROM public.source_resource
                 WHERE source_type = 'DATABASE'
                   AND configuration ->> 'provider' = ?
                   AND configuration ->> 'host' = ?
                   AND configuration ->> 'port' = ?
                   AND COALESCE(configuration ->> 'database', '') = ?
                """, Long.class, spec.provider(), spec.host(), Integer.toString(spec.port()),
                spec.database() == null ? "" : spec.database());

        if (existing != null && existing > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A DATABASE source for this database or instance is already registered");
        }

        String catalogName = "db_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String configuration = json.writeValueAsString(spec.publicConfiguration());
        catalogs.createCatalog(catalogName, request.name(), spec);

        try {
            Long id = jdbc.queryForObject("""
                    INSERT INTO public.source_resource
                        (name, source_type, access_mode, configuration,
                         gravitino_metalake, gravitino_catalog, status)
                    VALUES (?, 'DATABASE', 'DIRECT', CAST(? AS jsonb), ?, ?, 'ACTIVE')
                    RETURNING id
                    """, Long.class, request.name(), configuration,
                    catalogs.metalake(), catalogName);

            return SourceController.toResponse(sources.findById(id)
                    .orElseThrow(() -> new IllegalStateException("New DATABASE source not found")));

        } catch (RuntimeException ex) {
            catalogs.dropCatalogQuietly(catalogName);
            throw ex;
        }
    }
}
