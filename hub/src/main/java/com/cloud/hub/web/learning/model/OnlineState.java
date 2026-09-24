package com.cloud.hub.web.learning.model;

import java.time.LocalDateTime;

/**
 * 学习平台用户实时在线状态快照。
 *
 * @author cloud
 */
public class OnlineState {
    /** 用户标识 */
    public String userId;
    /** 账号名 */
    public String username;
    /** 姓名/昵称 */
    public String name;
    /** 当前所在前端路由页面 */
    public String page;
    /** 当前活跃学习功能模块 */
    public String feature;
    /** 客户端设备/浏览器类型 */
    public String device;
    /** 登录时间 */
    public LocalDateTime loginAt;
    /** 最近一次心跳探活时间 */
    public LocalDateTime lastSeenAt;

    public OnlineState() {
    }
}
