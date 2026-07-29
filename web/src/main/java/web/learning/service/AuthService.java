package web.learning.service;

import web.learning.model.Student;
import web.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 学习模块鉴权：优先绑定游戏 Web 会话（cookie sessionId），
 * 兼容旧版 X-Session-Token（仅本地学习会话，lobby 不可用时的回退登录）。
 */
@Service
public class AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final StudentService students;
    private final UsageService usage;
    private final InviteService invites;
    private final UserService userService;
    private final boolean openRegister;
    private final Duration idleTimeout;
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    public AuthService(StudentService students, UsageService usage, InviteService invites,
                       @Lazy UserService userService,
                       @Value("${family-learning.registration.open-enabled:false}") boolean openRegister,
                       @Value("${family-learning.session.idle-minutes:10}") int idleMinutes) {
        this.students = students;
        this.usage = usage;
        this.invites = invites;
        this.userService = userService;
        this.openRegister = openRegister;
        this.idleTimeout = Duration.ofMinutes(Math.max(1, idleMinutes));
    }

    public Map<String, Object> registrationOptions() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("openRegister", openRegister);
        result.put("idleMinutes", idleTimeout.toMinutes());
        result.put("useGameAccount", true);
        return result;
    }

    /** 仅在 lobby 不可用时由 UserService 回退调用；正常请走首页游戏登录。 */
    public LoginResult login(String username, String password, String device) throws Exception {
        Student user = students.findByUsername(username);
        if (user == null) throw new IllegalArgumentException("用户名或密码不正确");
        if (!user.enabled) throw new IllegalArgumentException("账号已停用");
        if (!students.passwordMatches(user, password)) throw new IllegalArgumentException("用户名或密码不正确");
        students.recordLogin(user);
        String token = newToken();
        Session session = new Session(user, device);
        sessions.put(token, session);
        usage.login(user, device, false);
        log.info("学习本地登录 username={}, device={}", session.username, session.device);
        return new LoginResult(token, students.view(user));
    }

    public LoginResult register(String username, String password, String name, String inviteToken, String device) throws Exception {
        throw new IllegalArgumentException("请使用首页注册/登录；学习账号已与游戏账号统一");
    }

    public void logout(String token) {
        logout(token, null);
    }

    public void logout(String token, String reason) {
        String label = reason == null || reason.trim().isEmpty() ? "主动退出" : reason.trim();
        if ("idle".equalsIgnoreCase(label) || "空闲超时".equals(label)) label = "空闲超时(前端)";
        endSession(resolveToken(token), label, ClientIp.current());
    }

    public Student require(String token) throws Exception {
        return require(token, true);
    }

    public Student requireHeartbeat(String token) throws Exception {
        return require(token, false);
    }

    public Student require(String token, boolean touchActivity) throws Exception {
        String sid = resolveToken(token);
        if (sid == null || sid.trim().isEmpty()) throw new SecurityException("请先登录");

        UserService.UserInfo gameUser = userService.getSession(sid);
        if (gameUser != null) {
            Student linked = students.ensureLinked(gameUser.getUsername(), gameUser.getNickname(), gameUser.isAdmin());
            if (!linked.enabled) throw new SecurityException("账号已停用");
            if (touchActivity) {
                try { students.recordLogin(linked); } catch (Exception ignored) { /* 心跳路径不强制 */ }
            }
            return linked;
        }

        Session session = sessions.get(sid);
        if (session == null) throw new SecurityException("登录已过期，请重新登录");
        LocalDateTime now = LocalDateTime.now();
        String ip = ClientIp.current();
        if (session.lastActivityAt.plus(idleTimeout).isBefore(now)) {
            endSession(sid, "空闲超时", ip);
            throw new SecurityException("已超过" + idleTimeout.toMinutes() + "分钟未操作，请重新登录");
        }
        Student user = students.get(session.userId);
        if (user == null || !user.enabled) {
            endSession(sid, "账号不可用", ip);
            throw new SecurityException("账号不可用");
        }
        session.lastIp = ip;
        if (touchActivity) session.lastActivityAt = now;
        return user;
    }

    public Student requireAdmin(String token) throws Exception {
        Student user = require(token, true);
        // 游戏 admin 已在 ensureLinked 升为 ADMIN；本地会话仍检查 role
        if (!"ADMIN".equals(user.role)) throw new SecurityException("需要管理员权限");
        return user;
    }

    public Student requirePermission(String token, String permission) throws Exception {
        Student user = require(token, true);
        if (user.mustChangePassword && sessions.containsKey(resolveToken(token))) {
            // 仅本地学习会话强制改密；游戏账号不强制
            throw new SecurityException("请先修改初始密码");
        }
        if (!"ADMIN".equals(user.role) && (user.permissions == null || !user.permissions.contains(permission)))
            throw new SecurityException("该功能未开放");
        return user;
    }

    @Scheduled(fixedDelay = 60000)
    public void removeExpiredSessions() {
        LocalDateTime now = LocalDateTime.now();
        for (Map.Entry<String, Session> entry : sessions.entrySet()) {
            Session session = entry.getValue();
            if (session.lastActivityAt.plus(idleTimeout).isBefore(now)) {
                endSession(entry.getKey(), "空闲超时(定时清理)", session.lastIp == null ? "-" : session.lastIp);
            }
        }
    }

    public void requireSelfOrAdmin(Student current, String userId) {
        if (!current.id.equals(userId) && !"ADMIN".equals(current.role)) throw new SecurityException("不能访问其他用户数据");
    }

    private String resolveToken(String token) {
        if (token != null && !token.trim().isEmpty()) return token.trim();
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) return null;
        HttpServletRequest request = attrs.getRequest();
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie cookie : cookies) {
            if ("sessionId".equals(cookie.getName())) return cookie.getValue();
        }
        return null;
    }

    private void endSession(String token, String reason, String ip) {
        if (token == null) return;
        Session session = sessions.remove(token);
        if (session == null) return;
        usage.logout(session.userId);
        log.info("学习本地会话结束 reason={}, username={}, ip={}", reason, session.username, ip);
    }

    private String newToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public static class Session {
        public String userId;
        public String username;
        public String name;
        public String device;
        public String lastIp;
        public LocalDateTime lastActivityAt;

        public Session(Student user, String device) {
            this.userId = user.id;
            this.username = user.username;
            this.name = user.name;
            this.device = device == null || device.trim().isEmpty() ? "未知设备" : device.trim();
            this.lastIp = ClientIp.current();
            this.lastActivityAt = LocalDateTime.now();
        }
    }

    public static class LoginResult {
        public String token;
        public Map<String, Object> user;
        public LoginResult(String token, Map<String, Object> user) {
            this.token = token;
            this.user = user;
        }
    }
}
