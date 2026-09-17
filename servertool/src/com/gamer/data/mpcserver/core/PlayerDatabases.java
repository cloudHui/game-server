package com.gamer.data.mpcserver.core;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gamer.data.log.Log;

/** 启动时加载的独立连接配置；不引用旧 DbConfig，不输出密码。 */
public final class PlayerDatabases {
    private final Map<String, JsonNode> connections = new LinkedHashMap<>(); // 名称 → 六字段连接配置

    /** @param file jar 同目录配置 @param mapper JSON 解析器 @param log 日志 */
    public PlayerDatabases(File file, ObjectMapper mapper, Log log) {
        if (!file.isFile()) {
            log.logMessage("[player-db] 未配置，跳过数据库: " + file);
            return;
        }
        try {
            JsonNode rows = mapper.readTree(file).get("connections");
            if (rows == null || !rows.isArray()) {
                throw new IllegalArgumentException();
            }
            for (JsonNode row : rows) {
                for (String field : new String[] {"name", "host", "database", "user", "password"}) {
                    if (!row.path(field).isTextual()
                        || (!"password".equals(field) && row.path(field).asText().trim().isEmpty())) {
                        throw new IllegalArgumentException();
                    }
                }
                int port = row.path("port").asInt(0);
                if (row.size() != 6 || !row.path("port").isIntegralNumber() || !row.path("port").canConvertToInt()
                    || port < 1 || port > 65535
                    || connections.put(row.get("name").asText(), row.deepCopy()) != null) {
                    throw new IllegalArgumentException();
                }
            }
            log.logMessage("[player-db] 已加载连接: " + connections.keySet());
        } catch (Exception e) {
            connections.clear();
            // JSON 错误可能包含带密码的原文，只报告路径与结构要求。
            log.logMessage("[player-db] 配置无效，已禁用: " + file
                + "；每项必须为 name/host/port/database/user/password，名称不可重复");
        }
    }

    /** @param name 配置名称 @return 独立配置；不存在时失败，绝不回退旧账号 */
    public JsonNode require(String name) {
        JsonNode row = connections.get(name);
        if (row == null) {
            throw new IllegalArgumentException("数据库未配置: " + name);
        }
        return row.deepCopy();
    }

    /** @return 可提供给 AI 的连接列表，不包含密码 */
    public List<Map<String, Object>> describe() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (JsonNode row : connections.values()) {
            Map<String, Object> item = new LinkedHashMap<>();
            for (String field : new String[] {"name", "host", "database"}) {
                item.put(field, row.get(field).asText());
            }
            item.put("port", row.get("port").intValue());
            result.add(item); // 独立结果，不暴露内部配置，无需只读包装。
        }
        return result;
    }
}
