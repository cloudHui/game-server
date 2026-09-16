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
 * 竞技场挂机探索接口（消除局部私有鉴权与异常，标准化改造）
 */
@RestController
@RequestMapping("/api/arena/journey")
@RequiresLogin
public class ArenaJourneyController {
    private final ArenaJourneyService service;

    public ArenaJourneyController(ArenaJourneyService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<?> state() throws Exception {
        return ResponseEntity.ok(service.state(SecurityUtils.getUserId()));
    }

    @PostMapping
    public ResponseEntity<?> run(@RequestBody Map<String, Object> b) throws Exception {
        int map = ((Number) b.get("map")).intValue();
        int runs = ((Number) b.get("runs")).intValue();
        return ResponseEntity.ok(service.explore(SecurityUtils.getUserId(), map, runs));
    }
}
