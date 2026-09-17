package com.gamer.data.map.level;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Level JSON 文件加载器。
 *
 * <p>
 * 使用 Jackson 进行结构化 JSON 解析，不依赖字段顺序或固定文本格式。
 * </p>
 */
public final class LevelFileLoader {

    private LevelFileLoader() {
    }

    public static List<LevelNodeBean> load(File file) {
        List<LevelNodeBean> list = new ArrayList<>();
        if (file == null || !file.exists()) {
            return list;
        }

        FileInputStream fis = null;
        InputStreamReader isr = null;
        BufferedReader reader = null;
        try {
            fis = new FileInputStream(file);
            isr = new InputStreamReader(fis, StandardCharsets.UTF_8);
            reader = new BufferedReader(isr);
            JsonNode rootNode = LevelJsonMapper.MAPPER.readTree(reader);
            List<LevelJsonNode> nodes = extractNodes(rootNode);
            if (nodes == null || nodes.isEmpty()) {
                return list;
            }

            for (LevelJsonNode node : nodes) {
                parseNodeRecursively(node, list);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (Exception ignore) {
            }
            try {
                if (isr != null) {
                    isr.close();
                }
            } catch (Exception ignore) {
            }
            try {
                if (fis != null) {
                    fis.close();
                }
            } catch (Exception ignore) {
            }
        }
        return list;
    }

    private static List<LevelJsonNode> extractNodes(JsonNode rootNode) {
        if (rootNode == null) {
            return null;
        }

        if (rootNode.isArray()) {
            return LevelJsonMapper.MAPPER.convertValue(rootNode, new TypeReference<List<LevelJsonNode>>() {
            });
        }

        JsonNode nodesNode = rootNode.get("nodes");
        if (nodesNode == null || !nodesNode.isArray()) {
            return null;
        }
        return LevelJsonMapper.MAPPER.convertValue(nodesNode, new TypeReference<List<LevelJsonNode>>() {
        });
    }

    private static LevelNodeBean parseNode(LevelJsonNode node) {
        int x = 0;
        int z = 0;
        if (node.position != null) {
            x = node.position.x;
            z = node.position.z;
        }
        String param = node.param == null ? "" : node.param.trim();

        return new LevelNodeBean(x, z, node.type, node.dataId, node.belongId, node.nextDataId, node.textId,
            node.indexId, param);
    }

    private static void parseNodeRecursively(LevelJsonNode node, List<LevelNodeBean> list) {
        if (node == null || list == null) {
            return;
        }

        list.add(parseNode(node));

        if (node.children != null && !node.children.isEmpty()) {
            for (LevelJsonNode child : node.children) {
                parseNodeRecursively(child, list);
            }
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class LevelJsonNode {
        @JsonProperty("DataId")
        private int dataId;
        @JsonProperty("BelongId")
        private int belongId;
        @JsonProperty("Type")
        private int type;
        @JsonProperty("NextDataId")
        private int nextDataId;
        @JsonProperty("TextId")
        private int textId;
        @JsonProperty("IndexId")
        private int indexId;
        @JsonProperty("Param")
        private String param;
        @JsonProperty("Position")
        private PositionJson position;
        @JsonProperty("children")
        private List<LevelJsonNode> children;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class PositionJson {
        @JsonProperty("x")
        private int x;
        @JsonProperty("z")
        private int z;
    }
}