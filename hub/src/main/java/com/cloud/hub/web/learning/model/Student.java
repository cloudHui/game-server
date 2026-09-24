package com.cloud.hub.web.learning.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 学习中心学生/学员账号实体。
 *
 * @author cloud
 */
public class Student {
    /** 学生唯一标识 */
    public String id;
    /** 账号名 */
    public String username;
    /** 姓名 */
    public String name;
    /** 角色权限 (USER / ADMIN) */
    public String role;
    /** 账号是否可用 */
    public boolean enabled;
    /** 特殊权限细则 */
    public List<String> permissions = new ArrayList<>();
    /** 当前就读学段 */
    public String stage;
    /** 账号创建时间 */
    public LocalDateTime createdAt;
    /** 最近活跃时间 */
    public LocalDateTime lastActiveAt;
    /** 最近登录时间 */
    public LocalDateTime lastLoginAt;
    /** 累计登录次数 */
    public long loginCount;

    public Student() {
    }

    public Student(String id, String username, String name) {
        this.id = id;
        this.username = username;
        this.name = name;
        this.role = "USER";
        this.enabled = true;
        this.stage = "幼小衔接";
        this.createdAt = LocalDateTime.now();
        this.lastActiveAt = this.createdAt;
    }
}
