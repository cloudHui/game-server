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
 * 竞技场装备与道具配方合成控制器。
 *
 * @author cloud
 */
@RestController
@RequestMapping("/api/arena/library")
@RequiresLogin
public class ArenaCraftController {

    private final ArenaCraftService service;

    public ArenaCraftController(ArenaCraftService service) {
        this.service = service;
    }

    /**
     * 查询玩家已解锁的合成配方及材料库存视图。
     *
     * @return 配方和库存列表
     */
    @GetMapping
    public ResponseEntity<?> view() throws Exception {
        return ResponseEntity.ok(service.view(SecurityUtils.getUserId()));
    }

    /**
     * 执行指定配方物品合成操作。
     *
     * @param b 包含配方 ID 的参数
     * @return 合成结果
     */
    @PostMapping("/craft")
    public ResponseEntity<?> craft(@RequestBody Map<String, String> b) throws Exception {
        return ResponseEntity.ok(service.craft(SecurityUtils.getUserId(), b.get("recipeId")));
    }
}
