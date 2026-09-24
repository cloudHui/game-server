package com.cloud.hub.web.controller;

import com.cloud.hub.bootstrap.ComponentState;
import com.cloud.hub.bootstrap.HubComponent;
import com.cloud.hub.bootstrap.HubLifecycle;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Hub 服务就绪状态与各组件能力暴露控制器。
 * <p>
 * 提供网关、大厅、游戏核心、Web 管理后台及一体化联机就绪情况的探测接口。
 *
 * @author cloud
 */
@RestController
public class CapabilitiesController {

    private final HubLifecycle lifecycle;

    public CapabilitiesController(HubLifecycle lifecycle) {
        this.lifecycle = lifecycle;
    }

    /**
     * 查询 Hub 整体可用性与各子组件运行状态切片。
     *
     * @return 组件状态及就绪标识映射表
     */
    @GetMapping("/api/capabilities")
    public Map<String, Object> capabilities() {
        Map<String, Object> result = new LinkedHashMap<>();
        boolean lobby = ready(HubComponent.LOBBY);
        boolean game = ready(HubComponent.GAME);
        boolean gateway = ready(HubComponent.GATEWAY);
        boolean onlineTables = lobby && game && gateway;

        result.put("ready", lifecycle.isReady());
        result.put("degraded", lifecycle.isDegraded());
        result.put("components", lifecycle.snapshot());
        result.put("web", ready(HubComponent.WEB));
        result.put("learning", ready(HubComponent.WEB));
        result.put("center", false);
        result.put("gate", gateway);
        result.put("lobby", lobby);
        result.put("game", game);
        result.put("onlineTables", onlineTables);
        result.put("miniOnline", ready(HubComponent.WEB));
        result.put("miniLocal", ready(HubComponent.WEB));
        result.put("message", onlineTables ? "全部就绪" : "一体化服务联网牌桌尚未就绪");
        return result;
    }

    private boolean ready(HubComponent component) {
        return lifecycle.state(component) == ComponentState.READY;
    }
}
