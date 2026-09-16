package com.cloud.hub.web.dto;

import javax.validation.constraints.NotBlank;

/**
 * 终端命令执行请求体（对齐 RuoYi 接口入参规范）
 */
public class ShellExecDto {
    @NotBlank(message = "执行命令不能为空")
    private String command;

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
