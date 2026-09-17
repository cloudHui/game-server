package com.gamer.data.file.config;

/**
 * WindowsTools 节假日配置（客户端下载调度用）。
 * <p>
 * 有对应年且日期在 off_days / work_days 中则按表判定；否则周一～五。
 * 按年维护：改下方 {@link #DEFAULT_JSON_LINES}。远端 API 不自动拉，见 {@code ConfigStore} 类注释。
 */
public final class HolidayConfig {

    /**
     * 节假日备用 JSON 行（便于按年增删日期；拼接后供解析器使用，空白可忽略）。
     */
    private static final String[] DEFAULT_JSON_LINES = {
        "[",
        "  {",
        "    \"year\": 2026,",
        "    \"data\": {",
        "      \"off_days\": [",
        "        \"2026-01-01\", \"2026-01-02\",",
        "        \"2026-02-17\", \"2026-02-18\", \"2026-02-19\", \"2026-02-20\", \"2026-02-23\",",
        "        \"2026-04-06\",",
        "        \"2026-05-01\", \"2026-05-04\", \"2026-05-05\",",
        "        \"2026-06-19\",",
        "        \"2026-09-25\",",
        "        \"2026-10-01\", \"2026-10-02\", \"2026-10-05\", \"2026-10-06\", \"2026-10-07\"",
        "      ],",
        "      \"work_days\": [",
        "        \"2026-01-04\",",
        "        \"2026-02-14\", \"2026-02-28\",",
        "        \"2026-04-11\",",
        "        \"2026-05-09\",",
        "        \"2026-09-19\", \"2026-10-10\"",
        "      ]",
        "    }",
        "  }",
        "]"
    };

    /**
     * 节假日备用 JSON（由 {@link #DEFAULT_JSON_LINES} 拼接）。
     */
    public static final String DEFAULT_JSON = joinLines();

    private HolidayConfig() {}

    /**
     * 将多行拼成单字符串（不加换行，解析器按索引扫描即可）。
     *
     * @return 拼接结果
     */
    private static String joinLines() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < HolidayConfig.DEFAULT_JSON_LINES.length; i++) {
            sb.append(HolidayConfig.DEFAULT_JSON_LINES[i]);
        }
        return sb.toString();
    }
}
