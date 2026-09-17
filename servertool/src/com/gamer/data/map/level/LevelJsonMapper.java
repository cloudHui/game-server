package com.gamer.data.map.level;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Level JSON 共用的 Jackson {@link ObjectMapper} 配置。
 */
public final class LevelJsonMapper {

    /** 忽略未知字段的 Level JSON 解析器。 */
    public static final ObjectMapper MAPPER = createMapper();

    private LevelJsonMapper() {}

    private static ObjectMapper createMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper;
    }
}
