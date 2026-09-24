package com.cloud.hub.web.controller;

import com.cloud.hub.common.annotation.Log;
import com.cloud.hub.common.core.domain.AjaxResult;
import com.cloud.hub.common.enums.BusinessType;
import com.cloud.hub.web.dto.LoginDto;
import com.cloud.hub.web.dto.RegisterDto;
import com.cloud.hub.web.service.UserService;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 用户认证与鉴权核心控制器。
 * <p>
 * 提供账号密码登录、Token 免密登录、新玩家注册及会话 Cookie 植入等标准接口。
 *
 * @author cloud
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    /**
     * 玩家登录接口。
     * <p>
     * 支持两种登录模式：
     * 1. 凭已有 Token 免密验证登录；
     * 2. 凭借账号和明文密码鉴权登录。
     *
     * @param request 登录请求体
     * @return 成功返回用户信息并植入 sessionId Cookie，失败返回对应错误码
     */
    @PostMapping("/login")
    public ResponseEntity<AjaxResult> login(@RequestBody LoginDto request) {
        String token = request.getToken();
        if (token != null && !token.trim().isEmpty()) {
            UserService.UserInfo userInfo = userService.validateToken(token.trim());
            if (userInfo == null) {
                return ResponseEntity.ok(AjaxResult.error(401, "Token无效或已过期"));
            }
            return withSessionCookie(userInfo);
        }

        String username = request.getUsername();
        String password = request.getPassword();
        if (username == null || username.trim().isEmpty() || password == null || password.isEmpty()) {
            return ResponseEntity.badRequest().body(AjaxResult.error(400, "用户名和密码不能为空"));
        }

        UserService.UserInfo userInfo = userService.login(username.trim(), password);
        if (userInfo == null) {
            return ResponseEntity.ok(AjaxResult.error(401, "登录失败，用户名或密码错误"));
        }
        return withSessionCookie(userInfo);
    }

    /**
     * 玩家注册接口。
     *
     * @param request 包含账号、密码、昵称与邀请码的注册信息
     * @return 注册并登录后的用户视图及 Session Cookie
     */
    @PostMapping("/register")
    @Log(title = "用户注册", businessType = BusinessType.INSERT)
    public ResponseEntity<AjaxResult> register(@RequestBody @Valid RegisterDto request) {
        String username = request.getUsername().trim();
        String password = request.getPassword();
        String nickname = request.getNickname() != null && !request.getNickname().trim().isEmpty()
                ? request.getNickname().trim() : username;
        String invite = request.getInvite().trim();

        UserService.UserInfo userInfo = userService.register(username, password, nickname, invite);
        if (userInfo == null) {
            return ResponseEntity.ok(AjaxResult.error(500, "注册失败"));
        }
        if (userInfo.getUserId() <= 0) {
            return ResponseEntity.ok(AjaxResult.error(userInfo.getErrorCode(), registerMsg(userInfo.getErrorCode())));
        }
        return withSessionCookie(userInfo);
    }

    private ResponseEntity<AjaxResult> withSessionCookie(UserService.UserInfo userInfo) {
        ResponseCookie cookie = ResponseCookie.from("sessionId", userInfo.getSessionId())
                .path("/").httpOnly(true).sameSite("Lax").build();
        return ResponseEntity.ok().header("Set-Cookie", cookie.toString()).body(toSuccessResult(userInfo));
    }

    private AjaxResult toSuccessResult(UserService.UserInfo userInfo) {
        AjaxResult result = AjaxResult.success();
        result.put("sessionId", userInfo.getSessionId());
        result.put("userId", userInfo.getUserId());
        result.put("username", userInfo.getUsername());
        result.put("nickname", userInfo.getNickname());
        result.put("token", userInfo.getToken());
        result.put("tables", userInfo.getTables());
        List<Map<String, Object>> infos = new ArrayList<>();
        for (UserService.TableInfoView t : userInfo.getTableInfos()) {
            infos.add(t.toMap());
        }
        result.put("tableInfos", infos);
        result.put("isAdmin", userInfo.isAdmin());
        return result;
    }

    private String registerMsg(int code) {
        switch (code) {
            case 2:
                return "用户名已存在";
            case 3:
                return "需要邀请码";
            case 4:
                return "邀请码无效";
            default:
                return "注册失败";
        }
    }
}
