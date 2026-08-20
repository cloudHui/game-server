package com.cloud.weball.web.learning.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import web.identity.SessionResolver;
import web.learning.model.Student;
import web.learning.service.StudentService;
import web.service.UserService;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 学习业务的身份适配器。账号、凭据和会话统一由 UserService 管理，
 * 本类只负责把统一用户映射为学习档案并检查学习权限。
 */
@Service
public class AuthService {
    private final StudentService students;
    private final UserService users;
    private final SessionResolver sessions;
    private final boolean openRegister;

    public AuthService(StudentService students, UserService users, SessionResolver sessions,
                       @Value("${account.open-register:false}") boolean openRegister) {
        this.students = students;
        this.users = users;
        this.sessions = sessions;
        this.openRegister = openRegister;
    }

    public Map<String, Object> registrationOptions() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("openRegister", openRegister);
        result.put("useGameAccount", true);
        return result;
    }

    public LoginResult login(String username, String password, String device) throws Exception {
        UserService.UserInfo user = users.login(username, password);
        if (user == null) throw new IllegalArgumentException("用户名或密码不正确");
        return result(user);
    }

    public LoginResult register(String username, String password, String name,
                                String invite, String device) throws Exception {
        UserService.UserInfo user = users.register(username, password, name, invite);
        if (user == null || user.getUserId() <= 0) {
            throw new IllegalArgumentException(registerMessage(user == null ? 1 : user.getErrorCode()));
        }
        return result(user);
    }

    public void logout(String token) {
        logout(token, null);
    }

    public void logout(String token, String reason) {
        String sessionId = resolveToken(token);
        if (sessionId != null) users.logout(sessionId);
    }

    public boolean changePassword(String token, String oldPassword, String newPassword) {
        String sessionId = resolveToken(token);
        return sessionId != null && users.changePassword(sessionId, oldPassword, newPassword);
    }

    public Student require(String token) throws Exception {
        return require(token, true);
    }

    public Student requireHeartbeat(String token) throws Exception {
        return require(token, false);
    }

    public Student require(String token, boolean touchActivity) throws Exception {
        UserService.UserInfo user = current(token);
        Student profile = students.ensureLinked(user.getUsername(), user.getNickname(), user.isAdmin());
        if (!profile.enabled) throw new SecurityException("账号已停用");
        if (touchActivity) students.recordLogin(profile);
        return profile;
    }

    public Student requireAdmin(String token) throws Exception {
        UserService.UserInfo user = current(token);
        if (!user.isAdmin()) throw new SecurityException("需要管理员权限");
        return students.ensureLinked(user.getUsername(), user.getNickname(), true);
    }

    public Student requirePermission(String token, String permission) throws Exception {
        UserService.UserInfo user = current(token);
        Student profile = students.ensureLinked(user.getUsername(), user.getNickname(), user.isAdmin());
        if (!user.isAdmin() && (profile.permissions == null || !profile.permissions.contains(permission))) {
            throw new SecurityException("该功能未开放");
        }
        return profile;
    }

    public void requireSelfOrAdmin(Student current, String userId) {
        if (!current.id.equals(userId) && !"ADMIN".equals(current.role)) {
            throw new SecurityException("不能访问其他用户数据");
        }
    }

    private UserService.UserInfo current(String token) {
        String sessionId = resolveToken(token);
        if (sessionId == null || sessionId.isEmpty()) throw new SecurityException("请先登录");
        UserService.UserInfo user = users.getSession(sessionId);
        if (user == null) throw new SecurityException("登录已过期，请重新登录");
        return user;
    }

    private LoginResult result(UserService.UserInfo user) throws Exception {
        Student profile = students.ensureLinked(user.getUsername(), user.getNickname(), user.isAdmin());
        return new LoginResult(user.getSessionId(), students.view(profile));
    }

    private String resolveToken(String token) {
        return sessions.resolveCurrent(token);
    }

    private String registerMessage(int code) {
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

    public static class LoginResult {
        public final String token;
        public final Map<String, Object> user;

        public LoginResult(String token, Map<String, Object> user) {
            this.token = token;
            this.user = user;
        }
    }
}
