package com.gamer.data.file.ssh;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.gamer.data.file.cmd.Logs;
import com.gamer.data.file.cmd.TaskLogView;
import com.gamer.data.file.config.SshConfig;
import com.gamer.data.file.job.Pipe;

/**
 * OpenSSH：密钥用 -i；密码用 SSH_ASKPASS，不把密码写进命令行。
 */
public final class Ssh implements AutoCloseable {

    /** 本次连接。 */
    private final SshConfig.Conn conn;
    /** 密码登录时的临时 askpass 目录；密钥登录为 null。 */
    private final File askDir;

    /**
     * 按连接参数准备认证。
     *
     * @param conn
     *            连接
     * @throws IOException
     *             写 askpass 失败
     */
    public Ssh(SshConfig.Conn conn) throws IOException {
        this.conn = conn;
        if (conn.useKey()) {
            askDir = null;
            return;
        }
        if (conn.pass.isEmpty()) {
            throw new IOException("SSH 密码和密钥都为空");
        }
        askDir = Files.createTempDirectory("ssh-ask").toFile();
        File passFile = new File(askDir, "p.txt");
        File askCmd = new File(askDir, "ask.cmd");
        // 密码单独落文件，避免 bat echo 转义。
        Files.write(passFile.toPath(), (conn.pass + "\n").getBytes(StandardCharsets.UTF_8));
        Files.write(askCmd.toPath(), "@echo off\r\ntype \"%~dp0p.txt\"\r\n".getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 把本机 copylog.sh 经 stdin 交给远端 sh -s，不把脚本写到测服。
     *
     * @param script
     *            本机脚本
     * @param remoteCmd
     *            {@link SshConfig#copyCmd}
     * @param view
     *            页签
     * @return COPYLOG_RESULT；无命中为空串
     * @throws IOException
     *             失败
     */
    public String execScript(File script, String remoteCmd, TaskLogView view) throws IOException {
        File lf = writeLfCopy(script);
        try {
            Logs.appendLog(view, "本地脚本（jar 资源）");
            return run(sshBase(remoteCmd), view, lf);
        } finally {
            if (!lf.delete()) {
                lf.deleteOnExit();
            }
        }
    }

    /**
     * 下载一个远端结果（目录或压缩包）到本机暂存目录。
     *
     * @param remoteDir
     *            远端目录
     * @param localDir
     *            本机目录
     * @param view
     *            页签
     * @throws IOException
     *             失败
     */
    public void fetchPath(String remoteDir, File localDir, TaskLogView view) throws IOException {
        if (!localDir.exists() && !localDir.mkdirs()) {
            throw new IOException("无法创建目录: " + localDir.getAbsolutePath());
        }
        List<String> cmd = scpBase();
        // 下载单个本次产物（目录或压缩包），不再拉取远端整个历史 log 目录。
        cmd.add(target() + ":" + remoteDir);
        cmd.add(slash(localDir));
        run(cmd, view, null);
    }

    /**
     * 删临时 askpass。
     */
    @Override
    public void close() {
        deleteTree(askDir);
    }

    /**
     * ssh 参数，末尾为远端命令。
     *
     * @param remoteCmd
     *            远端命令
     * @return 参数
     */
    private List<String> sshBase(String remoteCmd) {
        List<String> cmd = new ArrayList<>();
        cmd.add("ssh");
        cmd.add("-p");
        cmd.add(String.valueOf(conn.port));
        addAuth(cmd);
        cmd.add(target());
        cmd.add(remoteCmd);
        return cmd;
    }

    /**
     * scp -r 公共参数。
     *
     * @return 参数
     */
    private List<String> scpBase() {
        List<String> cmd = new ArrayList<>();
        cmd.add("scp");
        cmd.add("-r");
        cmd.add("-P");
        cmd.add(String.valueOf(conn.port));
        addAuth(cmd);
        return cmd;
    }

    /**
     * 密钥或密码选项。
     *
     * @param cmd
     *            参数
     */
    private void addAuth(List<String> cmd) {
        o(cmd, "StrictHostKeyChecking=accept-new");
        // 老 sshd 只提供 ssh-rsa，新 OpenSSH 默认禁用，不放开会握手 255。
        o(cmd, "HostKeyAlgorithms=+ssh-rsa");
        if (conn.useKey()) {
            cmd.add("-i");
            cmd.add(conn.key.trim());
            o(cmd, "IdentitiesOnly=yes");
            o(cmd, "BatchMode=yes");
            return;
        }
        o(cmd, "PreferredAuthentications=password");
        o(cmd, "NumberOfPasswordPrompts=1");
        o(cmd, "PubkeyAuthentication=no");
    }

    /**
     * 追加 -o 选项。
     *
     * @param cmd
     *            参数
     * @param opt
     *            选项
     */
    private static void o(List<String> cmd, String opt) {
        cmd.add("-o");
        cmd.add(opt);
    }

    /**
     * 跑 ssh/scp，输出批量进页签。
     *
     * @param cmd
     *            命令
     * @param view
     *            页签
     * @param stdinFile
     *            喂给 ssh stdin 的本地文件；空则 stdin 接 NUL（密码走 ASKPASS）
     * @throws IOException
     *             非 0 或启动失败
     */
    private String run(List<String> cmd, TaskLogView view, File stdinFile) throws IOException {
        Logs.appendLog(view, "$ " + String.join(" ", cmd));
        Map<String, String> env = null;
        if (askDir != null) {
            env = new HashMap<>();
            env.put("SSH_ASKPASS", new File(askDir, "ask.cmd").getAbsolutePath());
            env.put("SSH_ASKPASS_REQUIRE", "force");
            env.put("DISPLAY", "dummy");
        }
        String[] result = {null};
        int code = Pipe.runLines(cmd, null, env, stdinFile == null, stdinFile, StandardCharsets.UTF_8, line -> {
            Logs.appendLog(view, line);
            if (line.startsWith("COPYLOG_RESULT=")) {
                result[0] = line.substring("COPYLOG_RESULT=".length());
            } else if ("COPYLOG_EMPTY=1".equals(line)) {
                result[0] = "";
            }
        });
        if (code != 0) {
            throw new IOException("退出码 " + code);
        }
        return result[0];
    }

    /** 脚本改成 LF，避免 Windows CRLF 让远端 sh -s 失败。 */
    private static File writeLfCopy(File script) throws IOException {
        String text = new String(Files.readAllBytes(script.toPath()), StandardCharsets.UTF_8).replace("\r\n", "\n")
            .replace("\r", "\n");
        File copy = Files.createTempFile("copylog-", ".sh").toFile();
        Files.write(copy.toPath(), text.getBytes(StandardCharsets.UTF_8));
        return copy;
    }

    /**
     * user@host。
     *
     * @return 目标
     */
    private String target() {
        return conn.user + "@" + conn.host;
    }

    /**
     * scp 用正斜杠。
     *
     * @param file
     *            目录
     * @return 路径
     */
    private static String slash(File file) {
        return file.getAbsolutePath().replace('\\', '/');
    }

    /**
     * 递归删临时目录。
     *
     * @param dir
     *            可空
     */
    private static void deleteTree(File dir) {
        if (dir == null || !dir.exists()) {
            return;
        }
        File[] kids = dir.listFiles();
        if (kids != null) {
            for (File kid : kids) {
                if (kid.isDirectory()) {
                    deleteTree(kid);
                } else if (!kid.delete()) {
                    kid.deleteOnExit();
                }
            }
        }
        if (!dir.delete()) {
            dir.deleteOnExit();
        }
    }
}
