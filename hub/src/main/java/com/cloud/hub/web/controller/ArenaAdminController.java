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
 * 剑气除魔/竞技场管理员控制器。
 * <p>
 * 提供玩家存档总览、详细修仙境界查询、以及灵石/道具/英雄资质/镇魔塔进度的 GM 调控接口。
 *
 * @author cloud
 */
@RestController
@RequestMapping("/api/admin/arena")
@RequiresAdmin
public class ArenaAdminController {

    private final ArenaAdminService service;

    public ArenaAdminController(ArenaAdminService service) {
        this.service = service;
    }

    /**
     * 查询所有参与过竞技场的玩家总览名录。
     *
     * @return 玩家基础状态列表
     */
    @GetMapping("/players")
    public AjaxResult players() throws Exception {
        return AjaxResult.of("players", service.players());
    }

    /**
     * 获取指定玩家的完整修仙存档详情。
     *
     * @param userId 目标玩家 ID
     * @return 玩家修行详细数据
     */
    @GetMapping("/state")
    public AjaxResult state(@RequestParam long userId) throws Exception {
        return AjaxResult.of("state", service.detail(userId));
    }

    /**
     * GM 调整玩家基础货币与修仙资源（如灵石、仙缘等）。
     *
     * @param b 请求体参数
     * @return 更新后的全量状态
     */
    @PostMapping("/resource")
    @Log(title = "竞技场资源调整", businessType = BusinessType.UPDATE)
    public AjaxResult resource(@RequestBody Map<String, Object> b) throws Exception {
        return AjaxResult.of("state", service.adjust(num(b.get("userId")), str(b.get("resource")), num(b.get("delta"))));
    }

    /**
     * GM 调整玩家英雄属性、星级、道法等级及碎片。
     *
     * @param b 请求体参数
     * @return 更新后的全量状态
     */
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

    /**
     * GM 调整或增减玩家背包装备道具数量。
     *
     * @param b 请求体参数
     * @return 更新后的全量状态
     */
    @PostMapping("/item")
    @Log(title = "竞技场道具调整", businessType = BusinessType.UPDATE)
    public AjaxResult item(@RequestBody Map<String, Object> b) throws Exception {
        return AjaxResult.of("state", service.adjustItem(num(b.get("userId")), str(b.get("itemId")), integer(b.get("delta"))));
    }

    /**
     * GM 调整玩家镇魔塔已通关层数与洞天福地等级。
     *
     * @param b 请求体参数
     * @return 更新后的全量状态
     */
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
