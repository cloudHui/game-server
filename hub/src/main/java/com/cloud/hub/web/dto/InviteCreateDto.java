package com.cloud.hub.web.dto;

import javax.validation.constraints.Min;

/**
 * 邀请码生成创建请求传输对象。
 *
 * @author cloud
 */
public class InviteCreateDto {

    /** 备注说明信息 */
    private String note;

    /** 最大允许使用次数 */
    @Min(value = 1, message = "最大使用次数不能小于 1")
    private Integer maxUses = 1;

    /** 有效天数 */
    @Min(value = 1, message = "有效天数不能小于 1")
    private Integer expiresDays = 7;

    /** 会话标识 */
    private String sessionId;

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public Integer getMaxUses() {
        return maxUses != null ? maxUses : 1;
    }

    public void setMaxUses(Integer maxUses) {
        this.maxUses = maxUses;
    }

    public Integer getExpiresDays() {
        return expiresDays != null ? expiresDays : 7;
    }

    public void setExpiresDays(Integer expiresDays) {
        this.expiresDays = expiresDays;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }
}
