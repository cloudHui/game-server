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
 * 普通玩家回放接口，只返回本人参与的回放（参照 RuoYi 规范重构）
 */
@RestController
@RequestMapping("/api/replays")
@RequiresLogin
public class ReplayController {
    private final ReplayService replayService;

    public ReplayController(ReplayService replayService) {
        this.replayService = replayService;
    }

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
