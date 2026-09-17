package com.gamer.data.map.level;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * 为 Level JSON 原文（UTF-8 字节）建立标量字段的字节区间索引，并支持按区间局部替换、在 {@code children} 数组内插入对象， 使未修改区域字节与原文一致。
 */
public final class LevelJsonByteSpans {

    /**
     * 根对象下 {@code "nodes"} 顶层数组的 {@link ChildrenMeta} 键（与 {@link #pathKey} 的节点路径不同）。
     */
    public static final String ROOT_NODES_ARRAY_META_KEY = "__root_nodes__";

    /** 标量字段：路径键 → 值 token 的 [start,end) 字节区间（不含外层空白） */
    private final Map<String, Span> scalarSpans;

    /** 父节点路径键（与 pathKey 一致）→ 其 {@code children} 数组元数据 */
    private final Map<String, ChildrenMeta> childrenMeta;

    private LevelJsonByteSpans(Map<String, Span> scalarSpans, Map<String, ChildrenMeta> childrenMeta) {
        this.scalarSpans = scalarSpans;
        this.childrenMeta = childrenMeta;
    }

    /**
     * 扁平节点路径（自根 {@code nodes} 起）转为与索引一致的路径键，例如 [0,1] → {@code nodes/0/children/1}。
     */
    public static String pathKey(List<Integer> pathSegments) {
        if (pathSegments == null || pathSegments.isEmpty()) {
            return "nodes";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("nodes/").append(pathSegments.get(0).intValue());
        for (int i = 1; i < pathSegments.size(); i++) {
            sb.append("/children/").append(pathSegments.get(i).intValue());
        }
        return sb.toString();
    }

    /**
     * 从 UTF-8 原文建立索引（要求根为对象且含 {@code nodes} 数组，与现有关卡文件一致）。
     */
    public static LevelJsonByteSpans build(byte[] utf8, ObjectMapper mapper) throws IOException {
        Map<String, Span> scalars = new HashMap<>();
        Map<String, ChildrenMeta> ch = new HashMap<>();
        JsonFactory factory = mapper.getFactory();
        try (JsonParser p = factory.createParser(utf8)) {
            JsonToken t = p.nextToken();
            if (t != JsonToken.START_OBJECT) {
                throw new IOException("Level JSON 根须为对象");
            }
            while (p.nextToken() != JsonToken.END_OBJECT) {
                String field = p.getCurrentName();
                JsonToken vt = p.nextToken();
                if ("nodes".equals(field) && vt == JsonToken.START_ARRAY) {
                    parseNodesArray(p, utf8, scalars, ch);
                } else if (vt == JsonToken.START_OBJECT) {
                    p.skipChildren();
                } else if (vt == JsonToken.START_ARRAY) {
                    p.skipChildren();
                }
            }
        }
        return new LevelJsonByteSpans(scalars, ch);
    }

    private static boolean isScalarToken(JsonToken t) {
        if (t == null) {
            return false;
        }
        return t == JsonToken.VALUE_STRING || t == JsonToken.VALUE_NUMBER_INT || t == JsonToken.VALUE_NUMBER_FLOAT
            || t == JsonToken.VALUE_TRUE || t == JsonToken.VALUE_FALSE || t == JsonToken.VALUE_NULL;
    }

    private static void parseNodesArray(JsonParser p, byte[] utf8, Map<String, Span> scalars,
        Map<String, ChildrenMeta> chMeta) throws IOException {
        JsonLocation locOpen = p.getTokenLocation();
        long openBracket = locOpen.getByteOffset();
        if (openBracket < 0) {
            throw new IOException("JsonParser 未提供字节偏移");
        }
        int openB = (int)openBracket;
        List<Span> childObjSpans = new ArrayList<>();
        int index = 0;
        while (true) {
            JsonToken t = p.nextToken();
            if (t == JsonToken.END_ARRAY) {
                break;
            }
            if (t == JsonToken.START_OBJECT) {
                JsonLocation objStartLoc = p.getTokenLocation();
                int objStart = skipJsonWhitespaceBytes(utf8, (int)objStartLoc.getByteOffset());
                ArrayList<Integer> path = new ArrayList<>();
                path.add(index);
                parseNodeObject(p, utf8, scalars, chMeta, path);
                int objEnd = objectSpanEndExclusive(utf8, objStart);
                childObjSpans.add(new Span(objStart, objEnd));
                index++;
            } else {
                p.skipChildren();
            }
        }
        JsonLocation locClose = p.getCurrentLocation();
        int closeB = (int)locClose.getByteOffset();
        chMeta.put(ROOT_NODES_ARRAY_META_KEY, new ChildrenMeta(openB, closeB, childObjSpans));
    }

    private static void parseNodeObject(JsonParser p, byte[] utf8, Map<String, Span> scalars,
        Map<String, ChildrenMeta> chMeta, List<Integer> path) throws IOException {
        String pathBase = pathKey(path);
        while (p.nextToken() != JsonToken.END_OBJECT) {
            String name = p.getCurrentName();
            JsonToken vt = p.nextToken();
            if ("children".equals(name) && vt == JsonToken.START_ARRAY) {
                parseChildrenArray(p, utf8, scalars, chMeta, path, pathBase);
            } else if ("Position".equals(name) && vt == JsonToken.START_OBJECT) {
                while (p.nextToken() != JsonToken.END_OBJECT) {
                    String axis = p.getCurrentName();
                    p.nextToken();
                    recordScalarToken(p, utf8, scalars, pathBase + "/Position/" + axis);
                }
            } else if (vt == JsonToken.START_OBJECT) {
                p.skipChildren();
            } else if (vt == JsonToken.START_ARRAY) {
                p.skipChildren();
            } else if (isScalarToken(vt)) {
                recordScalarToken(p, utf8, scalars, pathBase + "/" + name);
            } else {
                p.skipChildren();
            }
        }
    }

    private static void parseChildrenArray(JsonParser p, byte[] utf8, Map<String, Span> scalars,
        Map<String, ChildrenMeta> chMeta, List<Integer> parentPath, String parentPathBase) throws IOException {
        JsonLocation locOpen = p.getTokenLocation();
        long openBracket = locOpen.getByteOffset();
        if (openBracket < 0) {
            throw new IOException("JsonParser 未提供字节偏移，请使用 UTF-8 字节数组解析");
        }
        int openB = (int)openBracket;
        List<Span> childObjSpans = new ArrayList<>();
        int childIdx = 0;
        while (true) {
            JsonToken t = p.nextToken();
            if (t == JsonToken.END_ARRAY) {
                break;
            }
            if (t == JsonToken.START_OBJECT) {
                JsonLocation objStartLoc = p.getTokenLocation();
                int objStart = skipJsonWhitespaceBytes(utf8, (int)objStartLoc.getByteOffset());
                List<Integer> cpath = new ArrayList<>(parentPath);
                cpath.add(childIdx);
                parseNodeObject(p, utf8, scalars, chMeta, cpath);
                // 勿用 getCurrentLocation()+1：END_OBJECT 的字节位置在部分 Jackson/场景下与真实 '}' 不一致，
                // 会导致子对象 span 偏短，插入兄弟节点时撕裂后续字段（如 "DataId"" ）。
                int objEnd = objectSpanEndExclusive(utf8, objStart);
                childObjSpans.add(new Span(objStart, objEnd));
                childIdx++;
            } else {
                p.skipChildren();
            }
        }
        JsonLocation locClose = p.getCurrentLocation();
        int closeB = (int)locClose.getByteOffset();
        ChildrenMeta meta = new ChildrenMeta(openB, closeB, childObjSpans);
        chMeta.put(parentPathBase, meta);
    }

    /**
     * {@code utf8[openBrace]} 必须为 {@code '{'}. 返回与之配对的 {@code '}'} 之后的首字节下标（exclusive），字符串内花括号不计入深度。
     */
    private static int objectSpanEndExclusive(byte[] utf8, int openBrace) throws IOException {
        if (openBrace < 0 || openBrace >= utf8.length || utf8[openBrace] != '{') {
            throw new IOException("对象 span 起点非法，偏移=" + openBrace);
        }
        int depth = 0;
        boolean inStr = false;
        int i = openBrace;
        int n = utf8.length;
        while (i < n) {
            byte b = utf8[i];
            if (inStr) {
                if (b == '"') {
                    inStr = false;
                    i++;
                    continue;
                }
                if (b == '\\' && i + 1 < n) {
                    byte nxt = utf8[i + 1];
                    if (nxt == 'u' && i + 5 < n) {
                        i += 6;
                        continue;
                    }
                    i += 2;
                    continue;
                }
                i++;
                continue;
            }
            if (b == '"') {
                inStr = true;
                i++;
                continue;
            }
            if (b == '{') {
                depth++;
                i++;
                continue;
            }
            if (b == '}') {
                depth--;
                if (depth == 0) {
                    return i + 1;
                }
                i++;
                continue;
            }
            i++;
        }
        throw new IOException("未找到与 '{' 配对的 '}'，偏移=" + openBrace);
    }

    private static void recordScalarToken(JsonParser p, byte[] utf8, Map<String, Span> scalars, String key)
        throws IOException {
        JsonLocation loc = p.getTokenLocation();
        long startL = loc.getByteOffset();
        if (startL < 0) {
            throw new IOException("JsonParser 未提供字节偏移");
        }
        int start = (int)startL;
        int end;
        JsonToken tok = p.getCurrentToken();
        if (tok == JsonToken.VALUE_STRING) {
            end = stringTokenEndExclusive(utf8, start);
        } else if (tok == JsonToken.VALUE_NUMBER_INT || tok == JsonToken.VALUE_NUMBER_FLOAT) {
            // getTokenLocation 偶发落在 ':'、空白甚至字段名引号前；numberTokenEndExclusive 会得到 end==start，保存时插入会破坏 "DataId" 等键名
            int ns = resolveNumberLiteralStart(utf8, start);
            end = numberTokenEndExclusive(utf8, ns);
            start = ns;
            if (end <= start) {
                throw new IOException("数字字段 span 非法（长度为 0）: " + key + " 起始偏移=" + startL);
            }
        } else if (tok == JsonToken.VALUE_TRUE) {
            int ks = resolveKeywordLiteralStart(utf8, start, "true");
            end = literalKeywordEnd(ks, "true");
            start = ks;
        } else if (tok == JsonToken.VALUE_FALSE) {
            int ks = resolveKeywordLiteralStart(utf8, start, "false");
            end = literalKeywordEnd(ks, "false");
            start = ks;
        } else if (tok == JsonToken.VALUE_NULL) {
            int ks = resolveKeywordLiteralStart(utf8, start, "null");
            end = literalKeywordEnd(ks, "null");
            start = ks;
        } else {
            int ns = resolveNumberLiteralStart(utf8, start);
            end = numberTokenEndExclusive(utf8, ns);
            start = ns;
        }
        scalars.put(key, new Span(start, end));
    }

    private static boolean isJsonWhitespaceByte(byte b) {
        return b == ' ' || b == '\t' || b == '\n' || b == '\r';
    }

    private static int skipJsonWhitespaceBytes(byte[] utf8, int from) {
        int i = from;
        while (i < utf8.length && isJsonWhitespaceByte(utf8[i])) {
            i++;
        }
        return i;
    }

    /**
     * 在 {@code reported} 起若干字节内定位数字字面量第一个字节（{@code -} 或数字）。
     */
    private static int resolveNumberLiteralStart(byte[] utf8, int reported) {
        int n = utf8.length;
        int hi = Math.min(n, reported + 96);
        for (int j = reported; j < hi; j++) {
            byte b = utf8[j];
            if (b == '-' || (b >= '0' && b <= '9')) {
                return j;
            }
        }
        return reported;
    }

    private static int resolveKeywordLiteralStart(byte[] utf8, int reported, String keyword) {
        byte[] kw = keyword.getBytes(StandardCharsets.US_ASCII);
        int n = utf8.length;
        int hi = Math.min(n, reported + 32);
        for (int j = reported; j <= hi - kw.length; j++) {
            boolean match = true;
            for (int k = 0; k < kw.length; k++) {
                if (utf8[j + k] != kw[k]) {
                    match = false;
                    break;
                }
            }
            if (match) {
                return j;
            }
        }
        return reported;
    }

    /**
     * 从 {@code start} 扫描 JSON 数字字面量结束位置（exclusive），与 UTF-8 原文一致。
     */
    private static int numberTokenEndExclusive(byte[] data, int start) {
        int i = start;
        int n = data.length;
        if (i >= n) {
            return i;
        }
        if (data[i] == '-') {
            i++;
        }
        while (i < n && data[i] >= '0' && data[i] <= '9') {
            i++;
        }
        if (i < n && data[i] == '.') {
            do {
                i++;
            } while (i < n && data[i] >= '0' && data[i] <= '9');
        }
        if (i < n && (data[i] == 'e' || data[i] == 'E')) {
            i++;
            if (i < n && (data[i] == '+' || data[i] == '-')) {
                i++;
            }
            while (i < n && data[i] >= '0' && data[i] <= '9') {
                i++;
            }
        }
        return i;
    }

    private static int literalKeywordEnd(int start, String keyword) {
        return start + keyword.length();
    }

    /**
     * {@code quoteStart} 指向 JSON 字符串的左双引号，返回该字符串 token 结束位置（右引号之后）的字节下标。
     */
    static int stringTokenEndExclusive(byte[] data, int quoteStart) {
        int i = quoteStart + 1;
        final int n = data.length;
        while (i < n) {
            byte b = data[i];
            if (b == '"') {
                return i + 1;
            }
            if (b == '\\' && i + 1 < n) {
                byte nxt = data[i + 1];
                if (nxt == 'u' && i + 5 < n) {
                    i += 6;
                    continue;
                }
                i += 2;
                continue;
            }
            i++;
        }
        return n;
    }

    public Span getScalarSpan(String fullPathKey) {
        return scalarSpans.get(fullPathKey);
    }

    public ChildrenMeta getChildrenMeta(String parentPathKey) {
        return childrenMeta.get(parentPathKey);
    }

    /**
     * 对原文应用多段替换（区间不得重叠）；按起始偏移从大到小应用，避免下标漂移。
     */
    public static byte[] applyReplacements(byte[] source, List<Replacement> reps) throws IOException {
        if (reps == null || reps.isEmpty()) {
            return source;
        }
        reps.sort((a, b) -> {
            if (a.start != b.start) {
                return b.start - a.start;
            }
            return b.end - a.end;
        });
        byte[] cur = source;
        for (Replacement rp : reps) {
            if (rp.start < 0 || rp.end > cur.length || rp.start > rp.end) {
                throw new IOException("非法替换区间: [" + rp.start + "," + rp.end + ")");
            }
            int oldLen = rp.end - rp.start;
            int newLen = rp.bytes == null ? 0 : rp.bytes.length;
            byte[] next = new byte[cur.length - oldLen + newLen];
            System.arraycopy(cur, 0, next, 0, rp.start);
            if (newLen > 0) {
                System.arraycopy(rp.bytes, 0, next, rp.start, newLen);
            }
            System.arraycopy(cur, rp.end, next, rp.start + newLen, cur.length - rp.end);
            cur = next;
        }
        return cur;
    }

    /**
     * 根据旧 token 字节与整型新值生成替换内容（保持是否带小数点等风格）。
     */
    public static byte[] formatIntToken(int value, byte[] utf8, Span span) {
        String orig = new String(utf8, span.start, span.end - span.start, StandardCharsets.UTF_8);
        if (orig.indexOf('.') >= 0 || orig.indexOf('e') >= 0 || orig.indexOf('E') >= 0) {
            return String.format(Locale.US, "%.1f", (double)value).getBytes(StandardCharsets.UTF_8);
        }
        return Integer.toString(value).getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Position 轴等：旧 token 多为 {@code 1781.0} 形式。
     */
    public static byte[] formatAxisToken(int value, byte[] utf8, Span span) {
        String orig = new String(utf8, span.start, span.end - span.start, StandardCharsets.UTF_8);
        if (orig.indexOf('.') >= 0) {
            return String.format(Locale.US, "%.1f", (double)value).getBytes(StandardCharsets.UTF_8);
        }
        return Integer.toString(value).getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 检测文件换行：优先 CRLF，否则 LF。
     */
    public static String detectEol(byte[] utf8) {
        for (int i = 0; i < utf8.length - 1; i++) {
            if (utf8[i] == '\r' && utf8[i + 1] == '\n') {
                return "\r\n";
            }
        }
        return "\n";
    }

    /**
     * 将新节点对象格式化为与现有文件相近的多行块（字段顺序与导出一致）。
     *
     * @param braceLinePrefix
     *            含 {@code '{'} 所在行行首的空格（与兄弟节点左花括号对齐） @param fieldLinePrefix 对象内字段行行首缩进（一般为
     *            {@code braceLinePrefix + "    "}）
     */
    public static byte[] formatNewNodeObject(ObjectNode node, String eol, String braceLinePrefix,
        String fieldLinePrefix, ObjectMapper mapper) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append(braceLinePrefix).append('{').append(eol);
        appendFieldLine(sb, fieldLinePrefix, eol, "BelongId", node.path("BelongId").asInt());
        appendFieldLine(sb, fieldLinePrefix, eol, "Type", node.path("Type").asInt());
        appendFieldLine(sb, fieldLinePrefix, eol, "TextId", node.path("TextId").asInt());
        String posIndent = fieldLinePrefix + "    ";
        sb.append(fieldLinePrefix).append("\"Position\": {").append(eol);
        JsonNode pos = node.get("Position");
        double px = pos != null && pos.has("x") ? pos.get("x").asDouble() : 0.0;
        double py = pos != null && pos.has("y") ? pos.get("y").asDouble() : 0.0;
        double pz = pos != null && pos.has("z") ? pos.get("z").asDouble() : 0.0;
        appendPosition(sb, posIndent, eol, px, py, pz, posIndent);// 输出 Position 对象
        appendXyzObjectBlock(sb, fieldLinePrefix, eol, "Scale", node.get("Scale"), true);
        appendXyzObjectBlock(sb, fieldLinePrefix, eol, "Rotation", node.get("Rotation"), false);
        appendFieldLine(sb, fieldLinePrefix, eol, "DataId", node.path("DataId").asInt());
        appendFieldLine(sb, fieldLinePrefix, eol, "IndexId", node.path("IndexId").asInt());
        appendFieldLine(sb, fieldLinePrefix, eol, "NextDataId", node.path("NextDataId").asInt());
        String param = node.path("Param").asText("");
        sb.append(fieldLinePrefix).append("\"Param\": ").append(mapper.writeValueAsString(param)).append(',')
            .append(eol);
        sb.append(fieldLinePrefix).append("\"children\": []").append(eol);
        sb.append(braceLinePrefix).append('}');
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String formatDoubleJson(double d) {
        if (d == (double)((long)d)) {
            return String.format(Locale.US, "%.1f", d);
        }
        return String.format(Locale.US, "%s", d);
    }

    private static void appendFieldLine(StringBuilder sb, String indent, String eol, String name, int v) {
        sb.append(indent).append('"').append(name).append("\": ").append(v);
        sb.append(',');
        sb.append(eol);
    }

    /**
     * 输出与 Position 相同风格的多行 {@code x,y,z} 对象（最后一行无尾随逗号）；Scale 缺省 1.0，Rotation 缺省 0.0。
     */
    private static void appendXyzObjectBlock(StringBuilder sb, String fieldLinePrefix, String eol, String objectName,
        JsonNode obj, boolean defaultOnes) {
        double x;
        double y;
        double z;
        if (obj != null && obj.isObject()) {
            x = obj.path("x").asDouble(defaultOnes ? 1.0 : 0.0);
            y = obj.path("y").asDouble(defaultOnes ? 1.0 : 0.0);
            z = obj.path("z").asDouble(defaultOnes ? 1.0 : 0.0);
        } else if (defaultOnes) {
            x = 1.0;
            y = 1.0;
            z = 1.0;
        } else {
            x = 0.0;
            y = 0.0;
            z = 0.0;
        }
        String inner = fieldLinePrefix + "    ";
        sb.append(fieldLinePrefix).append('"').append(objectName).append("\": {").append(eol);
        appendPosition(sb, fieldLinePrefix, eol, x, y, z, inner);
    }

    /**
     * 输出 Position 对象
     * 
     * @param sb
     *            字符串构建器
     * @param fieldLinePrefix
     *            字段行行首缩进
     * @param eol
     *            换行符
     * @param x
     *            x 坐标
     * @param y
     *            y 坐标
     * @param z
     *            z 坐标
     * @param inner
     *            对象内字段行行首缩进
     */
    private static void appendPosition(StringBuilder sb, String fieldLinePrefix, String eol, double x, double y,
        double z, String inner) {
        sb.append(inner).append("\"x\": ").append(formatDoubleJson(x)).append(',').append(eol);
        sb.append(inner).append("\"y\": ").append(formatDoubleJson(y)).append(',').append(eol);
        sb.append(inner).append("\"z\": ").append(formatDoubleJson(z)).append(eol);
        sb.append(fieldLinePrefix).append("},").append(eol);
    }

    /**
     * 对象左花括号所在行的行首空格序列（与兄弟节点对齐）。
     */
    public static String braceLineIndentOfObject(byte[] utf8, int objectBraceByteOffset) {
        int lineStart = 0;
        for (int i = objectBraceByteOffset - 1; i >= 0; i--) {
            if (utf8[i] == '\n') {
                lineStart = i + 1;
                break;
            }
        }
        int j = lineStart;
        while (j < utf8.length && utf8[j] == ' ') {
            j++;
        }
        return new String(utf8, lineStart, j - lineStart, StandardCharsets.UTF_8);
    }

    /**
     * 在父节点的 {@code children} 数组中插入格式化后的新对象字节块。
     * <p>
     * 子对象 span 为 [start,end)：含左花括号至右花括号之后第一个字节（即紧邻其后的逗号、空白或 {@code ]} 之前）。
     */
    public static byte[] insertChildObject(byte[] utf8, ChildrenMeta meta, int insertIndex, byte[] newObjectUtf8,
        String eol) throws IOException {
        List<Span> spans = meta.childObjectSpans;
        int count = spans.size();
        if (insertIndex < 0) {
            insertIndex = 0;
        }
        if (insertIndex > count) {
            insertIndex = count;
        }
        if (count == 0) {
            int innerStart = meta.openBracket + 1;
            int innerEnd = meta.closeBracket;
            while (innerStart < innerEnd && isWs(utf8[innerStart])) {
                innerStart++;
            }
            while (innerEnd > innerStart && isWs(utf8[innerEnd - 1])) {
                innerEnd--;
            }
            if (innerStart >= innerEnd) {
                String gap = eol + lineIndentBefore(utf8, meta.openBracket);
                byte[] mid =
                    concat(gap.getBytes(StandardCharsets.UTF_8), newObjectUtf8, gap.getBytes(StandardCharsets.UTF_8));
                return applyReplacements(utf8, Collections.singletonList(new Replacement(innerStart, innerEnd, mid)));
            }
        }
        if (insertIndex == count) {
            if (count == 0) {
                throw new IOException("内部错误：空 children 应已处理");
            }
            Span last = spans.get(count - 1);
            int tailFrom = last.end;
            int tailTo = meta.closeBracket;
            if (tailFrom > tailTo) {
                throw new IOException("children 数组 span 异常");
            }
            byte[] tail = new byte[tailTo - tailFrom];
            System.arraycopy(utf8, tailFrom, tail, 0, tail.length);
            byte[] ins = concat(("," + eol).getBytes(StandardCharsets.UTF_8), newObjectUtf8, tail);
            return applyReplacements(utf8, Collections.singletonList(new Replacement(tailFrom, tailTo, ins)));
        }
        if (insertIndex == 0) {
            Span first = spans.get(0);
            int innerStart = meta.openBracket + 1;
            int headTo = first.start;
            if (innerStart > headTo) {
                throw new IOException("children 数组首元素 span 异常");
            }
            byte[] head = new byte[headTo - innerStart];
            System.arraycopy(utf8, innerStart, head, 0, head.length);
            String bp = braceLineIndentOfObject(utf8, first.start);
            byte[] ins = concat(head, newObjectUtf8, ("," + eol + bp).getBytes(StandardCharsets.UTF_8));
            return applyReplacements(utf8, Collections.singletonList(new Replacement(innerStart, headTo, ins)));
        }
        Span prev = spans.get(insertIndex - 1);
        Span next = spans.get(insertIndex);
        int gapFrom = prev.end;
        int gapTo = next.start;
        if (gapFrom > gapTo) {
            throw new IOException("children 元素间区间异常");
        }
        byte[] gap = new byte[gapTo - gapFrom];
        System.arraycopy(utf8, gapFrom, gap, 0, gap.length);
        byte[] ins = concat(gap, newObjectUtf8, gap);
        return applyReplacements(utf8, Collections.singletonList(new Replacement(gapFrom, gapTo, ins)));
    }

    /**
     * 从 {@code nodes} 或 {@code children} 数组中删除下标为 {@code removeIndex} 的对象字节块（含与兄弟之间的分隔），保持其余字节不变。
     */
    public static byte[] removeChildObject(byte[] utf8, ChildrenMeta meta, int removeIndex) throws IOException {
        List<Span> spans = meta.childObjectSpans;
        int count = spans.size();
        if (removeIndex < 0 || removeIndex >= count) {
            throw new IOException("removeIndex 越界: " + removeIndex + " / " + count);
        }
        if (count == 1) {
            Span only = spans.get(0);
            return applyReplacements(utf8,
                Collections.singletonList(new Replacement(only.start, only.end, new byte[0])));
        }
        if (removeIndex == 0) {
            Span first = spans.get(0);
            Span second = spans.get(1);
            if (first.start > second.start) {
                throw new IOException("子节点 span 顺序异常");
            }
            return applyReplacements(utf8,
                Collections.singletonList(new Replacement(first.start, second.start, new byte[0])));
        }
        if (removeIndex == count - 1) {
            Span prev = spans.get(count - 2);
            Span last = spans.get(count - 1);
            return applyReplacements(utf8, Collections.singletonList(new Replacement(prev.end, last.end, new byte[0])));
        }
        Span before = spans.get(removeIndex - 1);
        Span cur = spans.get(removeIndex);
        Span after = spans.get(removeIndex + 1);
        int gapLen = after.start - cur.end;
        if (gapLen < 0) {
            throw new IOException("删除区间计算异常");
        }
        byte[] gapAfter = new byte[gapLen];
        System.arraycopy(utf8, cur.end, gapAfter, 0, gapLen);
        return applyReplacements(utf8, Collections.singletonList(new Replacement(before.end, after.start, gapAfter)));
    }

    /**
     * {@code offset} 所在行中，行首连续空格（用于推断 children 块缩进）。
     */
    public static String lineIndentBefore(byte[] utf8, int offset) {
        int lineStart = 0;
        for (int i = offset - 1; i >= 0; i--) {
            if (utf8[i] == '\n') {
                lineStart = i + 1;
                break;
            }
        }
        int j = lineStart;
        while (j < offset && utf8[j] == ' ') {
            j++;
        }
        return new String(utf8, lineStart, j - lineStart, StandardCharsets.UTF_8);
    }

    private static byte[] concat(byte[]... parts) {
        int len = 0;
        for (byte[] part : parts) {
            if (part != null) {
                len += part.length;
            }
        }
        byte[] out = new byte[len];
        int p = 0;
        for (byte[] part : parts) {
            if (part != null && part.length > 0) {
                System.arraycopy(part, 0, out, p, part.length);
                p += part.length;
            }
        }
        return out;
    }

    private static boolean isWs(byte b) {
        return b == ' ' || b == '\t' || b == '\r' || b == '\n';
    }

    public static final class Span {
        public final int start;
        public final int end;

        public Span(int start, int end) {
            this.start = start;
            this.end = end;
        }
    }

    public static final class ChildrenMeta {
        public final int openBracket;
        public final int closeBracket;
        public final List<Span> childObjectSpans;

        ChildrenMeta(int openBracket, int closeBracket, List<Span> childObjectSpans) {
            this.openBracket = openBracket;
            this.closeBracket = closeBracket;
            this.childObjectSpans = childObjectSpans;
        }
    }

    public static final class Replacement {
        public final int start;
        public final int end;
        public final byte[] bytes;

        public Replacement(int start, int end, byte[] bytes) {
            this.start = start;
            this.end = end;
            this.bytes = bytes;
        }
    }
}
