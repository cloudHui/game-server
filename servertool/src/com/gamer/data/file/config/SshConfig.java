package com.gamer.data.file.config;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * 测服 SSH / 拉日志的默认常量。
 * <p>
 * 永久改本类。密码与密钥二选一：{@link #KEY} 非空走密钥，否则走 {@link #PASS}。 不要把凭据写进 skill 或页签。
 */
public final class SshConfig {

    /** SSH 地址。 */
    public static final String HOST = "10.3.115.63";
    /** SSH 端口。 */
    public static final int PORT = 22;
    /** SSH 用户。 */
    public static final String USER = "root";
    /** SSH 密码；密钥非空时忽略。 */
    public static final String PASS = "Fast#10.3S$";
    /** 私钥路径；空则用密码。 */
    public static final String KEY = "";

    /** 区服根。 */
    public static final String ROOT = "/data/server/90001";
    /** 远端工作目录；结果写在此目录的 log/，脚本从本机 stdin 传入。 */
    public static final String DIR = ROOT + "/gameserver";
    /** 压缩结果固定名，远端与本机下载同名覆盖。 */
    public static final String ARCHIVE = "log.tar.gz";
    /** jar 内脚本路径，与 compile 拷入 classes 的位置一致。 */
    private static final String COPY_LOG_RESOURCE = "/com/gamer/data/file/copylog.sh";

    /**
     * 本次进程连接参数；默认来自上面常量，界面可改，不落盘。
     */
    public static final class Conn {
        /** 地址。 */
        public final String host;
        /** 端口。 */
        public final int port;
        /** 用户。 */
        public final String user;
        /** 密码；密钥非空时忽略。 */
        public final String pass;
        /** 私钥路径。 */
        public final String key;

        /**
         * @param host
         *            地址
         * @param port
         *            端口
         * @param user
         *            用户
         * @param pass
         *            密码
         * @param key
         *            密钥路径
         */
        public Conn(String host, int port, String user, String pass, String key) {
            this.host = host;
            this.port = port;
            this.user = user;
            this.pass = pass == null ? "" : pass;
            this.key = key == null ? "" : key;
        }

        /**
         * 密钥路径非空则走密钥。
         *
         * @return 用密钥
         */
        public boolean useKey() {
            return !key.trim().isEmpty();
        }

        /**
         * 日志用，不含密码。
         *
         * @return 简述
         */
        public String brief() {
            return user + "@" + host + ":" + port + (useKey() ? " 密钥" : " 密码");
        }

        /**
         * 类上常量的默认连接。
         *
         * @return 默认
         */
        public static Conn defaults() {
            return new Conn(HOST, PORT, USER, PASS, KEY);
        }
    }

    private SshConfig() {}

    /**
     * 本机脚本经 stdin 在远端执行的命令。不引用测服 copylog.sh。
     *
     * @param server
     *            game 或 all
     * @param compress
     *            是否压缩
     * @param from
     *            起始日期，可空
     * @param to
     *            截止日期，可空
     * @param condition
     *            检索条件，可空
     * @return 远端命令
     */
    public static String copyCmd(String server, boolean compress, String from, String to, String condition) {
        if (!"game".equals(server) && !"all".equals(server)) {
            throw new IllegalArgumentException("服务只支持 game/all");
        }
        StringBuilder args = new StringBuilder();
        String[][] options = {{"from", from}, {"to", to}, {"condition", condition}};
        for (String[] option : options) {
            if (!option[1].isEmpty()) {
                args.append(" --").append(option[0]).append(" ").append(shellQuote(option[1]));
            }
        }
        if ("all".equals(server)) {
            args.append(" --all");
        }
        if (compress) {
            args.append(" --compress");
        }
        return "COPYLOG_OUTPUT=" + shellQuote(DIR + "/log") + " sh -s --" + args;
    }

    /**
     * 本机 copylog.sh：优先读 jar/classes 资源，没有再退回 src 工程文件。
     *
     * @return 可喂给 SSH stdin 的脚本文件；来自 jar 时为临时文件
     * @throws IOException
     *             找不到脚本
     */
    public static File localCopyLogScript() throws IOException {
        InputStream in = SshConfig.class.getResourceAsStream(COPY_LOG_RESOURCE);
        if (in != null) {
            File copy = Files.createTempFile("copylog-", ".sh").toFile();
            copy.deleteOnExit();
            try {
                Files.copy(in, copy.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } finally {
                try {
                    in.close();
                } catch (IOException ignored) {
                }
            }
            return copy;
        }
        File file = new File(PathConfig.SERVERTOOL_DIR, "src/com/gamer/data/file/copylog.sh");
        if (file.isFile()) {
            return file.getAbsoluteFile();
        }
        throw new IOException("找不到 copylog.sh（未打进 jar，src 也没有）");
    }

    /**
     * @param value
     *            sh 参数 @return 单引号引用，拒绝控制字符
     */
    public static String shellQuote(String value) {
        if (value.indexOf('\0') >= 0 || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("参数不能包含换行或空字符");
        }
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

    /** @return 本机 Desktop/log */
    public static File localLog() {
        return new File(System.getProperty("user.home"), "Desktop/log");
    }

    /**
     * @param remote
     *            脚本返回路径 @return 经校验的本次结果名，拒绝任意远端路径
     */
    public static String exportName(String remote) {
        String prefix = DIR + "/log/";
        if (!remote.startsWith(prefix)) {
            throw new IllegalArgumentException("脚本结果不在约定 log 目录");
        }
        String name = remote.substring(prefix.length());
        boolean archive = ARCHIVE.equals(name);
        if (!archive) {
            throw new IllegalArgumentException("脚本结果名无效");
        }
        return name;
    }
}
