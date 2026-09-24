package com.cloud.hub.web.learning.service;

import com.cloud.hub.web.learning.model.Student;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 学生档案与学籍权限管理服务。
 * <p>
 * 负责游戏账号自动映射为学习档案、档案唯一 ID 生成、权限集分配及旧版数据平滑迁移升级。
 *
 * @author cloud
 */
@Service
public class StudentService {

    private static final Logger log = LoggerFactory.getLogger(StudentService.class);
    public static final String ARCHIVE_ID_PATTERN = "[a-f0-9]{20}";
    public static final List<String> DEFAULT_PERMISSIONS = Arrays.asList(
            "CHINESE", "MATH", "ENGLISH", "PRIMARY", "RESOURCES", "MISTAKES", "RECORDS", "STATS", "PRINT");
    public static final List<String> ALL_PERMISSIONS = Arrays.asList(
            "CHINESE", "MATH", "ENGLISH", "HISTORY", "CHEMISTRY", "PRIMARY", "RESOURCES",
            "MISTAKES", "RECORDS", "STATS", "PRINT", "ADMIN");

    private final JsonFileStore store;

    public StudentService(JsonFileStore store) {
        this.store = store;
    }

    /**
     * 校验学习档案 ID 格式合法性。
     */
    public static boolean isValidArchiveId(String id) {
        return id != null && id.matches(ARCHIVE_ID_PATTERN);
    }

    /**
     * 容器初始化迁移与确保初始管理员档案。
     */
    @PostConstruct
    public void init() throws Exception {
        migrateExisting();
        Student admin = findByUsername("admin");
        if (admin == null) {
            create("admin", "管理员", "ADMIN", ALL_PERMISSIONS);
        }
    }

    /**
     * 创建全新学生学习档案。
     *
     * @param username    用户名
     * @param name        姓名
     * @param role        角色 (USER / ADMIN)
     * @param permissions 权限列表
     * @return 创建的学生档案
     * @throws Exception 校验或冲突异常
     */
    public synchronized Student create(String username, String name, String role,
                                       List<String> permissions) throws Exception {
        username = normalizeUsername(username);
        if (findByUsername(username) != null) {
            throw new IllegalArgumentException("用户名已存在");
        }
        Student student = new Student(idFor(username), username, normalizeName(name == null ? username : name));
        student.role = "ADMIN".equals(role) ? "ADMIN" : "USER";
        student.permissions = new ArrayList<>("ADMIN".equals(student.role) ? ALL_PERMISSIONS :
                permissions == null || permissions.isEmpty() ? DEFAULT_PERMISSIONS : permissions);
        store.write(store.path("students", student.id), student);
        return student;
    }

    /**
     * 将平台用户登录映射并同步至学习档案。
     *
     * @param username  用户名
     * @param nickname  昵称
     * @param gameAdmin 是否为管理员
     * @return 映射后的学习档案
     * @throws Exception 存储异常
     */
    public synchronized Student ensureLinked(String username, String nickname, boolean gameAdmin) throws Exception {
        Student student = findByUsername(username);
        if (student == null) {
            student = new Student(idFor(normalizeUsername(username)), normalizeUsername(username),
                    normalizeName(nickname == null || nickname.trim().isEmpty() ? username : nickname));
            student.role = gameAdmin ? "ADMIN" : "USER";
            student.permissions = new ArrayList<>(gameAdmin ? ALL_PERMISSIONS : DEFAULT_PERMISSIONS);
            store.write(store.path("students", student.id), student);
            return student;
        }
        boolean changed = syncProfileAttrs(student, nickname, gameAdmin);
        if (changed) {
            store.write(store.path("students", student.id), student);
        }
        return student;
    }

    private boolean syncProfileAttrs(Student student, String nickname, boolean gameAdmin) {
        boolean changed = false;
        if (gameAdmin && !"ADMIN".equals(student.role)) {
            student.role = "ADMIN";
            student.permissions = new ArrayList<>(ALL_PERMISSIONS);
            changed = true;
        }
        if (nickname != null && !nickname.trim().isEmpty() && !nickname.trim().equals(student.name)) {
            try {
                student.name = normalizeName(nickname);
                changed = true;
            } catch (IllegalArgumentException ignored) {
            }
        }
        return changed;
    }

    /**
     * 根据档案 ID 获取学生信息。
     */
    public Student get(String id) throws Exception {
        return store.read(store.path("students", id), Student.class);
    }

    /**
     * 获取全部学生档案列表。
     */
    public List<Student> list() throws Exception {
        return store.readFolder("students", Student.class);
    }

    /**
     * 根据用户名检索学生档案。
     */
    public Student findByUsername(String raw) throws Exception {
        if (raw == null) {
            return null;
        }
        String username = raw.trim().toLowerCase();
        for (Student student : list()) {
            if (username.equals(student.username)) {
                return student;
            }
        }
        return null;
    }

    /**
     * 更新学生属性与权限。
     */
    public synchronized Student update(String id, Student changes) throws Exception {
        Student student = require(id);
        if (changes.name != null) {
            student.name = normalizeName(changes.name);
        }
        if (changes.stage != null && !changes.stage.trim().isEmpty()) {
            student.stage = changes.stage.trim();
        }
        student.enabled = changes.enabled;
        if (changes.role != null) {
            student.role = "ADMIN".equals(changes.role) ? "ADMIN" : "USER";
        }
        if (changes.permissions != null) {
            student.permissions = new ArrayList<>(changes.permissions);
        }
        if ("ADMIN".equals(student.role) && !student.permissions.contains("ADMIN")) {
            student.permissions.add("ADMIN");
        }
        store.write(store.path("students", id), student);
        return student;
    }

    /**
     * 删除指定学生及其所有学习记录与错题。
     */
    public synchronized void delete(String id) throws Exception {
        Student student = require(id);
        if ("admin".equals(student.username)) {
            throw new IllegalArgumentException("不能删除初始管理员");
        }
        store.delete(store.path("students", id));
        store.delete(store.path("records", id));
        store.delete(store.path("mistakes", id));
    }

    /**
     * 记录一次新的登录与活跃行为。
     */
    public synchronized void recordLogin(Student student) throws Exception {
        student.loginCount++;
        student.lastLoginAt = LocalDateTime.now();
        student.lastActiveAt = student.lastLoginAt;
        store.write(store.path("students", student.id), student);
    }

    /**
     * 将学生实体转换为对外安全视图 Map。
     */
    public Map<String, Object> view(Student student) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", student.id);
        result.put("username", student.username);
        result.put("name", student.name);
        result.put("role", student.role);
        result.put("enabled", student.enabled);
        result.put("permissions", student.permissions);
        result.put("stage", student.stage);
        result.put("createdAt", student.createdAt);
        result.put("lastActiveAt", student.lastActiveAt);
        result.put("lastLoginAt", student.lastLoginAt);
        result.put("loginCount", student.loginCount);
        return result;
    }

    private Student require(String id) throws Exception {
        Student student = get(id);
        if (student == null) {
            throw new IllegalArgumentException("找不到用户");
        }
        return student;
    }

    private String normalizeUsername(String value) {
        String username = value == null ? "" : value.trim().toLowerCase();
        if (!username.matches("[\\p{L}\\p{N}_.-]{1,20}")) {
            throw new IllegalArgumentException("用户名应为1到20位文字、数字或._-");
        }
        return username;
    }

    private String normalizeName(String value) {
        String name = value == null ? "" : value.trim().replaceAll("\\s+", " ");
        if (name.length() < 1 || name.length() > 20) {
            throw new IllegalArgumentException("姓名长度应为1到20个字符");
        }
        return name;
    }

    private String idFor(String username) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(username.getBytes(StandardCharsets.UTF_8));
        StringBuilder value = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            value.append(String.format("%02x", digest[i]));
        }
        return value.toString();
    }

    private void migrateExisting() throws Exception {
        List<JsonFileStore.Entry<Student>> entries = store.readFolderEntries("students", Student.class);
        Set<String> usedIds = new HashSet<>();
        for (JsonFileStore.Entry<Student> entry : entries) {
            if (isValidArchiveId(entry.value.id)) {
                usedIds.add(entry.value.id);
            } else if (isValidArchiveId(entry.key)) {
                usedIds.add(entry.key);
            }
        }
        for (JsonFileStore.Entry<Student> entry : entries) {
            migrateSingleEntry(entry, usedIds);
        }
    }

    private void migrateSingleEntry(JsonFileStore.Entry<Student> entry, Set<String> usedIds) throws Exception {
        Student student = entry.value;
        String oldKey = entry.key;
        boolean changed = false;
        if (student.username == null || student.username.trim().isEmpty()) {
            student.username = fixUsername(student.name, oldKey);
            changed = true;
        }
        if (student.role == null) {
            student.role = "USER";
            changed = true;
        }
        if (student.permissions == null || student.permissions.isEmpty()) {
            student.permissions = new ArrayList<>(DEFAULT_PERMISSIONS);
            changed = true;
        }

        String targetId = resolveArchiveId(student, oldKey, usedIds);
        if (!targetId.equals(student.id) || !targetId.equals(oldKey)) {
            log.warn("修复无效学习档案: username={}, oldKey={}, oldId={}, newId={}", student.username, oldKey, student.id, targetId);
            moveLearningData(oldKey, targetId);
            if (student.id != null && !student.id.equals(oldKey) && !student.id.equals(targetId)) {
                moveLearningData(student.id, targetId);
            }
            student.id = targetId;
            store.writeDocument("students", targetId, student);
            if (!oldKey.equals(targetId)) {
                store.deleteDocument("students", oldKey);
            }
            usedIds.add(targetId);
        } else if (changed) {
            store.writeDocument("students", targetId, student);
        }
    }

    private String fixUsername(String name, String oldKey) {
        String fallback = name == null || name.trim().isEmpty() ? oldKey : name;
        try {
            return normalizeUsername(fallback);
        } catch (IllegalArgumentException ignored) {
            return ("user" + Integer.toHexString(fallback.hashCode())).replace("-", "");
        }
    }

    private String resolveArchiveId(Student student, String oldKey, Set<String> usedIds) throws Exception {
        if (isValidArchiveId(student.id)) {
            return student.id;
        }
        if (isValidArchiveId(oldKey)) {
            return oldKey;
        }
        String preferred = idFor(student.username == null ? "user" : student.username);
        if (!usedIds.contains(preferred) || preferred.equals(oldKey) || preferred.equals(student.id)) {
            return preferred;
        }
        for (int i = 0; i < 16; i++) {
            String candidate = idFor(student.username + "#" + i);
            if (!usedIds.contains(candidate)) {
                return candidate;
            }
        }
        return idFor(student.username + "#" + System.nanoTime());
    }

    private void moveLearningData(String fromKey, String toKey) throws Exception {
        if (fromKey == null || toKey == null || fromKey.equals(toKey)) {
            return;
        }
        store.moveDocument("records", fromKey, toKey);
        store.moveDocument("mistakes", fromKey, toKey);
    }
}
