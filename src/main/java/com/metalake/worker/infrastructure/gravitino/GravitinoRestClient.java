
package com.metalake.worker.infrastructure.gravitino;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component
public class GravitinoRestClient implements GravitinoClient {

    private static final String BASE_PATH =
            "/api/metalakes/{metalake}/catalogs/{catalog}/schemas";

    private final RestClient restClient;
    private final String metalake;

    public GravitinoRestClient(
            @Value("${metalake.gravitino.base-url}") String baseUrl,
            @Value("${metalake.gravitino.metalake}") String metalake
    ) {
        this.metalake = metalake;

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(
                        HttpHeaders.ACCEPT,
                        "application/vnd.gravitino.v1+json"
                )
                .build();
    }

    @Override
    public List<String> listSchemas(String catalog) {
        return fetchIdentifiers(BASE_PATH, metalake, catalog);
    }

    @Override
    public List<String> listFilesets(String catalog, String schema) {
        return fetchIdentifiers(
                BASE_PATH + "/{schema}/filesets",
                metalake,
                catalog,
                schema
        );
    }

    @Override
    public GravitinoFileset loadFileset(
            String catalog,
            String schema,
            String fileset
    ) {
        FilesetResponse response = restClient.get()
                .uri(
                        BASE_PATH + "/{schema}/filesets/{fileset}",
                        metalake,
                        catalog,
                        schema,
                        fileset
                )
                .retrieve()
                .body(FilesetResponse.class);

        if (response == null
                || response.code() != 0
                || response.fileset() == null) {
            throw new IllegalStateException(
                    "Failed to load Gravitino fileset: "
                            + catalog + "." + schema + "." + fileset
            );
        }

        return response.fileset();
    }

    @Override
    public void createExternalFileset(
            String catalog,
            String schema,
            String filesetName,
            String storageLocation,
            Map<String, String> properties
    ) {
        Map<String, Object> body = Map.of(
                "name", filesetName,
                "type", "EXTERNAL",
                "storageLocations",
                Map.of(filesetName, storageLocation),
                "properties", properties
        );

        FilesetResponse response = restClient.post()
                .uri(
                        BASE_PATH + "/{schema}/filesets",
                        metalake, catalog, schema
                )
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(FilesetResponse.class);

        if (response == null
                || response.code() != 0
                || response.fileset() == null) {
            throw new IllegalStateException(
                    "Failed to create Fileset: " + filesetName
            );
        }
    }

    private List<String> fetchIdentifiers(
            String path,
            Object... uriVariables
    ) {
        IdentifiersResponse response = restClient.get()
                .uri(path, uriVariables)
                .retrieve()
                .body(IdentifiersResponse.class);

        if (response == null
                || !Integer.valueOf(0).equals(response.code())
                || response.identifiers() == null) {
            throw new IllegalStateException(
                    "Invalid Gravitino response"
            );
        }

        return response.identifiers().stream()
                .map(Identifier::name)
                .toList();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Identifier(
            List<String> namespace,
            String name
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record IdentifiersResponse(
            Integer code,
            List<Identifier> identifiers
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record FilesetResponse(
            Integer code,
            GravitinoFileset fileset
    ) {}
}
