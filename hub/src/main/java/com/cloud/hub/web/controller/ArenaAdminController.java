package com.cloud.hub.web.controller;

import com.cloud.hub.common.annotation.Log;
import com.cloud.hub.common.annotation.RequiresAdmin;
import com.cloud.hub.common.core.domain.AjaxResult;
import com.cloud.hub.common.enums.BusinessType;
import com.cloud.hub.web.arena.ArenaAdminService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 竞技场后台管理接口（参照 RuoYi 规范重构）
 */
@RestController
@RequestMapping("/api/admin/arena")
@RequiresAdmin
public class ArenaAdminController {
    private final ArenaAdminService service;

    public ArenaAdminController(ArenaAdminService service) {
        this.service = service;
    }

    @GetMapping("/players")
    public AjaxResult players() throws Exception {
        return AjaxResult.of("players", service.players());
    }

    @GetMapping("/state")
    public AjaxResult state(@RequestParam long userId) throws Exception {
        return AjaxResult.of("state", service.detail(userId));
    }

    @PostMapping("/resource")
    @Log(title = "竞技场资源调整", businessType = BusinessType.UPDATE)
    public AjaxResult resource(@RequestBody Map<String, Object> b) throws Exception {
        return AjaxResult.of("state", service.adjust(num(b.get("userId")), str(b.get("resource")), num(b.get("delta"))));
    }

    @PostMapping("/hero")
    @Log(title = "竞技场英雄调整", businessType = BusinessType.UPDATE)
    public AjaxResult hero(@RequestBody Map<String, Object> b) throws Exception {
        return AjaxResult.of("state", service.hero(
                num(b.get("userId")),
                str(b.get("heroId")),
                integer(b.get("rank")),
                integer(b.get("stars")),
                integer(b.get("skill")),
                integer(b.get("shards"))
        ));
    }

    @PostMapping("/item")
    @Log(title = "竞技场道具调整", businessType = BusinessType.UPDATE)
    public AjaxResult item(@RequestBody Map<String, Object> b) throws Exception {
        return AjaxResult.of("state", service.adjustItem(num(b.get("userId")), str(b.get("itemId")), integer(b.get("delta"))));
    }

    @PostMapping("/progress")
    @Log(title = "竞技场进度调整", businessType = BusinessType.UPDATE)
    public AjaxResult progress(@RequestBody Map<String, Object> b) throws Exception {
        return AjaxResult.of("state", service.progress(
                num(b.get("userId")),
                integer(b.get("dungeonCleared")),
                integer(b.get("dungeonAttempts")),
                integer(b.get("formationLevel")),
                integer(b.get("grottoLevel"))
        ));
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static long num(Object o) {
        return o instanceof Number ? ((Number) o).longValue() : Long.parseLong(str(o));
    }

    private static int integer(Object o) {
        return o instanceof Number ? ((Number) o).intValue() : Integer.parseInt(str(o));
    }
}
