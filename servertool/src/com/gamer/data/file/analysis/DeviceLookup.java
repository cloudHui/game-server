package com.gamer.data.file.analysis;

import com.gamer.data.file.config.DbConfig;
import com.gamer.data.file.db.ConnectionConfig;
import com.gamer.data.file.db.JarLoader;
import com.gamer.data.file.db.JdbcClient;

/**
 * roleId → uid → deviceCode 查询。
 * <p>
 * 连接与 mysql jar 直接读 {@link DbConfig}。
 */
final class DeviceLookup {

    private static final String[] UID_ALIASES = { "测试服game", "本地game" };
    private static final String ALIAS_TEST_PASS = "测试pass";

    private static final JdbcClient JDBC = new JdbcClient();
    private static final JarLoader JAR = new JarLoader();

    private DeviceLookup() {
    }

    /**
     * 先测服/本地 game 取 uid，再测服 pass 取 deviceCode。
     */
    static String query(long roleId) {
        StringBuilder out = new StringBuilder();
        out.append("roleId: ").append(roleId).append('\n');

        ClassLoader mysql;
        try {
            mysql = JAR.getMysqlLoader(DbConfig.MYSQL_JAR);
        } catch (Exception e) {
            return out.append("mysql jar 加载失败: ").append(msg(e)).toString();
        }

        Long uid = queryUid(mysql, roleId, out);
        if (uid == null) {
            return out.append("未能取得 uid").toString();
        }

        ConnectionConfig pass = DbConfig.findConnection(ALIAS_TEST_PASS);
        if (pass == null) {
            return out.append("缺少连接: ").append(ALIAS_TEST_PASS).append("（请改 DbConfig.CONNECTIONS）")
                .toString();
        }
        try {
            Object device = JDBC.queryScalar(pass, mysql, "SELECT deviceCode FROM passport WHERE uid=?", uid);
            if (device == null) {
                out.append(ALIAS_TEST_PASS).append(": 无该 uid 的 deviceCode");
            } else {
                out.append("deviceCode: ").append(device);
            }
        } catch (Exception e) {
            out.append(ALIAS_TEST_PASS).append(" 查询失败: ").append(msg(e));
        }
        return out.toString();
    }

    /**
     * 按别名顺序查 uid；无数据与查询异常分别记录。
     */
    private static Long queryUid(ClassLoader mysql, long roleId, StringBuilder out) {
        for (String alias : UID_ALIASES) {
            ConnectionConfig cfg = DbConfig.findConnection(alias);
            if (cfg == null) {
                out.append("缺少连接: ").append(alias).append("（请改 DbConfig.CONNECTIONS）\n");
                continue;
            }
            try {
                Long uid = toLong(JDBC.queryScalar(cfg, mysql, "SELECT uid FROM role WHERE roleId=?", roleId));
                if (uid != null) {
                    out.append("uid: ").append(uid).append('\n');
                    out.append("来源: ").append(alias).append('\n');
                    return uid;
                }
                out.append(alias).append(": 无该 roleId\n");
            } catch (Exception e) {
                out.append(alias).append(" 查询失败: ").append(msg(e)).append('\n');
            }
        }
        return null;
    }

    private static Long toLong(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number) {
            return ((Number) v).longValue();
        }
        return Long.parseLong(v.toString());
    }

    private static String msg(Exception e) {
        String m = e.getMessage();
        return m == null || m.isEmpty() ? e.getClass().getSimpleName() : m;
    }
}
