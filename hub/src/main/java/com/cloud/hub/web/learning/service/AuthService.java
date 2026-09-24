package com.cloud.hub.web.learning.service;

import com.cloud.hub.web.identity.SessionResolver;
import com.cloud.hub.web.learning.model.Student;
import com.cloud.hub.web.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 学习业务专属身份鉴权与权限校验门面。
 * <p>
 * 账号、密码凭据和会话统一由底层 UserService 管理，本类负责将平台用户无缝投影为学习档案并实施权限守卫。
 *
 * @author cloud
 */
@Service
public class AuthService {

    private final StudentService students;
    private final UserService users;
    private final SessionResolver sessions;
    private final boolean openRegister;

    public AuthService(StudentService students,
                       UserService users,
                       SessionResolver sessions,
                       @Value("${account.open-register:false}") boolean openRegister) {
        this.students = students;
        this.users = users;
        this.sessions = sessions;
        this.openRegister = openRegister;
    }

    /**
     * 查询注册模式配置项。
     */
    public Map<String, Object> registrationOptions() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("openRegister", openRegister);
        result.put("useGameAccount", true);
        return result;
    }

    /**
     * 账号密码登录学习中心。
     *
     * @param username 用户名
     * @param password 密码
     * @param device   设备标识
     * @return 登录会话与学习档案视图
     * @throws Exception 认证失败异常
     */
    public LoginResult login(String username, String password, String device) throws Exception {
        UserService.UserInfo user = users.login(username, password);
        if (user == null) {
            throw new IllegalArgumentException("用户名或密码不正确");
        }
        return result(user);
    }

    /**
     * 注册新学员账号。
     *
     * @param username 用户名
     * @param password 密码
     * @param name     姓名/昵称
     * @param invite   邀请码
     * @param device   设备标识
     * @return 注册并登录后的结果
     * @throws Exception 校验异常
     */
    public LoginResult register(String username, String password, String name,
                                String invite, String device) throws Exception {
        UserService.UserInfo user = users.register(username, password, name, invite);
        if (user == null || user.getUserId() <= 0) {
            throw new IllegalArgumentException(registerMessage(user == null ? 1 : user.getErrorCode()));
        }
        return result(user);
    }

    /**
     * 登出当前用户会话。
     *
     * @param token 凭证
     */
    public void logout(String token) {
        logout(token, null);
    }

    /**
     * 登出当前用户会话附带原因。
     *
     * @param token  凭据
     * @param reason 原因
     */
    public void logout(String token, String reason) {
        String sessionId = resolveToken(token);
        if (sessionId != null) {
            users.logout(sessionId);
        }
    }

    /**
     * 修改用户登录密码。
     */
    public boolean changePassword(String token, String oldPassword, String newPassword) {
        String sessionId = resolveToken(token);
        return sessionId != null && users.changePassword(sessionId, oldPassword, newPassword);
    }

    /**
     * 强制校验登录，默认触碰活跃时间戳。
     */
    public Student require(String token) throws Exception {
        return require(token, true);
    }

    /**
     * 心跳请求校验登录，不更新重度活跃记录。
     */
    public Student requireHeartbeat(String token) throws Exception {
        return require(token, false);
    }

    /**
     * 校验 Token 有效性并映射关联的学习档案。
     *
     * @param token         用户 Token
     * @param touchActivity 是否更新活跃时间戳
     * @return 学习档案
     * @throws Exception 未登录或账号被停用
     */
    public Student require(String token, boolean touchActivity) throws Exception {
        UserService.UserInfo user = current(token);
        Student profile = students.ensureLinked(user.getUsername(), user.getNickname(), user.isAdmin());
        if (!profile.enabled) {
            throw new SecurityException("账号已停用");
        }
        if (touchActivity) {
            students.recordLogin(profile);
        }
        return profile;
    }

    /**
     * 校验当前登录用户必须具备管理员权限。
     */
    public Student requireAdmin(String token) throws Exception {
        UserService.UserInfo user = current(token);
        if (!user.isAdmin()) {
            throw new SecurityException("需要管理员权限");
        }
        return students.ensureLinked(user.getUsername(), user.getNickname(), true);
    }

    /**
     * 校验指定模块权限。
     */
    public Student requirePermission(String token, String permission) throws Exception {
        UserService.UserInfo user = current(token);
        Student profile = students.ensureLinked(user.getUsername(), user.getNickname(), user.isAdmin());
        if (!user.isAdmin() && (profile.permissions == null || !profile.permissions.contains(permission))) {
            throw new SecurityException("该功能未开放");
        }
        return profile;
    }

    /**
     * 校验操作者必须为本人或管理员。
     */
    public void requireSelfOrAdmin(Student current, String userId) {
        if (!current.id.equals(userId) && !"ADMIN".equals(current.role)) {
            throw new SecurityException("不能访问其他用户数据");
        }
    }

    private UserService.UserInfo current(String token) {
        String sessionId = resolveToken(token);
        if (sessionId == null || sessionId.isEmpty()) {
            throw new SecurityException("请先登录");
        }
        UserService.UserInfo user = users.getSession(sessionId);
        if (user == null) {
            throw new SecurityException("登录已过期，请重新登录");
        }
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

    /**
     * 登录结果实体。
     */
    public static class LoginResult {
        public final String token;
        public final Map<String, Object> user;

        public LoginResult(String token, Map<String, Object> user) {
            this.token = token;
            this.user = user;
        }
    }
}
