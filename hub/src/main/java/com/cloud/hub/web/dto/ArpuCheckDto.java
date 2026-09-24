package com.cloud.hub.web.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;

/**
 * ARPU 查询请求数据传输对象。
 *
 * @author cloud
 */
public class ArpuCheckDto {

    /** 目标查询手机号 */
    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "1[3-9]\\d{9}", message = "请输入 11 位有效手机号")
    private String phoneNo;

    /** 可选的会话标识 */
    private String sessionId;

    public String getPhoneNo() {
        return phoneNo;
    }

    public void setPhoneNo(String phoneNo) {
        this.phoneNo = phoneNo;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }
}
