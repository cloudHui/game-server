package com.cloud.hub.web.learning.service.poetry;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Reads paged poetry metadata, then seeks only selected bodies from local JSONL. */
public final class PoetryCatalogReader {
    private static final int LIMIT = 50;
    private final Path data;
    private final Path catalog;
    private final ObjectMapper mapper;

    public PoetryCatalogReader(Path root, ObjectMapper mapper) {
        this.data = root.resolve("poetry.jsonl");
        this.catalog = root.resolve("poetry-idx").resolve("all.tsv");
        this.mapper = mapper;
    }

    public boolean isReady() { return Files.isRegularFile(data) && Files.isRegularFile(catalog); }

    public Map<String, Object> page(String dynasty, String author, int page, int size) throws IOException {
        int safeSize = Math.max(1, Math.min(size, LIMIT));
        int safePage = Math.max(1, page);
        long from = (long) (safePage - 1) * safeSize;
        long matched = 0;
        List<long[]> hits = new ArrayList<>();
        Map<String, Integer> dynasties = new LinkedHashMap<>(), authors = new LinkedHashMap<>();
        try (BufferedReader reader = Files.newBufferedReader(catalog, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("\t", 5);
                if (parts.length < 5) continue;
                String rowDynasty = parts[2], rowAuthor = parts[3];
                dynasties.merge(rowDynasty, 1, Integer::sum);
                if (dynasty.isEmpty() || dynasty.equals(rowDynasty)) if (!rowAuthor.isEmpty()) authors.merge(rowAuthor, 1, Integer::sum);
                if (!dynasty.isEmpty() && !dynasty.equals(rowDynasty)) continue;
                if (!author.isEmpty() && !rowAuthor.contains(author)) continue;
                if (matched >= from && hits.size() < safeSize) {
                    try { hits.add(new long[]{Long.parseLong(parts[0]), Integer.parseInt(parts[1])}); }
                    catch (NumberFormatException ignored) { }
                }
                matched++;
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", load(hits));
        result.put("tags", tags(authors, 50));
        result.put("dynasties", tags(dynasties, 0));
        result.put("page", safePage); result.put("size", safeSize); result.put("total", matched);
        result.put("pageCount", Math.max(1, (matched + safeSize - 1) / safeSize));
        return result;
    }

    private List<Map<String, Object>> load(List<long[]> hits) throws IOException {
        List<Map<String, Object>> result = new ArrayList<>();
        try (RandomAccessFile file = new RandomAccessFile(data.toFile(), "r")) {
            for (long[] hit : hits) {
                byte[] bytes = new byte[(int) hit[1]]; file.seek(hit[0]); file.readFully(bytes);
                JsonNode node = mapper.readTree(new String(bytes, StandardCharsets.UTF_8));
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("title", node.path("title").asText()); row.put("author", node.path("author").asText(""));
                row.put("dynasty", node.path("dynasty").asText(""));
                List<String> paragraphs = new ArrayList<>(); node.path("paragraphs").forEach(value -> paragraphs.add(value.asText()));
                row.put("paragraphs", paragraphs); result.add(row);
            }
        }
        return result;
    }

    private List<Map<String, Object>> tags(Map<String, Integer> counts, int limit) {
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(counts.entrySet());
        entries.sort((a, b) -> { int c = Integer.compare(b.getValue(), a.getValue()); return c != 0 ? c : a.getKey().compareTo(b.getKey()); });
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : entries) {
            if (limit > 0 && result.size() >= limit) break;
            Map<String, Object> row = new LinkedHashMap<>(); row.put("id", entry.getKey()); row.put("name", entry.getKey()); row.put("count", entry.getValue()); result.add(row);
        }
        return result;
    }
}
