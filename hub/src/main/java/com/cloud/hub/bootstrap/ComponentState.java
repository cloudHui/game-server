package com.cloud.hub.bootstrap;

/**
 * Hub 一体化服务各组件的生命周期状态枚举。
 *
 * @author cloud
 * @version 1.0
 * @since 1.0
 */
public enum ComponentState {
    /** 尚未初始化或启动 */
    NOT_STARTED,
    /** 正在启动中 */
    STARTING,
    /** 正常运行就绪 */
    READY,
    /** 降级运行中（部分非核心功能受限） */
    DEGRADED,
    /** 正在停止中 */
    STOPPING,
    /** 启动或运行失败 */
    FAILED,
    /** 已完全停止并释放资源 */
    STOPPED
}
