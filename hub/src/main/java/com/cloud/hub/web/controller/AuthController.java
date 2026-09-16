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
 * 认证接口（对齐 /api/auth/*，参照 RuoYi 设计）
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    /**
     * POST /api/auth/login { "username","password" } 或 { "token" }
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
     * POST /api/auth/register { "username","password","nickname?","invite" }
     */
    @PostMapping("/register")
    @Log(title = "用户注册", businessType = BusinessType.INSERT)
    public ResponseEntity<AjaxResult> register(@RequestBody @Valid RegisterDto request) {
        String username = request.getUsername().trim();
        String password = request.getPassword();
        String nickname = request.getNickname() != null && !request.getNickname().trim().isEmpty() ?
                request.getNickname().trim() : username;
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
