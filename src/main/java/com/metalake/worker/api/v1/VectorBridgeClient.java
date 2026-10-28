package com.metalake.worker.api.v1;

import com.metalake.worker.processing.document.CanonicalDocument;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/** Internal bridge: Lance IO & Gravitino model resolution live in the Python vector service. */
@Service
public class VectorBridgeClient {
    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();
    private final String baseUrl;
    private final String token;

    public VectorBridgeClient(ObjectMapper json,
            @Value("${metalake.vector.service-url:http://localhost:8108}") String baseUrl,
            @Value("${metalake.vector.service-token:}") String token) {
        this.json = json;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.token = token;
    }

    public record VectorManifest(String datasetUri, long recordCount,
                                 String model, int modelVersion, int dimension,
                                 long datasetVersion, String catalog,
                                 String namespace, String table) {}
    public record DocumentPayload(String documentId, String sourcePath, String contentJson) {}
    public record IngestRequest(Long sourceId, Long jobId, List<DocumentPayload> documents) {}
    public record SearchRequest(Long sourceId, Long jobId, String catalog,
                                String namespace, String table, String query, int topK) {}
    public record SearchHit(String chunkId, String documentId, String sourcePath,
                            int pageNo, String sectionPath, String sourceRef,
                            String content, double distance) {}
    public record SearchResponse(Long sourceId, Long jobId, List<SearchHit> hits) {}

    public VectorManifest ingest(Long sourceId, Long jobId, List<CanonicalDocument> documents) {
        if (documents.isEmpty()) throw new IllegalArgumentException("No documents to embed");
        var entries = documents.stream().map(doc -> new DocumentPayload(
                doc.documentId(), doc.sourcePath(), json.writeValueAsString(doc))).toList();
        return post("/internal/vectors/ingest", new IngestRequest(sourceId, jobId, entries),
                VectorManifest.class);
    }

    public SearchResponse search(Long sourceId, Long jobId,
                                 String catalog, String namespace, String table,
                                 String query, int topK) {
        return post("/internal/vectors/search",
                new SearchRequest(sourceId, jobId, catalog, namespace, table, query, topK),
                SearchResponse.class);
    }

    private <T> T post(String path, Object data, Class<T> type) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofMinutes(10))
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(data)));
            if (token != null && !token.isBlank()) builder.header("X-Internal-Token", token);
            HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                // Do not include service response body: may contain internal URLs.
                throw new IllegalStateException("Vector service HTTP " + response.statusCode());
            }
            return json.readValue(response.body(), type);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Vector service call interrupted", interrupted);
        } catch (java.io.IOException ioe) {
            throw new IllegalStateException("Vector service unavailable", ioe);
        }
    }
}
