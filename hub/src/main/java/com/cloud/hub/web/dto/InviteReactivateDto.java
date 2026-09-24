package com.cloud.hub.web.dto;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;

/**
 * 邀请码重新激活延期请求传输对象。
 *
 * @author cloud
 */
public class InviteReactivateDto {

    /** 目标邀请码 Token */
    @NotBlank(message = "邀请码不能为空")
    private String token;

    /** 延长有效天数 */
    @Min(value = 1, message = "有效天数不能小于 1")
    private Integer expiresDays = 7;

    /** 追加最大使用次数 */
    @Min(value = 1, message = "追加使用次数不能小于 1")
    private Integer additionalUses = 1;

    /** 会话标识 */
    private String sessionId;

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public Integer getExpiresDays() {
        return expiresDays != null ? expiresDays : 7;
    }

    public void setExpiresDays(Integer expiresDays) {
        this.expiresDays = expiresDays;
    }

    public Integer getAdditionalUses() {
        return additionalUses != null ? additionalUses : 1;
    }

    public void setAdditionalUses(Integer additionalUses) {
        this.additionalUses = additionalUses;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }
}
