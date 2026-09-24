package com.cloud.hub.web.controller;

import com.cloud.hub.common.annotation.RequiresLogin;
import com.cloud.hub.framework.security.SecurityUtils;
import com.cloud.hub.web.arena.ArenaJourneyService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 竞技场仙途历练与挂机探索控制器。
 *
 * @author cloud
 */
@RestController
@RequestMapping("/api/arena/journey")
@RequiresLogin
public class ArenaJourneyController {

    private final ArenaJourneyService service;

    public ArenaJourneyController(ArenaJourneyService service) {
        this.service = service;
    }

    /**
     * 查询玩家当前地图历练挂机状态与累计收益。
     *
     * @return 历练状态
     */
    @GetMapping
    public ResponseEntity<?> state() throws Exception {
        return ResponseEntity.ok(service.state(SecurityUtils.getUserId()));
    }

    /**
     * 发起指定地图的快速历练探索。
     *
     * @param b 包含地图编号 map 与历练次数 runs
     * @return 历练探索收益及掉落
     */
    @PostMapping
    public ResponseEntity<?> run(@RequestBody Map<String, Object> b) throws Exception {
        int map = ((Number) b.get("map")).intValue();
        int runs = ((Number) b.get("runs")).intValue();
        return ResponseEntity.ok(service.explore(SecurityUtils.getUserId(), map, runs));
    }
}
