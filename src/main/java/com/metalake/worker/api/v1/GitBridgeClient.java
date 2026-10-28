package com.metalake.worker.api.v1;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

@Service
public class GitBridgeClient {
    private final ObjectMapper json;
    private final String url;
    private final String token;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public GitBridgeClient(ObjectMapper json,
            @Value("${metalake.git.service-url:http://localhost:8110}") String url,
            @Value("${metalake.git.service-token:}") String token) {
        this.json = json;
        this.url = url.replaceAll("/+$", "");
        this.token = token;
    }

    public record BranchRequest(String repositoryUrl, String credentialRef) {}
    public record BranchResponse(List<String> branches) {}
    public record GitJobRequest(Long sourceId, Long jobId, String repositoryUrl,
                                String branch, String credentialRef) {}
    public record GitOutput(String kind, String catalog, String namespace, String table,
                            long recordCount, Long snapshotId, String tableLocation) {}
    public record GitFileset(String metalake, String catalog, String schema, String name) {}
    public record GitJobResult(String sourceRevision, String mirrorUri, GitFileset fileset,
                               List<GitOutput> outputs, int processedFiles,
                               int mirroredFiles, List<String> processedPaths) {}

    public BranchResponse branches(String url, String credentialRef) {
        return post("/internal/git/branches", new BranchRequest(url, credentialRef), BranchResponse.class);
    }

    public GitJobResult normalize(GitJobRequest request) {
        return post("/internal/git/normalize", request, GitJobResult.class);
    }

    private <T> T post(String path, Object body, Class<T> responseType) {
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url + path))
                    .timeout(Duration.ofMinutes(30))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
            if (token != null && !token.isBlank()) request.header("X-Internal-Token", token);
            HttpResponse<String> response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                String detail = response.body();

                throw new IllegalStateException(
                        "Git worker HTTP " + response.statusCode()
                                + " | " + detail
                );
            }
            return json.readValue(response.body(), responseType);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Git worker interrupted", e);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Git worker unavailable", e);
        }
    }
}
