package com.cloud.hub.bootstrap;

/**
 * 可被 {@link HubLifecycle} 纳管的受控组件生命周期接口。
 *
 * @author cloud
 * @version 1.0
 * @since 1.0
 */
public interface ManagedComponent {

    /**
     * 获取当前组件的所属类型。
     *
     * @return 组件枚举
     */
    HubComponent component();

    /**
     * 启动组件并建立相关资源连接。
     *
     * @throws Exception 启动失败时抛出异常并触发安全回滚
     */
    void start() throws Exception;

    /**
     * 停止组件并优雅释放相关资源。
     *
     * @throws Exception 停止异常
     */
    void stop() throws Exception;
}
