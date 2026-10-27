
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


    @Override
    public GravitinoS3Credentials loadS3Credentials(
            String catalog,
            String schema,
            String fileset
    ) {
        String fullName = catalog + "." + schema + "." + fileset;

        CredentialResponse response = restClient.get()
                .uri(
                        "/api/metalakes/{metalake}/objects/FILESET/{fullName}/credentials",
                        metalake,
                        fullName
                )
                .retrieve()
                .body(CredentialResponse.class);

        if (response == null
                || !Integer.valueOf(0).equals(response.code())
                || response.credentials() == null) {
            throw new IllegalStateException(
                    "Failed to load Fileset credentials"
            );
        }

        CredentialData credential = response.credentials()
                .stream()
                .filter(c -> "s3-secret-key".equals(c.credentialType()))
                .findFirst()
                .orElseThrow(() ->
                        new IllegalStateException(
                                "S3 credential not found for " + fullName
                        )
                );

        Map<String, String> info = credential.credentialInfo();

        if (info == null
                || isBlank(info.get("s3-access-key-id"))
                || isBlank(info.get("s3-secret-access-key"))) {
            throw new IllegalStateException(
                    "Invalid S3 credential response"
            );
        }

        return new GravitinoS3Credentials(
                info.get("s3-access-key-id"),
                info.get("s3-secret-access-key")
        );
    }

    @Override
    public void createFilesetCatalog(
            String catalog,
            Map<String, String> properties
    ) {
        Map<String, Object> body = Map.of(
                "name", catalog,
                "type", "FILESET",
                "comment", "External object storage source",
                "properties", properties
        );

        CodeResponse response = restClient.post()
                .uri(
                        "/api/metalakes/{metalake}/catalogs",
                        metalake
                )
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.parseMediaType(
                        "application/vnd.gravitino.v1+json"
                ))
                .body(body)
                .retrieve()
                .body(CodeResponse.class);

        requireSuccess(response, "Create catalog: " + catalog);
    }


    @Override
    public void createSchema(
            String catalog,
            String schema
    ) {
        Map<String, Object> body = Map.of(
                "name", schema,
                "comment", "Object storage source schema",
                "properties", Map.of()
        );

        CodeResponse response = restClient.post()
                .uri(
                        "/api/metalakes/{metalake}/catalogs/{catalog}/schemas",
                        metalake,
                        catalog
                )
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.parseMediaType(
                        "application/vnd.gravitino.v1+json"
                ))
                .body(body)
                .retrieve()
                .body(CodeResponse.class);

        requireSuccess(
                response,
                "Create schema: " + catalog + "." + schema
        );
    }

    @Override
    public S3CatalogSettings loadS3CatalogSettings(String catalog) {

        CatalogResponse response = restClient.get()
                .uri(
                        "/api/metalakes/{metalake}/catalogs/{catalog}",
                        metalake,
                        catalog
                )
                .retrieve()
                .body(CatalogResponse.class);

        if (response == null
                || !Integer.valueOf(0).equals(response.code())
                || response.catalog() == null
                || response.catalog().properties() == null) {
            throw new IllegalStateException(
                    "Failed to load catalog settings: " + catalog
            );
        }

        Map<String, String> properties =
                response.catalog().properties();

        String endpoint = properties.get("s3-endpoint");

        if (isBlank(endpoint)) {
            throw new IllegalStateException(
                    "S3 endpoint is missing: " + catalog
            );
        }

        return new S3CatalogSettings(
                endpoint,
                properties.getOrDefault("s3-region", "us-east-1")
        );
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record CredentialResponse(
            Integer code,
            List<CredentialData> credentials
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record CredentialData(
            String credentialType,
            Map<String, String> credentialInfo
    ) {}


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

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record CodeResponse(Integer code) {
    }

    private void requireSuccess(
            CodeResponse response,
            String operation
    ) {
        if (response == null
                || !Integer.valueOf(0).equals(response.code())) {
            throw new IllegalStateException(
                    "Gravitino operation failed: " + operation
            );
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record CatalogResponse(
            Integer code,
            CatalogData catalog
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record CatalogData(
            Map<String, String> properties
    ) {}
}
