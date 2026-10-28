package com.metalake.worker.api.v1;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Public API types. SourceType is deliberately distinct from the current internal FILESET enum. */
public final class ApiModels {
    private ApiModels() {}

    public record RegisterSourceRequest(
            @NotBlank String name,
            @NotBlank String sourceType,
            @Valid @NotNull Connection connection
    ) {
        @Override public String toString() {
            return "RegisterSourceRequest[name=" + name + ",sourceType=" + sourceType + ",connection=REDACTED]";
        }
    }

    public record Connection(
            String provider,
            String endpoint,
            String bucket,
            String prefix,
            String accessKey,
            String secretKey,
            String credentialRef,
            String repositoryUrl,
            String ref,
            String host,
            Integer port,
            String database,
            String username,
            String password
    ) {
        // Compatibility for existing 12-field Connection constructor callers.
        public Connection(String provider, String endpoint, String bucket, String prefix,
                          String accessKey, String secretKey, String credentialRef,
                          String repositoryUrl, String ref, String host, Integer port,
                          String database) {
            this(provider, endpoint, bucket, prefix, accessKey, secretKey, credentialRef,
                    repositoryUrl, ref, host, port, database, null, null);
        }
        @Override public String toString() { return "Connection[REDACTED]"; }
    }

    public record FilesetRef(String metalake, String catalog, String schema, String name) {}

    public record DatabaseRef(String metalake, String catalog) {}

    public record Capabilities(boolean normalizable, boolean fileBrowsable, boolean catalogBrowsable) {}

    public record SourceResponse(Long sourceId, String name, String sourceType,
                                 String accessMode, String status, Capabilities capabilities,
                                 FilesetRef fileset,
                                 String originalGvfsUri, DatabaseRef database) {

        // Compatibility for existing 8-field SourceResponse constructor callers.
        public SourceResponse(Long sourceId, String name, String sourceType,
                              String accessMode, String status, Capabilities capabilities,
                              FilesetRef fileset, String originalGvfsUri) {
            this(sourceId, name, sourceType, accessMode, status, capabilities,
                    fileset, originalGvfsUri, null);
        }

        // Keep existing seven-argument call sites compatible.
        public SourceResponse(Long sourceId, String name, String sourceType,
                              String accessMode, String status, Capabilities capabilities,
                              FilesetRef fileset) {
            this(sourceId, name, sourceType, accessMode, status, capabilities, fileset, null);
        }
    }

    public record EmbeddingOptions(Boolean enabled) {
        public boolean isEnabled() { return Boolean.TRUE.equals(enabled); }
    }

    public record NormalizeRequest(
            @Size(max = 1000) List<@NotBlank String> relativePaths,
            @Valid EmbeddingOptions embedding
    ) {
        // Backwards compatibility with existing unit tests and Java callers.
        public NormalizeRequest(List<String> relativePaths) { this(relativePaths, null); }
    }

    public record JobAccepted(Long sourceId, Long jobId, String status, String statusUrl) {}

    public record OutputInfo(String kind, String format, String catalog, String namespace,
                             String table, Long processingJobId, Long recordCount,
                             Object snapshotId, String datasetUri, String tableLocation) {
        // Retain the original 9-argument constructor for older callers.
        public OutputInfo(String kind, String format, String catalog, String namespace,
                          String table, Long processingJobId, Long recordCount,
                          Object snapshotId, String datasetUri) {
            this(kind, format, catalog, namespace, table, processingJobId,
                 recordCount, snapshotId, datasetUri, null);
        }
    }

    public record JobResponse(Long sourceId, Long jobId, String status, String error,
                              List<OutputInfo> outputs) {}

    public record PublishedResults(Long sourceId, String status, Long processingJobId,
                                   List<OutputInfo> outputs) {}
}
