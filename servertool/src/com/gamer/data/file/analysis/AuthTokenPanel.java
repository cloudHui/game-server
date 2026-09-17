package com.gamer.data.file.analysis;

import java.awt.BorderLayout;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.DESedeKeySpec;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingWorker;

import com.gamer.data.ui.ViewUi;

/**
 * Token 分析 + 查设备。
 */
public final class AuthTokenPanel {

    private static final String KEY = "_bPGzlQ&M0pp`DpCu+Ck4Xur;Vsu^vnh#897@sOxE*TaNla~zoS98k#kvVZ.tVDWm$Tvr'BzIg!g";
    private static final long TOKEN_EXPIRE_MS = 300000L;
    private static final SimpleDateFormat SDF = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private AuthTokenPanel() {
    }

    public static JPanel create() {
        JTabbedPane tabs = ViewUi.tabs();
        tabs.addTab("Token分析", createTokenTab());
        tabs.addTab("查设备", createDeviceTab());
        JPanel root = new JPanel(new BorderLayout());
        ViewUi.page(root);
        root.add(tabs, BorderLayout.CENTER);
        return root;
    }

    // ---------- Token 分析 ----------

    private static JPanel createTokenTab() {
        JTextArea input = textArea(6, true);
        JTextArea output = textArea(14, false);

        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setOpaque(false);
        panel.add(ViewUi.row(ViewUi.click("分析", () -> output.setText(analyze(input.getText()))),
            ViewUi.click("清空", () -> {
                input.setText("");
                output.setText("");
            })), BorderLayout.NORTH);
        panel.add(ViewUi.splitV(ViewUi.card("日志 / Token 输入", ViewUi.scroll(input)),
            ViewUi.card("分析结果", ViewUi.scroll(output)), 0.35), BorderLayout.CENTER);
        return panel;
    }

    private static String analyze(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return "输入为空";
        }
        String recvLine = findLine(raw);
        if (recvLine == null) {
            return formatTokenOnly(decodeParts(raw.trim()));
        }
        Map<String, String> req = parseRecvLine(recvLine);
        String token = req.get("token");
        if (token == null || token.isEmpty()) {
            return "未找到 token";
        }
        String[] parts = decodeParts(token);
        if (parts == null) {
            return "token 解密失败";
        }
        return formatLogAnalysis(req, parts, findAuthFailTime(raw));
    }

    private static String findLine(String raw) {
        int idx = raw.indexOf("C2S_GAME_AUTH[RECV]");
        if (idx < 0) {
            return null;
        }
        int end = raw.indexOf('\n', idx);
        return end < 0 ? raw.substring(idx) : raw.substring(idx, end);
    }

    private static Map<String, String> parseRecvLine(String line) {
        Map<String, String> map = new LinkedHashMap<>();
        int start = line.indexOf(">>>");
        if (start < 0) {
            return map;
        }
        String body = line.substring(start + 3).trim();
        int tokenIdx = body.lastIndexOf(". token=");
        if (tokenIdx >= 0) {
            map.put("token", body.substring(tokenIdx + 8).trim());
            body = body.substring(0, tokenIdx);
        }
        for (String pair : body.split(", ")) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                map.put(pair.substring(0, eq).trim(), pair.substring(eq + 1).trim());
            }
        }
        return map;
    }

    private static long findAuthFailTime(String raw) {
        for (String line : raw.split("\n")) {
            if (line.contains("invalid token") || line.contains("C2S_GAME_AUTH[FAIL]")) {
                if (line.length() >= 23) {
                    try {
                        return SDF.parse(line.substring(0, 23)).getTime();
                    } catch (Exception ignored) {
                        // next
                    }
                }
            }
        }
        return System.currentTimeMillis();
    }

    private static String formatLogAnalysis(Map<String, String> req, String[] parts, long checkTime) {
        StringBuilder sb = new StringBuilder();
        sb.append("【请求参数】\n");
        append(sb, "platform", req.get("platform"));
        append(sb, "platformChannel", req.get("platformChannel"));
        append(sb, "version", req.get("version"));
        append(sb, "deviceCode", req.get("deviceCode"));
        append(sb, "uid", req.get("uid"));
        append(sb, "serverId", req.get("serverId"));

        sb.append("\n【Token 内容】\n");
        appendTokenFields(sb, parts, checkTime);

        sb.append("\n【对比结果】\n");
        boolean ok = compare(sb, "version", req.get("version"), parts[1]);
        ok = compare(sb, "platform", req.get("platform"), parts[2]) && ok;
        ok = compare(sb, "channel", req.get("platformChannel"), parts[3]) && ok;
        ok = compare(sb, "deviceCode", req.get("deviceCode"), parts[4]) && ok;
        ok = compare(sb, "uid", req.get("uid"), parts[5]) && ok;

        long age = checkTime - Long.parseLong(parts[0]);
        boolean expired = age >= TOKEN_EXPIRE_MS;
        sb.append(expired ? "✗ " : "✓ ");
        sb.append("有效期: 签发后 ").append(age / 1000).append("s，");
        sb.append(expired ? "已过期(>300s)" : "未过期").append('\n');
        if (expired) {
            ok = false;
        }

        sb.append("\n【结论】\n");
        if (ok) {
            sb.append("token 校验通过，若仍失败请检查 serverId 或其他逻辑");
        } else {
            sb.append("认证失败：");
            if (expired) {
                sb.append("token 已过期；");
            }
            if (!eq(req.get("version"), parts[1])) {
                sb.append("version 不一致；");
            }
            if (!eq(req.get("platform"), parts[2])) {
                sb.append("platform 不一致；");
            }
            if (!eq(req.get("platformChannel"), parts[3])) {
                sb.append("channel 不一致；");
            }
            if (!eq(req.get("deviceCode"), parts[4])) {
                sb.append("deviceCode 不一致；");
            }
            if (!eq(req.get("uid"), parts[5])) {
                sb.append("uid 不一致；");
            }
        }
        return sb.toString().trim();
    }

    private static String formatTokenOnly(String[] parts) {
        if (parts == null) {
            return "解密失败，请检查 token 是否完整";
        }
        StringBuilder sb = new StringBuilder("【Token 内容】\n");
        appendTokenFields(sb, parts, System.currentTimeMillis());
        return sb.toString().trim();
    }

    private static void appendTokenFields(StringBuilder sb, String[] parts, long checkTime) {
        long tokenTime = Long.parseLong(parts[0]);
        long age = checkTime - tokenTime;
        sb.append("time: ").append(SDF.format(new Date(tokenTime)));
        sb.append(" (").append(age / 1000).append("s, ");
        sb.append(age >= TOKEN_EXPIRE_MS ? "已过期" : "有效").append(")\n");
        append(sb, "version", parts[1]);
        append(sb, "platform", parts[2]);
        append(sb, "channel", parts[3]);
        append(sb, "deviceCode", parts[4]);
        append(sb, "uid", parts[5]);
        append(sb, "authType", parts[6]);
        append(sb, "bind", parts[7]);
        append(sb, "openid", parts[8]);
        append(sb, "openname", parts[9]);
        append(sb, "unionid", parts[10]);
    }

    private static void append(StringBuilder sb, String key, String value) {
        sb.append(key).append(": ").append(value == null ? "" : value).append('\n');
    }

    private static boolean compare(StringBuilder sb, String name, String reqVal, String tokenVal) {
        boolean match = eq(reqVal, tokenVal);
        sb.append(match ? "✓ " : "✗ ").append(name).append(": ");
        if (match) {
            sb.append(reqVal);
        } else {
            sb.append("请求=").append(reqVal).append(" token=").append(tokenVal);
        }
        sb.append('\n');
        return match;
    }

    private static boolean eq(String a, String b) {
        if (a == null) {
            return b == null || b.isEmpty();
        }
        return a.equals(b);
    }

    private static String[] decodeParts(String token) {
        String plain = decrypt3Des(token.trim());
        if (plain.isEmpty()) {
            return null;
        }
        String[] parts = plain.split("\\^", 11);
        return parts.length == 11 ? parts : null;
    }

    private static String decrypt3Des(String src) {
        try {
            String base64 = URLDecoder.decode(src, "UTF-8");
            byte[] encrypted = Base64.getDecoder().decode(base64);
            byte[] md5 = MessageDigest.getInstance("MD5").digest(KEY.getBytes("GBK"));
            byte[] desKey = new byte[24];
            System.arraycopy(md5, 0, desKey, 0, Math.min(md5.length, 24));
            SecretKey secretKey = SecretKeyFactory.getInstance("DESede").generateSecret(new DESedeKeySpec(desKey));
            Cipher cipher = Cipher.getInstance("DESede");
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_16LE);
        } catch (Exception e) {
            return "";
        }
    }

    // ---------- 查设备 ----------

    private static JPanel createDeviceTab() {
        JTextField roleIdField = new JTextField(18);
        ViewUi.compactField(roleIdField);
        JTextArea output = textArea(16, false);
        JButton query = ViewUi.style(new JButton("查设备"));
        query.addActionListener(e -> {
            String raw = roleIdField.getText().trim();
            if (raw.isEmpty()) {
                output.setText("请输入 roleId");
                return;
            }
            final long roleId;
            try {
                roleId = Long.parseLong(raw);
            } catch (NumberFormatException ex) {
                output.setText("roleId 必须是数字");
                return;
            }
            query.setEnabled(false);
            output.setText("查询中...");
            new SwingWorker<String, Void>() {
                @Override
                protected String doInBackground() {
                    return DeviceLookup.query(roleId);
                }

                @Override
                protected void done() {
                    try {
                        output.setText(get());
                    } catch (Exception ex) {
                        output.setText("查询异常: " + ex.getMessage());
                    }
                    query.setEnabled(true);
                }
            }.execute();
        });
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setOpaque(false);
        panel.add(ViewUi.row(ViewUi.label("roleId"), roleIdField, query, ViewUi.click("清空", () -> {
            roleIdField.setText("");
            output.setText("");
        })), BorderLayout.NORTH);
        panel.add(ViewUi.card("查询结果", ViewUi.scroll(output)), BorderLayout.CENTER);
        return panel;
    }

    private static JTextArea textArea(int rows, boolean editable) {
        JTextArea area = new JTextArea(rows, 60);
        ViewUi.log(area);
        area.setEditable(editable);
        ViewUi.enableTextCopy(area);
        return area;
    }
}
