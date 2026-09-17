package com.gamer.data.file.client.holiday;

import java.util.ArrayList;
import java.util.List;

/**
 * 节假日 JSON 字符串解析与格式化（无第三方 JSON 库）。
 * <p>
 * 仅处理 {@code [{year,data:{off_days,work_days}}]} 及 timor.tech API 的 holiday 对象。
 */
final class JsonParser {

    private JsonParser() {}

    /**
     * 从本地配置 JSON 解析全部年份条目。
     *
     * @param json
     *            {@link HolidayConfig} 拼接后的 JSON
     * @return 年份列表；无有效条目时空列表
     */
    static List<YearData> parseConfigJson(String json) {
        List<YearData> list = new ArrayList<>();
        if (json == null || json.isEmpty()) {
            return list;
        }

        int pos = 0;
        while (pos < json.length()) {
            int yearIdx = json.indexOf("\"year\"", pos);
            if (yearIdx < 0) {
                break;
            }
            int year = parseJsonIntAfterColon(json, yearIdx + 6);
            if (year > 0) {
                String section = extractYearDataSection(json, year);
                if (section != null) {
                    YearData data = new YearData();
                    data.year = year;
                    List<String> offList = extractDatesFromArray(section, "off_days");
                    for (String dateStr : offList) {
                        if (isValidDate(dateStr)) {
                            data.offDays.add(dateStr);
                        }
                    }
                    List<String> workList = extractDatesFromArray(section, "work_days");
                    for (String dateStr : workList) {
                        if (isValidDate(dateStr)) {
                            data.workDays.add(dateStr);
                        }
                    }
                    list.add(data);
                }
            }
            pos = yearIdx + 6;
        }
        return list;
    }

    /**
     * 从 timor.tech 年节假日 API 正文解析 off/work（自行 GET 后可用；运行时不请求）。
     * 仅在 {@code "holiday":{...}} 块内扫描。
     *
     * @param body
     *            API 响应
     * @param year
     *            年份
     * @return 解析结果；无效时 null
     */
    static YearData parseApiHolidayBody(String body, int year) {
        if (body == null || !body.contains("\"code\":0")) {
            return null;
        }

        int blockStart = body.indexOf("\"holiday\":{");
        if (blockStart < 0) {
            return null;
        }
        blockStart = body.indexOf('{', blockStart + 10);
        int blockEnd = findMatchingBrace(body, blockStart);
        if (blockStart < 0 || blockEnd < 0) {
            return null;
        }

        String block = body.substring(blockStart, blockEnd + 1);
        YearData data = new YearData();
        data.year = year;
        int pos = 0;
        while (pos < block.length()) {
            int dateIdx = block.indexOf("\"date\":\"", pos);
            if (dateIdx < 0) {
                break;
            }
            int dateStart = dateIdx + 8;
            int dateEnd = block.indexOf('"', dateStart);
            if (dateEnd < 0) {
                break;
            }
            String dateStr = block.substring(dateStart, dateEnd);
            if (!isValidDate(dateStr)) {
                pos = dateEnd + 1;
                continue;
            }

            int objStart = block.lastIndexOf('{', dateIdx);
            int objEnd = block.indexOf('}', dateIdx);
            if (objStart < 0 || objEnd < 0) {
                break;
            }
            String obj = block.substring(objStart, objEnd + 1);
            if (obj.contains("\"holiday\":true")) {
                data.offDays.add(dateStr);
            } else if (obj.contains("\"holiday\":false")) {
                data.workDays.add(dateStr);
            }
            pos = dateEnd + 1;
        }
        return data.isEmpty() ? null : data;
    }

    /** 查找指定年份条目。 */
    static YearData findYear(List<YearData> config, int year) {
        for (YearData data : config) {
            if (data.year == year) {
                return data;
            }
        }
        return null;
    }

    /** 提取指定年份的 data 段文本。 */
    private static String extractYearDataSection(String json, int year) {
        int yearIdx = findYearFieldIndex(json, year);
        if (yearIdx < 0) {
            return null;
        }
        int dataIdx = json.indexOf("\"data\"", yearIdx);
        if (dataIdx < 0) {
            return null;
        }
        int nextYearIdx = json.indexOf("\"year\"", yearIdx + 6);
        if (nextYearIdx < 0) {
            return json.substring(dataIdx);
        }
        return json.substring(dataIdx, nextYearIdx);
    }

    /** 定位 {@code "year":YYYY} 字段，避免误匹配更长数字。 */
    private static int findYearFieldIndex(String json, int year) {
        String token = "\"year\":" + year;
        int idx = json.indexOf(token);
        if (idx >= 0 && isYearValueBoundary(json, idx + token.length())) {
            return idx;
        }
        token = "\"year\": " + year;
        idx = json.indexOf(token);
        if (idx >= 0 && isYearValueBoundary(json, idx + token.length())) {
            return idx;
        }
        return -1;
    }

    private static boolean isYearValueBoundary(String json, int afterYearIdx) {
        if (afterYearIdx >= json.length()) {
            return true;
        }
        char c = json.charAt(afterYearIdx);
        return c == ',' || c == '}' || c == ']' || Character.isWhitespace(c);
    }

    private static List<String> extractDatesFromArray(String section, String arrayKey) {
        List<String> dates = new ArrayList<>();
        int keyIdx = section.indexOf("\"" + arrayKey + "\"");
        if (keyIdx < 0) {
            return dates;
        }
        int bracketStart = section.indexOf('[', keyIdx);
        int bracketEnd = section.indexOf(']', bracketStart);
        if (bracketStart < 0 || bracketEnd < 0) {
            return dates;
        }
        String arrayBody = section.substring(bracketStart + 1, bracketEnd);
        int pos = 0;
        while (pos < arrayBody.length()) {
            int q1 = arrayBody.indexOf('"', pos);
            if (q1 < 0) {
                break;
            }
            int q2 = arrayBody.indexOf('"', q1 + 1);
            if (q2 < 0) {
                break;
            }
            dates.add(arrayBody.substring(q1 + 1, q2));
            pos = q2 + 1;
        }
        return dates;
    }

    private static int parseJsonIntAfterColon(String json, int fromIdx) {
        int colon = json.indexOf(':', fromIdx);
        if (colon < 0) {
            return -1;
        }
        int i = colon + 1;
        while (i < json.length() && Character.isWhitespace(json.charAt(i))) {
            i++;
        }
        int start = i;
        while (i < json.length() && Character.isDigit(json.charAt(i))) {
            i++;
        }
        if (start == i) {
            return -1;
        }
        return Integer.parseInt(json.substring(start, i));
    }

    private static int findMatchingBrace(String text, int start) {
        int depth = 0;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    /** 校验 yyyy-MM-dd 格式。 */
    private static boolean isValidDate(String dateStr) {
        if (dateStr == null || dateStr.length() != 10) {
            return false;
        }
        return dateStr.charAt(4) == '-' && dateStr.charAt(7) == '-';
    }
}
