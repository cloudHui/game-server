package com.gamer.data.file.config;

/**
 * 本机 Redis 连接（WindowsTools 与 MCP 共用）。改配置改本类后重新编译。
 */
public final class RedisConfig {

    /** Redis 主机。 */
    public static final String HOST = "127.0.0.1";

    /** Redis 端口。 */
    public static final int PORT = 6379;

    /** Redis 用户名；ACL 未启用时为空。 */
    public static final String USER = "";

    /** Redis 密码。 */
    public static final String PASSWORD = "9ijn0okm";

    private RedisConfig() {
    }
}
