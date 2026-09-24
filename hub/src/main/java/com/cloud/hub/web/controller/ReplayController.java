package com.cloud.hub.web.controller;

import com.cloud.hub.common.annotation.RequiresLogin;
import com.cloud.hub.common.core.domain.AjaxResult;
import com.cloud.hub.framework.security.SecurityUtils;
import com.cloud.hub.web.service.ReplayService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 普通玩家战局回放查看控制器。
 * <p>
 * 遵循数据隐私安全，仅允许并返回当前登录玩家本人参与的对局回放及回放码解析。
 *
 * @author cloud
 */
@RestController
@RequestMapping("/api/replays")
@RequiresLogin
public class ReplayController {

    private final ReplayService replayService;

    public ReplayController(ReplayService replayService) {
        this.replayService = replayService;
    }

    /**
     * 分页查询当前玩家参与的全部对局回放。
     *
     * @param page 分页页码
     * @param size 每页大小
     * @return 个人回放分页列表
     */
    @GetMapping
    public AjaxResult list(@RequestParam(defaultValue = "1") int page,
                           @RequestParam(defaultValue = "20") int size) {
        int userId = SecurityUtils.getUserId();
        Map<String, Object> result = replayService.pageForUser(userId, page, size);
        result.put("code", 0);
        AjaxResult ajax = new AjaxResult();
        ajax.putAll(result);
        return ajax;
    }

    /**
     * 根据回放提取码查询单局回放详情（受用户鉴权约束）。
     *
     * @param code 回放提取码 (如 2026-09-24/game_12345.json)
     * @return 回放记录详情或错误提示
     */
    @GetMapping("/code")
    public AjaxResult byCode(@RequestParam String code) {
        int userId = SecurityUtils.getUserId();
        int slash = code == null ? -1 : code.indexOf('/');
        if (slash <= 0 || slash == code.length() - 1) {
            return AjaxResult.error(400, "回放码格式为 日期/文件名");
        }
        Map<String, Object> result = replayService.getReplayForUser(userId, code.substring(0, slash), code.substring(slash + 1));
        AjaxResult ajax = new AjaxResult();
        if (result != null) {
            ajax.putAll(result);
        }
        return ajax;
    }
}
