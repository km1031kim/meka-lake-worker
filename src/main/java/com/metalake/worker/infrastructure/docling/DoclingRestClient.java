
package com.metalake.worker.infrastructure.docling;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.nio.file.Path;
import java.util.Map;

@Component
public class DoclingRestClient {

    private final RestClient restClient;

    public DoclingRestClient(
            @Value("${metalake.docling.base-url}") String baseUrl
    ) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    public Object convertPdf(Path pdfPath, String originalName) {

        FileSystemResource resource = new FileSystemResource(pdfPath) {
            @Override
            public String getFilename() {
                return originalName;
            }
        };

        MultiValueMap<String, Object> form =
                new LinkedMultiValueMap<>();

        form.add("files", resource);
        form.add("from_formats", "pdf");
        form.add("to_formats", "json");
        form.add("do_ocr", "false");

        Map<?, ?> response = restClient.post()
                .uri("/v1/convert/file")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .accept(MediaType.APPLICATION_JSON)
                .body(form)
                .retrieve()
                .body(Map.class);

        if (response == null) {
            throw new IllegalStateException("Empty Docling response");
        }

        Object status = response.get("status");

        if (!"success".equals(status)
                && !"partial_success".equals(status)) {
            throw new IllegalStateException(
                    "Docling conversion failed: " + status
            );
        }

        if (!(response.get("document") instanceof Map<?, ?> document)) {
            throw new IllegalStateException("Missing Docling document");
        }

        Object json = document.get("json_content");

        if (json == null) {
            throw new IllegalStateException("Missing Docling JSON");
        }

        return json;
    }
}
