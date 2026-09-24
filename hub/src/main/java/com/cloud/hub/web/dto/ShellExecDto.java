package com.cloud.hub.web.dto;

import javax.validation.constraints.NotBlank;

/**
 * 终端 Shell 命令执行请求传输对象。
 *
 * @author cloud
 */
public class ShellExecDto {

    /** 待执行的终端命令文本 */
    @NotBlank(message = "执行命令不能为空")
    private String command;

    /** 会话标识 */
    private String sessionId;

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }
}
