package com.cloud.hub.web.controller;

import com.cloud.hub.common.core.domain.AjaxResult;
import com.cloud.hub.framework.security.LoginUser;
import com.cloud.hub.framework.security.SecurityUtils;
import com.cloud.hub.web.dto.LoginDto;
import com.cloud.hub.web.service.UserService;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 兼容旧路径（/api/login, /api/validate, /api/logout，参照 RuoYi 响应格式对齐）
 */
@RestController
@RequestMapping("/api")
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/login")
    public ResponseEntity<AjaxResult> login(@RequestBody LoginDto request) {
        String username = request.getUsername();
        String password = request.getPassword();
        if (username == null || username.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(AjaxResult.error(400, "请使用 username/password 登录，或改用 /api/auth/login"));
        }
        if (password == null || password.isEmpty()) {
            return ResponseEntity.badRequest().body(AjaxResult.error(400, "密码不能为空"));
        }

        UserService.UserInfo userInfo = userService.login(username.trim(), password);
        if (userInfo == null) {
            return ResponseEntity.status(401).body(AjaxResult.error(401, "登录失败"));
        }
        return ResponseEntity.ok(toSuccess(userInfo));
    }

    @GetMapping("/validate")
    public ResponseEntity<AjaxResult> validate(
            @RequestParam(value = "token", required = false) String tokenParam,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        String token = null;
        if (tokenParam != null && !tokenParam.trim().isEmpty()) {
            token = tokenParam.trim();
        } else if (authorization != null && authorization.startsWith("Bearer ")) {
            token = authorization.substring(7).trim();
        }
        if (token == null || token.isEmpty()) {
            return ResponseEntity.status(401).body(AjaxResult.error(401, "缺少token"));
        }
        UserService.UserInfo userInfo = userService.validateToken(token);
        if (userInfo != null) {
            return ResponseEntity.ok(toSuccess(userInfo));
        }
        return ResponseEntity.ok(AjaxResult.error(401, "Token无效或已过期"));
    }

    @PostMapping("/logout")
    public ResponseEntity<AjaxResult> logout(@RequestBody(required = false) Map<String, String> request) {
        String sessionId = request != null ? request.get("sessionId") : null;
        if (sessionId == null) {
            LoginUser loginUser = SecurityUtils.getLoginUser();
            if (loginUser != null) {
                sessionId = loginUser.getSessionId();
            }
        }
        if (sessionId != null) {
            userService.logout(sessionId);
        }
        ResponseCookie cookie = ResponseCookie.from("sessionId", "")
                .path("/").maxAge(0).httpOnly(true).sameSite("Lax").build();
        return ResponseEntity.ok().header("Set-Cookie", cookie.toString()).body(AjaxResult.success());
    }

    private AjaxResult toSuccess(UserService.UserInfo userInfo) {
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
}
