package com.cloud.hub.bootstrap;

/**
 * Hub 一体化服务内管理的子系统组件枚举（按依赖顺序排列）。
 *
 * @author cloud
 * @version 1.0
 * @since 1.0
 */
public enum HubComponent {
    /** 基础存储引擎（SQLite/文件持久化） */
    STORAGE,
    /** 大厅与房间管理系统 */
    LOBBY,
    /** 游戏对局与玩法状态机系统 */
    GAME,
    /** 通信网关桥接（长连接协议适配） */
    GATEWAY,
    /** Web 管理端与 HTTP 交互接口 */
    WEB
}
