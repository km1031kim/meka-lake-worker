
package com.metalake.worker.processing.document;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class DoclingNormalizer {

    private static final Pattern HEADING_NUMBER =
            Pattern.compile("^\\s*(\\d+(?:\\.\\d+)*)(?:\\.)?\\s+");

    private final ObjectMapper objectMapper;

    public CanonicalDocument normalize(
            PdfExtractionService.PdfExtractionResult result
    ) {
        JsonNode root = objectMapper.valueToTree(result.doclingJson());

        List<JsonNode> orderedItems = readOrderedItems(root);
        if (orderedItems.isEmpty()) {
            throw new IllegalStateException("No Docling content found");
        }

        List<String> sections = new ArrayList<>();
        List<CanonicalDocument.CanonicalBlock> blocks =
                new ArrayList<>();

        for (JsonNode item : orderedItems) {
            String type = item.path("label").asText("text");
            String text = item.path("text").asText("");

            if ("title".equals(type) ||
                    "section_header".equals(type)) {
                if (!text.isBlank()) {
                    int level = headingLevel(item, type, text);

                    while (sections.size() >= level) {
                        sections.remove(sections.size() - 1);
                    }
                    sections.add(text);
                }
            }

            CanonicalDocument.TableContent table = null;

            if ("table".equals(type)) {
                table = extractTable(item);
                text = tableToText(table);
            } else if ("picture".equals(type)) {
                text = extractCaption(root, item);
            }

            String ref = item.path("self_ref").asText("");

            blocks.add(new CanonicalDocument.CanonicalBlock(
                    ref,
                    ref,
                    blocks.size() + 1,
                    type,
                    pageNumber(item),
                    text,
                    List.copyOf(sections),
                    boundingBox(item),
                    table
            ));
        }

        int maxPage = blocks.stream()
                .map(CanonicalDocument.CanonicalBlock::pageNo)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .max()
                .orElse(0);

        String documentKey =
                result.sourceId() + ":" + result.sourcePath();

        String documentId = UUID.nameUUIDFromBytes(
                documentKey.getBytes(StandardCharsets.UTF_8)
        ).toString();

        String title = root.path("name").asText("");
        if (title.isBlank()) {
            title = result.sourcePath();
        }

        return new CanonicalDocument(
                documentId,
                result.sourceId(),
                result.sourcePath(),
                title,
                result.mimeType(),
                result.sizeBytes(),
                Math.max(root.path("pages").size(), maxPage),
                "docling",
                root.path("version").asText("unknown"),
                Instant.now(),
                List.copyOf(blocks)
        );
    }

    private List<JsonNode> readOrderedItems(JsonNode root) {
        List<JsonNode> items = new ArrayList<>();
        Set<String> visited = new HashSet<>();

        visitChildren(root, root.path("body"), visited, items);

        // 구형/불완전 JSON에 대한 POC fallback.
        // 이 경우 문서의 실제 읽기 순서를 보장하지 않는다.
        if (items.isEmpty()) {
            for (String key : List.of("texts", "tables", "pictures")) {
                for (JsonNode item : root.path(key)) {
                    items.add(item);
                }
            }
        }

        return items;
    }

    private void visitChildren(
            JsonNode root,
            JsonNode parent,
            Set<String> visited,
            List<JsonNode> result
    ) {
        for (JsonNode childRef : parent.path("children")) {
            String ref = childRef.path("$ref").asText("");

            if (!ref.startsWith("#/") || !visited.add(ref)) {
                continue;
            }

            JsonNode child = root.at(ref.substring(1));

            if (child.isMissingNode()) {
                continue;
            }

            if (ref.startsWith("#/texts/")
                    || ref.startsWith("#/tables/")
                    || ref.startsWith("#/pictures/")) {
                result.add(child);
            }

            // groups 및 제목 아래의 자식들도 순회
            visitChildren(root, child, visited, result);
        }
    }

    private int headingLevel(
            JsonNode item,
            String type,
            String text
    ) {
        if ("title".equals(type)) {
            return 1;
        }

        Matcher matcher = HEADING_NUMBER.matcher(text);

        if (matcher.find()) {
            return matcher.group(1).split("\\.").length;
        }

        return Math.max(1, item.path("level").asInt(1));
    }

    private Integer pageNumber(JsonNode item) {
        JsonNode prov = item.path("prov");

        if (!prov.isArray() || prov.isEmpty()) {
            return null;
        }

        JsonNode page = prov.get(0).path("page_no");
        return page.isNumber() ? page.asInt() : null;
    }

    private CanonicalDocument.BoundingBox boundingBox(JsonNode item) {
        JsonNode prov = item.path("prov");

        if (!prov.isArray() || prov.isEmpty()) {
            return null;
        }

        JsonNode bbox = prov.get(0).path("bbox");

        if (!bbox.path("l").isNumber()
                || !bbox.path("t").isNumber()
                || !bbox.path("r").isNumber()
                || !bbox.path("b").isNumber()) {
            return null;
        }

        return new CanonicalDocument.BoundingBox(
                bbox.path("l").asDouble(),
                bbox.path("t").asDouble(),
                bbox.path("r").asDouble(),
                bbox.path("b").asDouble()
        );
    }

    private CanonicalDocument.TableContent extractTable(JsonNode item) {
        JsonNode data = item.path("data");
        List<List<String>> rows = new ArrayList<>();

        for (JsonNode row : data.path("grid")) {
            List<String> cells = new ArrayList<>();

            for (JsonNode cell : row) {
                cells.add(cell.path("text").asText(""));
            }

            rows.add(List.copyOf(cells));
        }

        return new CanonicalDocument.TableContent(
                data.path("num_rows").asInt(rows.size()),
                data.path("num_cols").asInt(
                        rows.stream().mapToInt(List::size)
                                .max().orElse(0)
                ),
                List.copyOf(rows)
        );
    }

    private String tableToText(
            CanonicalDocument.TableContent table
    ) {
        List<String> lines = new ArrayList<>();

        for (List<String> row : table.cells()) {
            lines.add(String.join(" | ", row));
        }

        return String.join("\n", lines);
    }

    private String extractCaption(JsonNode root, JsonNode item) {
        List<String> captions = new ArrayList<>();

        for (JsonNode caption : item.path("captions")) {
            String ref = caption.path("$ref").asText("");

            if (ref.startsWith("#/")) {
                String text = root.at(ref.substring(1))
                        .path("text").asText("");

                if (!text.isBlank()) {
                    captions.add(text);
                }
            }
        }

        return String.join(" ", captions);
    }
}
