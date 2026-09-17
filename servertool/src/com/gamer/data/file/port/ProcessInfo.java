package com.gamer.data.file.port;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import com.gamer.data.file.job.Pipe;

/**
 * Windows 进程、服务和父进程信息。
 */
public final class ProcessInfo {

    /** 进程 PID。 */
    public int pid;
    /** 进程映像名。 */
    public String name = "?";
    /** 完整启动命令。 */
    public String command = "";
    /** 工作集（KB）。 */
    public long workingSetKb;
    /** 当前 PID 承载的服务，格式为服务名 / 显示名 / 状态。 */
    public final List<String> services = new ArrayList<>();

    /**
     * 查询指定 PID；CIM 不可用时回退 tasklist。
     *
     * @param pid
     *            PID
     * @return 进程信息
     */
    public static ProcessInfo query(int pid) {
        ProcessInfo info = new ProcessInfo();
        info.pid = pid;
        try {
            String script = script(pid);
            String encoded = Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE));
            parse(Pipe.capture("powershell.exe -NoProfile -NonInteractive -EncodedCommand " + encoded), info);
        } catch (Exception ignored) {
            // tasklist 仍可提供基础进程名。
        }
        if (!text(info.name) || "?".equals(info.name)) {
            info.name = tasklistName(pid);
        }
        return info;
    }

    /**
     * Java 显示主类/JAR，其他进程显示映像名。
     *
     * @return 程序名称
     */
    String programName() {
        if (!"java.exe".equalsIgnoreCase(name) && !"javaw.exe".equalsIgnoreCase(name)) {
            return name;
        }
        List<String> args = splitCommand(command);
        for (int i = 0; i < args.size(); i++) {
            if ("-jar".equalsIgnoreCase(args.get(i)) && i + 1 < args.size()) {
                return "Java JAR " + new File(args.get(i + 1)).getName();
            }
        }
        for (int i = 1; i < args.size(); i++) {
            String arg = args.get(i);
            if ("-cp".equalsIgnoreCase(arg) || "-classpath".equalsIgnoreCase(arg)) {
                i++;
            } else if (!arg.startsWith("-")) {
                return "Java " + arg;
            }
        }
        return name;
    }

    /**
     * svchost 或同 PID 多服务禁止强杀。
     *
     * @return 是否保护
     */
    boolean isProtectedServiceHost() {
        return "svchost.exe".equalsIgnoreCase(name) || services.size() > 1;
    }

    /**
     * 生成服务提示文本。
     *
     * @param prefix
     *            每行前缀
     * @return 服务文本
     */
    String serviceText(String prefix) {
        StringBuilder text = new StringBuilder();
        for (String service : services) {
            text.append(prefix).append(service).append('\n');
        }
        return text.toString();
    }

    private static String script(int pid) {
        String head = "$ErrorActionPreference='SilentlyContinue';$ProgressPreference='SilentlyContinue';"
            + "function E($v){if($null -eq $v){return ''};"
            + "[Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes([string]$v))};";
        String process = "$p=Get-CimInstance Win32_Process -Filter 'ProcessId=" + pid + "';"
            + "if($null -ne $p){$ws=0;$g=Get-Process -Id " + pid + ";"
            + "if($null -ne $g){$ws=[int64]($g.WorkingSet64/1KB)};"
            + "Write-Output ('P|'+(E $p.Name)+'|'+(E $p.CommandLine)+'|'+(E $ws))};";
        String services = "Get-CimInstance Win32_Service -Filter 'ProcessId=" + pid + "'|"
            + "ForEach-Object{Write-Output ('S|'+(E $_.Name)+'|'+(E $_.DisplayName)+'|'+(E $_.State))};";
        return head + process + services;
    }

    private static void parse(String output, ProcessInfo info) {
        if (!text(output)) {
            return;
        }
        for (String line : output.split("\\r?\\n")) {
            String[] field = line.trim().split("\\|", -1);
            if (field.length >= 3 && "P".equals(field[0])) {
                info.name = decode(field[1]);
                info.command = decode(field[2]);
                if (field.length >= 4) {
                    try {
                        info.workingSetKb = Long.parseLong(decode(field[3]).trim());
                    } catch (Exception ignored) {
                    }
                }
            } else if (field.length >= 4 && "S".equals(field[0])) {
                info.services.add(decode(field[1]) + " / " + decode(field[2]) + " / " + decode(field[3]));
            }
        }
    }

    private static String tasklistName(int pid) {
        try {
            String output = Pipe.capture("tasklist /FI \"PID eq " + pid + "\" /FO CSV /NH");
            for (String line : output.split("\\r?\\n")) {
                String row = line.trim();
                int end = row.indexOf('"', 1);
                if (row.startsWith("\"") && end > 1) {
                    return row.substring(1, end);
                }
            }
        } catch (IOException ignored) {
            // 调用方统一显示未知进程。
        }
        return "?";
    }

    private static List<String> splitCommand(String command) {
        List<String> args = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; command != null && i < command.length(); i++) {
            char ch = command.charAt(i);
            if (ch == '"') {
                quoted = !quoted;
            } else if (Character.isWhitespace(ch) && !quoted) {
                if (current.length() > 0) {
                    args.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(ch);
            }
        }
        if (current.length() > 0) {
            args.add(current.toString());
        }
        return args;
    }

    private static String decode(String value) {
        try {
            return text(value) ? new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8) : "";
        } catch (IllegalArgumentException e) {
            return "";
        }
    }

    private static boolean text(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
