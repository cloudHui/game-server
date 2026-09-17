package com.gamer.data.excel.ui;

/**
 * Swing 列表/表格 HTML 文本高亮工具（红字标出子串匹配段）。
 */
public final class ViewHtmlHighlightUtil {

    private ViewHtmlHighlightUtil() {
    }

    /**
     * 转义 HTML 特殊字符。
     *
     * @param text
     *            原文
     * @return 转义后文本
     */
    public static String escapeHtml(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(text.length() + 16);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '&') {
                sb.append("&amp;");
            } else if (c == '<') {
                sb.append("&lt;");
            } else if (c == '>') {
                sb.append("&gt;");
            } else if (c == '\"') {
                sb.append("&quot;");
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * 生成带红字高亮匹配段的 HTML（子串包含、忽略大小写）。
     *
     * @param displayText
     *            展示文本
     * @param query
     *            检索词
     * @return 可用于 JLabel 的 HTML 文本
     */
    public static String highlightSubstringRedHtml(String displayText, String query) {
        if (displayText == null) {
            return "";
        }
        if (query == null || query.isEmpty()) {
            return escapeHtml(displayText);
        }
        String lower = displayText.toLowerCase();
        String qlower = query.toLowerCase();
        StringBuilder sb = new StringBuilder();
        sb.append("<html>");
        int from = 0;
        int idx = lower.indexOf(qlower, from);
        while (idx >= 0) {
            sb.append(escapeHtml(displayText.substring(from, idx)));
            sb.append("<font color='red'>");
            int endIdx = idx + query.length();
            if (endIdx > displayText.length()) {
                endIdx = displayText.length();
            }
            sb.append(escapeHtml(displayText.substring(idx, endIdx)));
            sb.append("</font>");
            from = endIdx;
            idx = lower.indexOf(qlower, from);
        }
        sb.append(escapeHtml(displayText.substring(from)));
        sb.append("</html>");
        return sb.toString();
    }
}
