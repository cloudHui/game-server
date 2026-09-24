package com.cloud.hub.lobby.db;

/**
 * 注册邀请码持久化实体。
 * <p>
 * 对应 SQLite 数据库中的 {@code invite} 表，维护管理员生成的注册邀请码凭证、备注、
 * 有效期截止时间、最大允许注册次数与已消耗次数。
 * </p>
 *
 * @author cloud
 */
public class InviteEntity {

    /** 邀请码记录自增主键 */
    private long id;
    /** 邀请码 Token 密文字符串 */
    private String token;
    /** 备注说明（例如所属渠道、测试批次） */
    private String note;
    /** 创建人账号名 */
    private String createdBy;
    /** 创建时间戳（毫秒） */
    private long createdAt;
    /** 过期时间戳（毫秒），为 null 或 0 表示永不过期 */
    private Long expiresAt;
    /** 最大允许使用次数上限 */
    private int maxUses;
    /** 当前已使用次数统计 */
    private int usedCount;
    /** 是否启用（true=启用，false=已作废） */
    private boolean enabled;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public Long getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Long expiresAt) {
        this.expiresAt = expiresAt;
    }

    public int getMaxUses() {
        return maxUses;
    }

    public void setMaxUses(int maxUses) {
        this.maxUses = maxUses;
    }

    public int getUsedCount() {
        return usedCount;
    }

    public void setUsedCount(int usedCount) {
        this.usedCount = usedCount;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 判断当前邀请码在当前时刻是否仍然处于可用状态。
     *
     * @return true 表示可用
     */
    public boolean isValidNow() {
        if (!enabled) {
            return false;
        }
        if (expiresAt != null && expiresAt > 0 && System.currentTimeMillis() > expiresAt) {
            return false;
        }
        return usedCount < maxUses;
    }
}

