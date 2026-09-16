package com.cloud.hub.web.controller;

import com.cloud.hub.common.annotation.RequiresLogin;
import com.cloud.hub.framework.security.SecurityUtils;
import com.cloud.hub.web.arena.ArenaCraftService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 竞技场配方合成接口（消除局部私有鉴权与异常，标准化改造）
 */
@RestController
@RequestMapping("/api/arena/library")
@RequiresLogin
public class ArenaCraftController {
    private final ArenaCraftService service;

    public ArenaCraftController(ArenaCraftService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<?> view() throws Exception {
        return ResponseEntity.ok(service.view(SecurityUtils.getUserId()));
    }

    @PostMapping("/craft")
    public ResponseEntity<?> craft(@RequestBody Map<String, String> b) throws Exception {
        return ResponseEntity.ok(service.craft(SecurityUtils.getUserId(), b.get("recipeId")));
    }
}
