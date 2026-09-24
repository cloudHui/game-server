package com.cloud.hub.web.learning.service;

import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.util.regex.Pattern;

/**
 * 客户端真实 IP 解析工具类。
 * <p>
 * 严密处理多级反向代理（如 Nginx、CDN）头部伪造，优先信任可信反代写入的 X-Real-IP 与 X-Forwarded-For 尾节点。
 *
 * @author cloud
 */
public final class ClientIp {

    private static final Pattern IPV4 = Pattern.compile(
            "^(?:(?:25[0-5]|2[0-4]\\d|[01]?\\d?\\d)\\.){3}(?:25[0-5]|2[0-4]\\d|[01]?\\d?\\d)$");

    private ClientIp() {
    }

    /**
     * 从当前请求线程上下文中获取客户端 IP。
     *
     * @return 客户端 IP 地址，如无上下文返回 "-"
     */
    public static String current() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) {
                return "-";
            }
            return from(attrs.getRequest());
        } catch (Exception ignored) {
            return "-";
        }
    }

    /**
     * 从 HttpServletRequest 提取真实客户端 IP。
     *
     * @param request HTTP 请求
     * @return 标准化 IP 字符串
     */
    public static String from(HttpServletRequest request) {
        if (request == null) {
            return "-";
        }
        String remote = normalize(request.getRemoteAddr());

        // 仅当直连来自本机反代或公网 IP 时才采信转发代理头
        boolean trustForward = isLoopback(remote) || isPublicIp(remote);
        if (trustForward) {
            String realIp = normalize(header(request, "X-Real-IP"));
            if (isValidIp(realIp)) {
                return realIp;
            }

            String forwarded = header(request, "X-Forwarded-For");
            if (forwarded != null) {
                String[] parts = forwarded.split(",");
                for (int i = parts.length - 1; i >= 0; i--) {
                    String ip = normalize(parts[i]);
                    if (isValidIp(ip)) {
                        return ip;
                    }
                }
            }
        }
        return remote == null || remote.isEmpty() ? "-" : remote;
    }

    static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String ip = value.trim();
        if (ip.isEmpty()) {
            return null;
        }
        if (ip.regionMatches(true, 0, "::ffff:", 0, 7)) {
            ip = ip.substring(7);
        }
        return ip;
    }

    static boolean isLoopback(String ip) {
        if (ip == null || ip.isEmpty()) {
            return false;
        }
        return "127.0.0.1".equals(ip) || "0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip)
                || "localhost".equalsIgnoreCase(ip);
    }

    static boolean isPublicIp(String ip) {
        return isValidIp(ip) && !isLoopback(ip);
    }

    static boolean isValidIp(String ip) {
        if (ip == null || ip.isEmpty() || "-".equals(ip)) {
            return false;
        }
        if (IPV4.matcher(ip).matches()) {
            return true;
        }
        return ip.indexOf(':') >= 0 && ip.length() <= 45 && ip.chars().allMatch(c ->
                (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F') || c == ':' || c == '.');
    }

    private static String header(HttpServletRequest request, String name) {
        String value = request.getHeader(name);
        return value == null ? null : value.trim();
    }
}
