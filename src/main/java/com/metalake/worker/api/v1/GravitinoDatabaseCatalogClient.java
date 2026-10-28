package com.metalake.worker.api.v1;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Gravitino metadata API ONLY. It does not execute JDBC SELECT statements. */
@Slf4j
@Component
public class GravitinoDatabaseCatalogClient {

    private final RestTemplate http;
    private final String gravitinoUri;
    private final String metalake;

    public GravitinoDatabaseCatalogClient(
            @Value("${metalake.gravitino.base-url}") String baseUrl,
            @Value("${metalake.gravitino.metalake}") String metalake
    ) {
        this.gravitinoUri = baseUrl.replaceAll("/+$", "");
        this.metalake = metalake;

        SimpleClientHttpRequestFactory factory =
                new SimpleClientHttpRequestFactory();

        factory.setConnectTimeout(5000);
        factory.setReadTimeout(15000);

        this.http = new RestTemplate(factory);
    }

    public String metalake() {
        return metalake;
    }

    private URI uri(String... segments) {
        var builder = UriComponentsBuilder
                .fromUriString(gravitinoUri)
                .pathSegment("api", "metalakes", metalake);

        for (String segment : segments) {
            builder.pathSegment(segment);
        }

        return builder.build().encode().toUri();
    }

    private HttpHeaders headers() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.setAccept(List.of(MediaType.parseMediaType("application/vnd.gravitino.v1+json")));
        return h;
    }

    public void createCatalog(String catalogName, String sourceName, DatabaseCatalogSpec spec) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("name", catalogName);
        payload.put("type", "RELATIONAL");
        payload.put("provider", spec.gravitinoProvider());
        payload.put("comment", "Meta Lake source: " + sourceName);
        payload.put("properties", spec.catalogProperties());
        try {
            http.exchange(
                    uri("catalogs"),
                    HttpMethod.POST,
                    new HttpEntity<>(payload, headers()),
                    String.class
            );

        } catch (HttpStatusCodeException e) {

            log.error(
                    "Gravitino catalog registration failed. " +
                            "catalog={}, status={}",
                    catalogName,
                    e.getStatusCode().value()
            );

            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Gravitino catalog registration failed",
                    e
            );

        } catch (RestClientException e) {

            log.error(
                    "Gravitino request failed. " +
                            "catalog={}, exceptionType={}",
                    catalogName,
                    e.getClass().getSimpleName()
            );

            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Gravitino connection failed",
                    e
            );
        }
    }

    public void dropCatalogQuietly(String catalogName) {
        try {
            http.exchange(uri("catalogs", catalogName), HttpMethod.DELETE,
                    new HttpEntity<>(headers()), String.class);
        } catch (RuntimeException ignored) {
            // Compensation is best effort. Failed cleanup needs operator reconciliation.
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> get(String... segments) {
        try {
            ResponseEntity<Map> resp = http.exchange(uri(segments), HttpMethod.GET,
                    new HttpEntity<>(headers()), Map.class);
            return resp.getBody() == null ? Map.of() : (Map<String, Object>) resp.getBody();
        } catch (RestClientException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Gravitino metadata request failed");
        }
    }

    public Map<String, Object> schemas(String catalog) {
        return get("catalogs", catalog, "schemas");
    }

    public Map<String, Object> tables(String catalog, String schema) {
        return get("catalogs", catalog, "schemas", schema, "tables");
    }

    public Map<String, Object> table(String catalog, String schema, String table) {
        return get("catalogs", catalog, "schemas", schema, "tables", table);
    }
}
