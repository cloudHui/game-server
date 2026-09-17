package com.gamer.data.file.db;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * 基于外挂 mysql jar 的 JDBC 只读客户端。
 */
public final class JdbcClient {

    /** 单条 SQL 超时秒数 */
    private static final int QUERY_TIMEOUT_SEC = 30;

    /** 行数据：列名 → 单元格值（BLOB 为 byte[]，其余为 Object） */
    public static final class RowData {
        /** 列值 */
        private final Map<String, Object> values = new LinkedHashMap<>();

        /**
         * @param column
         *            列名
         * @param value
         *            值
         */
        public void put(String column, Object value) {
            values.put(column, value);
        }

        /**
         * @param column
         *            列名
         * @return 值
         */
        public Object get(String column) {
            return values.get(column);
        }
    }

    /** 分页查询结果 */
    public static final class QueryResult {
        /** 列名 */
        private final List<String> columns = new ArrayList<>();

        /** 行数据 */
        private final List<RowData> rows = new ArrayList<>();

        /** 满足条件的总行数 */
        private long totalCount;

        /**
         * @return 列名
         */
        public List<String> getColumns() {
            return columns;
        }

        /**
         * @return 行
         */
        public List<RowData> getRows() {
            return rows;
        }

        /**
         * @return 总行数
         */
        public long getTotalCount() {
            return totalCount;
        }

        /**
         * @param totalCount
         *            总行数
         */
        public void setTotalCount(long totalCount) {
            this.totalCount = totalCount;
        }
    }

    /**
     * 测试连接是否可用。
     *
     * @param config
     *            连接配置
     * @param mysqlLoader
     *            mysql ClassLoader
     * @throws Exception
     *             连接失败
     */
    public void testConnection(ConnectionConfig config, ClassLoader mysqlLoader) throws Exception {
        Connection conn = openConnection(config, mysqlLoader);
        conn.close();
    }

    /**
     * 执行单值查询，无行返回 null。
     */
    public Object queryScalar(ConnectionConfig config, ClassLoader mysqlLoader, String sql, Object... params)
        throws Exception {
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = openConnection(config, mysqlLoader);
            ps = prepare(conn, sql, params);
            rs = ps.executeQuery();
            return rs.next() ? rs.getObject(1) : null;
        } finally {
            closeQuietly(rs, ps, conn);
        }
    }

    /**
     * 列出当前库全部表名。
     *
     * @param config
     *            连接配置
     * @param mysqlLoader
     *            mysql ClassLoader
     * @return 表名列表
     * @throws Exception
     *             查询失败
     */
    public List<String> listTables(ConnectionConfig config, ClassLoader mysqlLoader) throws Exception {
        List<String> tables = new ArrayList<>();
        Connection conn = null;
        Statement st = null;
        ResultSet rs = null;
        try {
            conn = openConnection(config, mysqlLoader);
            st = conn.createStatement();
            st.setQueryTimeout(QUERY_TIMEOUT_SEC);
            rs = st.executeQuery("SHOW TABLES");
            while (rs.next()) {
                tables.add(rs.getString(1));
            }
        } finally {
            closeQuietly(rs, st, conn);
        }
        return tables;
    }

    /**
     * 分页查询表数据（可选 WHERE 条件）。
     *
     * @param config
     *            连接配置
     * @param mysqlLoader
     *            mysql ClassLoader
     * @param table
     *            表名
     * @param filter
     *            筛选条件，可为 null
     * @param offset
     *            偏移
     * @param limit
     *            每页条数
     * @return 查询结果（含 totalCount）
     * @throws Exception
     *             查询失败
     */
    public QueryResult queryTablePage(ConnectionConfig config, ClassLoader mysqlLoader, String table,
                                      QueryFilter filter, int offset, int limit) throws Exception {
        if (!isSafeIdentifier(table)) {
            throw new IllegalArgumentException("非法表名: " + table);
        }
        if (offset < 0 || limit <= 0) {
            throw new IllegalArgumentException("非法分页参数");
        }
        WhereBuilder.WhereClause where = WhereBuilder.build(filter);
        QueryResult result = new QueryResult();
        Connection conn = null;
        try {
            conn = openConnection(config, mysqlLoader);
            result.setTotalCount(countRows(conn, table, where));
            loadPageRows(conn, table, where, offset, limit, result);
        } finally {
            closeQuietly(conn);
        }
        return result;
    }

    /**
     * 统计满足条件的行数。
     */
    private long countRows(Connection conn, String table, WhereBuilder.WhereClause where) throws Exception {
        String sql = "SELECT COUNT(*) FROM `" + table + "`" + where.getSql();
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = prepare(conn, sql, where.getParams().toArray());
            rs = ps.executeQuery();
            return rs.next() ? rs.getLong(1) : 0;
        } finally {
            closeQuietly(rs, ps);
        }
    }

    /**
     * 读取一页数据。
     */
    private void loadPageRows(Connection conn, String table, WhereBuilder.WhereClause where, int offset, int limit,
                              QueryResult result) throws Exception {
        String sql = "SELECT * FROM `" + table + "`" + where.getSql() + " LIMIT " + offset + "," + limit;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = prepare(conn, sql, where.getParams().toArray());
            rs = ps.executeQuery();
            ResultSetMetaData meta = rs.getMetaData();
            int columnCount = meta.getColumnCount();
            for (int i = 1; i <= columnCount; i++) {
                result.columns.add(meta.getColumnLabel(i));
            }
            while (rs.next()) {
                RowData row = new RowData();
                for (int i = 1; i <= columnCount; i++) {
                    String col = result.columns.get(i - 1);
                    int type = meta.getColumnType(i);
                    row.put(col, isBinaryType(type) ? rs.getBytes(i) : rs.getObject(i));
                }
                result.rows.add(row);
            }
        } finally {
            closeQuietly(rs, ps);
        }
    }

    /**
     * 创建带超时与参数的 PreparedStatement。
     */
    private PreparedStatement prepare(Connection conn, String sql, Object[] params) throws Exception {
        PreparedStatement ps = conn.prepareStatement(sql);
        ps.setQueryTimeout(QUERY_TIMEOUT_SEC);
        if (params != null) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
        }
        return ps;
    }

    /**
     * 是否二进制列类型。
     */
    private boolean isBinaryType(int type) {
        return type == java.sql.Types.BLOB || type == java.sql.Types.BINARY || type == java.sql.Types.VARBINARY
            || type == java.sql.Types.LONGVARBINARY;
    }

    /**
     * 通过外挂 Driver 打开连接。
     */
    private Connection openConnection(ConnectionConfig config, ClassLoader mysqlLoader) throws Exception {
        Class<?> driverClass = Class.forName("com.mysql.cj.jdbc.Driver", true, mysqlLoader);
        Driver driver = (Driver) driverClass.getDeclaredConstructor().newInstance();
        Properties props = new Properties();
        props.put("user", config.getUser());
        props.put("password", config.getPassword());
        Connection conn = driver.connect(config.buildJdbcUrl(), props);
        if (conn == null) {
            throw new IllegalStateException("无法建立连接: " + config.summary());
        }
        return conn;
    }

    /**
     * 校验标识符只含安全字符。
     */
    private boolean isSafeIdentifier(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '_') {
                return false;
            }
        }
        return true;
    }

    /**
     * 关闭多个 AutoCloseable，忽略异常。
     */
    private void closeQuietly(AutoCloseable... closeables) {
        for (AutoCloseable closeable : closeables) {
            if (closeable != null) {
                try {
                    closeable.close();
                } catch (Exception ignored) {
                    // 关闭失败忽略
                }
            }
        }
    }
}
