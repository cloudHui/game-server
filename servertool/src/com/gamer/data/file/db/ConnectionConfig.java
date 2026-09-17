package com.gamer.data.file.db;

/**
 * 数据库连接配置（别名 + JDBC 连接参数）。
 */
public class ConnectionConfig {

    /** 连接别名，列表展示用 */
    private String alias;

    /** 数据库主机 */
    private String host;

    /** 端口 */
    private int port;

    /** 用户名 */
    private String user;

    /** 密码 */
    private String password;

    /** 默认库名 */
    private String database;

    /**
     * @return 连接别名
     */
    public String getAlias() {
        return alias;
    }

    /**
     * @param alias
     *            连接别名
     */
    public void setAlias(String alias) {
        this.alias = alias;
    }

    /**
     * @return 主机地址
     */
    public String getHost() {
        return host;
    }

    /**
     * @param host
     *            主机地址
     */
    public void setHost(String host) {
        this.host = host;
    }

    /**
     * @return 端口
     */
    public int getPort() {
        return port;
    }

    /**
     * @param port
     *            端口
     */
    public void setPort(int port) {
        this.port = port;
    }

    /**
     * @return 用户名
     */
    public String getUser() {
        return user;
    }

    /**
     * @param user
     *            用户名
     */
    public void setUser(String user) {
        this.user = user;
    }

    /**
     * @return 密码
     */
    public String getPassword() {
        return password;
    }

    /**
     * @param password
     *            密码
     */
    public void setPassword(String password) {
        this.password = password;
    }

    /**
     * @return 库名
     */
    public String getDatabase() {
        return database;
    }

    /**
     * @param database
     *            库名
     */
    public void setDatabase(String database) {
        this.database = database;
    }

    /**
     * 构建 JDBC URL。
     *
     * @return JDBC URL
     */
    public String buildJdbcUrl() {
        return "jdbc:mysql://" + host + ":" + port + "/" + database
            + "?useUnicode=true&characterEncoding=UTF-8&useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true"
            + "&connectTimeout=5000&socketTimeout=30000";
    }

    /**
     * 列表展示摘要。
     *
     * @return host:port / database
     */
    public String summary() {
        return host + ":" + port + " / " + database;
    }
}
