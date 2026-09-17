package com.gamer.data.mpcserver.core;

/** 一个固定数据库目标。 */
public final class DbDefaults {
    private final String host;
    private final int port;
    private final String user;
    private final String password;
    private final String database;

    public DbDefaults(String host, Integer port, String user, String password, String database) {
        this.host = empty(host) ? "127.0.0.1" : host.trim();
        this.port = port == null ? 3306 : port;
        this.user = user == null ? "" : user;
        this.password = password == null ? "" : password;
        this.database = empty(database) ? null : database.trim();
    }

    public String host() {
        return host;
    }

    public int port() {
        return port;
    }

    public String user() {
        return user;
    }

    public String password() {
        return password;
    }

    public String database() {
        return database;
    }

    public String buildJdbcUrl(String database) {
        String name = empty(database) ? this.database : database.trim();
        if (empty(name)) {
            name = "kingdom_game";
        }
        return "jdbc:mysql://" + host + ":" + port + "/" + name
            + "?useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC&allowPublicKeyRetrieval=true&useSSL=false";
    }

    private static boolean empty(String value) {
        return value == null || value.trim().isEmpty();
    }
}
