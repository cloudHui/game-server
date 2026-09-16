package com.cloud.hub.web.controller;

import com.cloud.hub.common.annotation.Log;
import com.cloud.hub.common.annotation.RequiresAdmin;
import com.cloud.hub.common.core.domain.AjaxResult;
import com.cloud.hub.common.core.page.TableDataInfo;
import com.cloud.hub.common.enums.BusinessType;
import com.cloud.hub.framework.security.LoginUser;
import com.cloud.hub.framework.security.SecurityUtils;
import com.cloud.hub.web.dto.InviteCreateDto;
import com.cloud.hub.web.dto.InviteReactivateDto;
import com.cloud.hub.web.dto.InviteRevokeDto;
import com.cloud.hub.web.dto.ShellExecDto;
import com.cloud.hub.web.dto.UserEnableDto;
import com.cloud.hub.web.service.ReplayService;
import com.cloud.hub.web.service.ShellService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理后台 API：邀请 / 玩家 / 桌子 / 回放 / 终端（参照 RuoYi 架构重构）
 */
@RestController
@RequestMapping("/api/admin")
@RequiresAdmin
public class AdminController {
    private static final Logger logger = LoggerFactory.getLogger(AdminController.class);

    private final LobbyAdminClient lobbyAdminClient;
    private final ReplayService replayService;
    private final ShellService shellService;

    public AdminController(LobbyAdminClient lobbyAdminClient,
                           ReplayService replayService, ShellService shellService) {
        this.lobbyAdminClient = lobbyAdminClient;
        this.replayService = replayService;
        this.shellService = shellService;
    }

    @GetMapping("/invites")
    public AjaxResult list() {
        LoginUser user = SecurityUtils.getRequiredUser();
        Map<String, Object> result = lobbyAdminClient.listInvites(user.getToken());
        return toAjax(result);
    }

    @PostMapping("/invites")
    @Log(title = "邀请码管理", businessType = BusinessType.INSERT)
    public AjaxResult create(@RequestBody @Valid InviteCreateDto body) {
        LoginUser user = SecurityUtils.getRequiredUser();
        Map<String, Object> payload = new HashMap<>();
        payload.put("note", body.getNote() != null ? body.getNote() : "");
        payload.put("maxUses", body.getMaxUses());
        payload.put("expiresDays", body.getExpiresDays());
        Map<String, Object> result = lobbyAdminClient.createInvite(user.getToken(), payload);
        return toAjax(result);
    }

    @PostMapping("/invites/revoke")
    @Log(title = "邀请码管理", businessType = BusinessType.DELETE)
    public AjaxResult revoke(@RequestBody @Valid InviteRevokeDto body) {
        LoginUser user = SecurityUtils.getRequiredUser();
        Map<String, Object> payload = new HashMap<>();
        payload.put("token", body.getToken());
        Map<String, Object> result = lobbyAdminClient.revokeInvite(user.getToken(), payload);
        return toAjax(result);
    }

    @PostMapping("/invites/reactivate")
    @Log(title = "邀请码管理", businessType = BusinessType.UPDATE)
    public AjaxResult reactivate(@RequestBody @Valid InviteReactivateDto body) {
        LoginUser user = SecurityUtils.getRequiredUser();
        Map<String, Object> payload = new HashMap<>();
        payload.put("token", body.getToken());
        payload.put("expiresDays", body.getExpiresDays());
        payload.put("additionalUses", body.getAdditionalUses());
        Map<String, Object> result = lobbyAdminClient.reactivateInvite(user.getToken(), payload);
        return toAjax(result);
    }

    @GetMapping("/users")
    public AjaxResult users() {
        LoginUser user = SecurityUtils.getRequiredUser();
        Map<String, Object> result = lobbyAdminClient.listUsers(user.getToken());
        return toAjax(result);
    }

    @PostMapping("/users/enable")
    @Log(title = "用户管理", businessType = BusinessType.UPDATE)
    public AjaxResult enableUser(@RequestBody @Valid UserEnableDto body) {
        LoginUser user = SecurityUtils.getRequiredUser();
        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", body.getUserId());
        payload.put("enabled", body.getEnabled());
        Map<String, Object> result = lobbyAdminClient.enableUser(user.getToken(), payload);
        return toAjax(result);
    }

    @GetMapping("/tables")
    public AjaxResult tables() {
        LoginUser user = SecurityUtils.getRequiredUser();
        Map<String, Object> result = lobbyAdminClient.listTables(user.getToken());
        return toAjax(result);
    }

    @PostMapping("/robot-matches")
    @Log(title = "机器人对局", businessType = BusinessType.INSERT)
    public AjaxResult createRobotMatch(@RequestBody Map<String, Object> body) {
        LoginUser user = SecurityUtils.getRequiredUser();
        Map<String, Object> payload = new HashMap<>(body);
        payload.remove("sessionId");
        Map<String, Object> result = lobbyAdminClient.createRobotMatch(user.getToken(), payload);
        return toAjax(result);
    }

    @GetMapping("/replays")
    public AjaxResult replays(@RequestParam(required = false, defaultValue = "1") int page,
                              @RequestParam(required = false, defaultValue = "20") int size,
                              @RequestParam(required = false, defaultValue = "") String category,
                              @RequestParam(required = false, defaultValue = "") String gameType) {
        Map<String, Object> result = replayService.page(page, size, category, gameType);
        result.put("code", 0);
        return toAjax(result);
    }

    @GetMapping("/records")
    public TableDataInfo records(@RequestParam(required = false, defaultValue = "1") int page,
                                 @RequestParam(required = false, defaultValue = "20") int size) {
        LoginUser user = SecurityUtils.getRequiredUser();
        List<Map<String, Object>> records = lobbyAdminClient.listRecords(user.getToken(), page, size);
        long total = records != null ? records.size() : 0;
        return TableDataInfo.build(records, total, page, size);
    }

    @GetMapping("/replays/detail")
    public AjaxResult replayDetail(@RequestParam String date, @RequestParam String name) {
        return toAjax(replayService.getReplay(date, name));
    }

    @GetMapping("/replays/code")
    public AjaxResult replayByCode(@RequestParam String code) {
        int slash = code == null ? -1 : code.indexOf('/');
        if (slash <= 0 || slash == code.length() - 1) {
            return AjaxResult.error(400, "回放码格式为 日期/文件名");
        }
        return toAjax(replayService.getReplay(code.substring(0, slash), code.substring(slash + 1)));
    }

    /**
     * 管理员 Linux 终端：返回当前工作目录（默认 /home/ec2-user）。
     */
    @GetMapping("/shell")
    public AjaxResult shellCwd() {
        LoginUser user = SecurityUtils.getRequiredUser();
        String cwd = shellService.currentCwd(user.getSessionId());
        AjaxResult result = AjaxResult.success();
        result.put("cwd", cwd);
        result.put("user", "ec2-user");
        result.put("host", "server");
        return result;
    }

    /**
     * 管理员 Linux 终端：执行一行命令并返回输出 / 退出码 / 最新 cwd。
     */
    @PostMapping("/shell")
    @Log(title = "管理员终端", businessType = BusinessType.EXECUTE)
    public AjaxResult shellExec(@RequestBody @Valid ShellExecDto body) {
        LoginUser user = SecurityUtils.getRequiredUser();
        String sessionId = user.getSessionId();
        String command = body.getCommand();
        logger.info("管理员终端执行, user: {}, cmd: {}", user.getUsername(), command);
        Map<String, Object> exec = shellService.execute(sessionId, command);
        exec.put("msg", "success");
        Object exit = exec.remove("code");
        exec.put("exitCode", exit == null ? 0 : exit);
        exec.put("code", 0);
        return toAjax(exec);
    }

    private AjaxResult toAjax(Map<String, Object> result) {
        if (result == null) {
            return AjaxResult.error(502, "lobby admin 不可用");
        }
        AjaxResult ajax = new AjaxResult();
        ajax.putAll(result);
        return ajax;
    }
}
