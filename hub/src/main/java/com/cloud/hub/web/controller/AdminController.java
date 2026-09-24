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
import org.springframework.web.bind.annotation.PathVariable;
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
 * 管理后台核心控制器。
 * <p>
 * 提供邀请码管理、用户启停、活跃桌子监控、战局回放检索、以及管理员远程终端等管理接口。
 *
 * @author cloud
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
                           ReplayService replayService,
                           ShellService shellService) {
        this.lobbyAdminClient = lobbyAdminClient;
        this.replayService = replayService;
        this.shellService = shellService;
    }

    /**
     * 查询系统内所有邀请码列表。
     *
     * @return 邀请码列表包装结果
     */
    @GetMapping("/invites")
    public AjaxResult list() {
        LoginUser user = SecurityUtils.getRequiredUser();
        Map<String, Object> result = lobbyAdminClient.listInvites(user.getToken());
        return toAjax(result);
    }

    /**
     * 创建新的邀请码。
     *
     * @param body 邀请码创建请求体
     * @return 响应结果
     */
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

    /**
     * 作废指定的邀请码。
     *
     * @param body 作废请求参数
     * @return 响应结果
     */
    @PostMapping("/invites/revoke")
    @Log(title = "邀请码管理", businessType = BusinessType.DELETE)
    public AjaxResult revoke(@RequestBody @Valid InviteRevokeDto body) {
        LoginUser user = SecurityUtils.getRequiredUser();
        Map<String, Object> payload = new HashMap<>();
        payload.put("token", body.getToken());
        Map<String, Object> result = lobbyAdminClient.revokeInvite(user.getToken(), payload);
        return toAjax(result);
    }

    /**
     * 重新激活或延长邀请码有效期。
     *
     * @param body 重新激活请求参数
     * @return 响应结果
     */
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

    /**
     * 分页/全量查询注册用户列表。
     *
     * @return 用户列表数据
     */
    @GetMapping("/users")
    public AjaxResult users() {
        LoginUser user = SecurityUtils.getRequiredUser();
        Map<String, Object> result = lobbyAdminClient.listUsers(user.getToken());
        return toAjax(result);
    }

    /**
     * 启用或禁用指定用户账号。
     *
     * @param body 账号状态更新参数
     * @return 响应结果
     */
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

    /**
     * 获取大厅当前存活的所有房间/桌子信息。
     *
     * @return 桌子列表
     */
    @GetMapping("/tables")
    public AjaxResult tables(@RequestParam(value = "tableId", required = false) Long tableId) {
        LoginUser user = SecurityUtils.getRequiredUser();
        Map<String, Object> result = lobbyAdminClient.listTables(user.getToken(), tableId);
        return toAjax(result);
    }

    /**
     * 查询指定桌子的上帝视角透视详情（包含状态码、各玩家手牌明牌、牌堆与底牌）。
     *
     * @param tableId 桌号
     * @return 牌桌上帝视角全景透视数据
     */
    @GetMapping("/tables/{tableId}")
    public AjaxResult tableDetail(@PathVariable("tableId") long tableId) {
        LoginUser user = SecurityUtils.getRequiredUser();
        Map<String, Object> result = lobbyAdminClient.getTableDetail(user.getToken(), tableId);
        return toAjax(result);
    }

    /**
     * 对指定麻将桌执行换牌调试命令（动态替换剩余摸牌或杠牌）。
     *
     * @param tableId 桌号
     * @param body    包含 command 命令字符串的请求体
     * @return 执行反馈结果
     */
    @PostMapping("/tables/{tableId}/mj-cheat")
    @Log(title = "牌桌换牌调试", businessType = BusinessType.UPDATE)
    public AjaxResult mjCheat(@PathVariable("tableId") long tableId, @RequestBody Map<String, Object> body) {
        LoginUser user = SecurityUtils.getRequiredUser();
        String command = body.get("command") != null ? String.valueOf(body.get("command")) : "";
        if (command.isEmpty() && body.containsKey("action")) {
            command = body.get("action") + " " + body.get("tile");
        }
        Map<String, Object> result = lobbyAdminClient.executeMjCheat(user.getToken(), tableId, command);
        return toAjax(result);
    }

    /**
     * 发起机器人对局测试。
     *
     * @param body 对局配置参数
     * @return 创建结果
     */
    @PostMapping("/robot-matches")
    @Log(title = "机器人对局", businessType = BusinessType.INSERT)
    public AjaxResult createRobotMatch(@RequestBody Map<String, Object> body) {
        LoginUser user = SecurityUtils.getRequiredUser();
        Map<String, Object> payload = new HashMap<>(body);
        payload.remove("sessionId");
        Map<String, Object> result = lobbyAdminClient.createRobotMatch(user.getToken(), payload);
        return toAjax(result);
    }

    /**
     * 分页检索全局对局回放文件列表。
     *
     * @param page 当前页码
     * @param size 分页大小
     * @param category 分类过滤
     * @param gameType 玩法类型
     * @return 分页回放列表
     */
    @GetMapping("/replays")
    public AjaxResult replays(@RequestParam(required = false, defaultValue = "1") int page,
                              @RequestParam(required = false, defaultValue = "20") int size,
                              @RequestParam(required = false, defaultValue = "") String category,
                              @RequestParam(required = false, defaultValue = "") String gameType) {
        Map<String, Object> result = replayService.page(page, size, category, gameType);
        result.put("code", 0);
        return toAjax(result);
    }

    /**
     * 分页查询游戏大厅持久化对局记录。
     *
     * @param page 页码
     * @param size 每页记录条数
     * @return 表格分页结构
     */
    @GetMapping("/records")
    public TableDataInfo records(@RequestParam(required = false, defaultValue = "1") int page,
                                 @RequestParam(required = false, defaultValue = "20") int size) {
        LoginUser user = SecurityUtils.getRequiredUser();
        List<Map<String, Object>> records = lobbyAdminClient.listRecords(user.getToken(), page, size);
        long total = records != null ? records.size() : 0;
        return TableDataInfo.build(records, total, page, size);
    }

    /**
     * 获取指定日期和文件名的回放详情。
     *
     * @param date 日期目录
     * @param name 文件名
     * @return 回放详情
     */
    @GetMapping("/replays/detail")
    public AjaxResult replayDetail(@RequestParam String date, @RequestParam String name) {
        return toAjax(replayService.getReplay(date, name));
    }

    /**
     * 根据回放码快捷解析回放详情。
     *
     * @param code 回放提取码 (日期/文件名)
     * @return 回放详情或校验错误
     */
    @GetMapping("/replays/code")
    public AjaxResult replayByCode(@RequestParam String code) {
        int slash = code == null ? -1 : code.indexOf('/');
        if (slash <= 0 || slash == code.length() - 1) {
            return AjaxResult.error(400, "回放码格式为 日期/文件名");
        }
        return toAjax(replayService.getReplay(code.substring(0, slash), code.substring(slash + 1)));
    }

    /**
     * 获取当前管理员 Linux 虚拟终端工作目录。
     *
     * @return 包含当前工作目录信息
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
     * 管理员执行单行 Shell 终端命令。
     *
     * @param body 终端执行请求体
     * @return 执行输出、返回值与最新工作路径
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

    /**
     * 查询系统实时监控与运行时度量指标（仅限管理员访问）。
     *
     * @return 包含实时在线人数、活跃桌子、累计对局数、JVM 性能等指标
     */
    @GetMapping("/metrics")
    public AjaxResult metrics() {
        return AjaxResult.success(com.cloud.hub.framework.metrics.HubMetrics.getInstance().getSystemOverview());
    }

    /**
     * 将底层 Map 结果包装为统一的 AjaxResult。
     */
    private AjaxResult toAjax(Map<String, Object> result) {
        if (result == null) {
            return AjaxResult.error(502, "lobby admin 服务不可用");
        }
        AjaxResult ajax = new AjaxResult();
        ajax.putAll(result);
        return ajax;
    }
}
