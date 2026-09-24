package com.cloud.hub.web.learning.controller;

import com.cloud.hub.web.account.AccountService;
import com.cloud.hub.web.learning.model.Student;
import com.cloud.hub.web.learning.service.AuthService;
import com.cloud.hub.web.learning.service.StudentService;
import com.cloud.hub.web.learning.service.UsageService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.Map;

/**
 * 学习中心认证登录与会话心跳控制器。
 *
 * @author cloud
 */
@RestController("learningAuthController")
@RequestMapping("/api/learning/auth")
public class AuthController {

    private final AuthService auth;
    private final StudentService students;
    private final UsageService usage;
    private final AccountService accounts;

    public AuthController(AuthService auth,
                          StudentService students,
                          UsageService usage,
                          AccountService accounts) {
        this.auth = auth;
        this.students = students;
        this.usage = usage;
        this.accounts = accounts;
    }

    /**
     * 查询注册选项与校验邀请码。
     */
    @GetMapping("/registration")
    public Map<String, Object> registration(@RequestParam(value = "invite", required = false) String invite) throws Exception {
        Map<String, Object> result = auth.registrationOptions();
        if (invite != null && !invite.trim().isEmpty()) {
            if (!accounts.peekInviteValid(invite)) {
                throw new IllegalArgumentException("邀请码无效或已过期");
            }
            result.put("inviteValid", true);
        }
        return result;
    }

    /**
     * 登录学习中心。
     */
    @PostMapping("/login")
    public AuthService.LoginResult login(@RequestBody LoginRequest request) throws Exception {
        return auth.login(request.username, request.password, request.device);
    }

    /**
     * 注册学习账号。
     */
    @PostMapping("/register")
    public AuthService.LoginResult register(@RequestBody RegisterRequest request) throws Exception {
        return auth.register(request.username, request.password, request.name, request.invite, request.device);
    }

    /**
     * 退出当前登录。
     */
    @PostMapping("/logout")
    public Map<String, Object> logout(@RequestHeader(value = "X-Session-Token", required = false) String token,
                                      @RequestParam(value = "reason", required = false) String reason) {
        auth.logout(token, reason);
        return ok("已退出");
    }

    /**
     * 获取当前登录学员资料。
     */
    @GetMapping("/me")
    public Map<String, Object> me(@RequestHeader(value = "X-Session-Token", required = false) String token) throws Exception {
        return students.view(auth.require(token));
    }

    /**
     * 修改当前登录密码。
     */
    @PostMapping("/password")
    public Map<String, Object> password(@RequestHeader(value = "X-Session-Token", required = false) String token,
                                        @RequestBody PasswordRequest request) throws Exception {
        auth.require(token);
        if (!auth.changePassword(token, request.oldPassword, request.newPassword)) {
            throw new IllegalArgumentException("原密码不正确或新密码不符合要求");
        }
        return ok("密码已修改");
    }

    /**
     * 提交客户端探活与页面停留心跳。
     */
    @PostMapping("/heartbeat")
    public Map<String, Object> heartbeat(@RequestHeader(value = "X-Session-Token", required = false) String token,
                                         @RequestBody HeartbeatRequest request) throws Exception {
        Student user = auth.requireHeartbeat(token);
        usage.heartbeat(user, request.page, request.feature, request.device);
        return ok("ok");
    }

    /**
     * 接收前端页面异常埋点上报。
     */
    @PostMapping("/frontend-error")
    public Map<String, Object> frontendError(@RequestHeader(value = "X-Session-Token", required = false) String token) throws Exception {
        auth.require(token);
        usage.frontendError();
        return ok("ok");
    }

    private Map<String, Object> ok(String message) {
        return Collections.singletonMap("message", message);
    }

    public static class LoginRequest {
        public String username;
        public String password;
        public String device;
    }

    public static class RegisterRequest {
        public String username;
        public String password;
        public String name;
        public String invite;
        public String device;
    }

    public static class PasswordRequest {
        public String oldPassword;
        public String newPassword;
    }

    public static class HeartbeatRequest {
        public String page;
        public String feature;
        public String device;
    }
}
