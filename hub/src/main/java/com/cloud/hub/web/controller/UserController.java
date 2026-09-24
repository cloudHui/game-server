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
 * 用户身份与会话基础接口控制器（兼容历史路径 /api/login, /api/validate, /api/logout）。
 *
 * @author cloud
 */
@RestController
@RequestMapping("/api")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * 兼容登录接口。
     *
     * @param request 登录入参
     * @return 登录结果
     */
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

    /**
     * 会话 Token 校验接口。
     *
     * @param tokenParam    URL 参数中的 token
     * @param authorization 请求头中的 Authorization
     * @return 会话用户信息
     */
    @GetMapping("/validate")
    public ResponseEntity<AjaxResult> validate(
            @RequestParam(value = "token", required = false) String tokenParam,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        String token = resolveToken(tokenParam, authorization);
        if (token == null || token.isEmpty()) {
            return ResponseEntity.status(401).body(AjaxResult.error(401, "缺少token"));
        }
        UserService.UserInfo userInfo = userService.validateToken(token);
        if (userInfo != null) {
            return ResponseEntity.ok(toSuccess(userInfo));
        }
        return ResponseEntity.ok(AjaxResult.error(401, "Token无效或已过期"));
    }

    private String resolveToken(String tokenParam, String authorization) {
        if (tokenParam != null && !tokenParam.trim().isEmpty()) {
            return tokenParam.trim();
        }
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring(7).trim();
        }
        return null;
    }

    /**
     * 用户登出接口。
     *
     * @param request 包含可选 sessionId 的字典
     * @return 成功状态
     */
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
