package com.cloud.web.learning.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import web.learning.model.Student;
import web.learning.service.JsonFileStore;

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

    public static boolean isValidArchiveId(String id) {
        return id != null && id.matches(ARCHIVE_ID_PATTERN);
    }

    @PostConstruct
    public void init() throws Exception {
        migrateExisting();
        Student admin = findByUsername("admin");
        if (admin == null) {
            create("admin", "管理员", "ADMIN", ALL_PERMISSIONS);
        }
    }

    public synchronized Student create(String username, String name, String role,
                                       List<String> permissions) throws Exception {
        username = normalizeUsername(username);
        if (findByUsername(username) != null) throw new IllegalArgumentException("用户名已存在");
        Student student = new Student(idFor(username), username, normalizeName(name == null ? username : name));
        student.role = "ADMIN".equals(role) ? "ADMIN" : "USER";
        student.permissions = new ArrayList<>("ADMIN".equals(student.role) ? ALL_PERMISSIONS :
                permissions == null || permissions.isEmpty() ? DEFAULT_PERMISSIONS : permissions);
        store.write(store.path("students", student.id), student);
        return student;
    }

    /**
     * 将游戏账号同步为学习档案；游戏管理员自动为学习管理员。
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
            } catch (IllegalArgumentException ignored) { /* 保持原名 */ }
        }
        if (changed) store.write(store.path("students", student.id), student);
        return student;
    }

    public Student get(String id) throws Exception {
        return store.read(store.path("students", id), Student.class);
    }

    public List<Student> list() throws Exception {
        return store.readFolder("students", Student.class);
    }

    public Student findByUsername(String raw) throws Exception {
        if (raw == null) return null;
        String username = raw.trim().toLowerCase();
        for (Student student : list()) if (username.equals(student.username)) return student;
        return null;
    }

    public synchronized Student update(String id, Student changes) throws Exception {
        Student student = require(id);
        if (changes.name != null) student.name = normalizeName(changes.name);
        if (changes.stage != null && !changes.stage.trim().isEmpty()) student.stage = changes.stage.trim();
        student.enabled = changes.enabled;
        if (changes.role != null) student.role = "ADMIN".equals(changes.role) ? "ADMIN" : "USER";
        if (changes.permissions != null) student.permissions = new ArrayList<>(changes.permissions);
        if ("ADMIN".equals(student.role) && !student.permissions.contains("ADMIN")) student.permissions.add("ADMIN");
        store.write(store.path("students", id), student);
        return student;
    }

    public synchronized void delete(String id) throws Exception {
        Student student = require(id);
        if ("admin".equals(student.username)) throw new IllegalArgumentException("不能删除初始管理员");
        store.delete(store.path("students", id));
        store.delete(store.path("records", id));
        store.delete(store.path("mistakes", id));
    }

    public synchronized void recordLogin(Student student) throws Exception {
        student.loginCount++;
        student.lastLoginAt = LocalDateTime.now();
        student.lastActiveAt = student.lastLoginAt;
        store.write(store.path("students", student.id), student);
    }

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
        if (student == null) throw new IllegalArgumentException("找不到用户");
        return student;
    }

    private String normalizeUsername(String value) {
        String username = value == null ? "" : value.trim().toLowerCase();
        if (!username.matches("[\\p{L}\\p{N}_.-]{1,20}"))
            throw new IllegalArgumentException("用户名应为1到20位文字、数字或._-");
        return username;
    }

    private String normalizeName(String value) {
        String name = value == null ? "" : value.trim().replaceAll("\\s+", " ");
        if (name.length() < 1 || name.length() > 20) throw new IllegalArgumentException("姓名长度应为1到20个字符");
        return name;
    }

    private String idFor(String username) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(username.getBytes(StandardCharsets.UTF_8));
        StringBuilder value = new StringBuilder();
        for (int i = 0; i < 10; i++) value.append(String.format("%02x", digest[i]));
        return value.toString();
    }

    private void migrateExisting() throws Exception {
        List<JsonFileStore.Entry<Student>> entries = store.readFolderEntries("students", Student.class);
        Set<String> usedIds = new HashSet<>();
        for (JsonFileStore.Entry<Student> entry : entries) {
            if (isValidArchiveId(entry.value.id)) usedIds.add(entry.value.id);
            else if (isValidArchiveId(entry.key)) usedIds.add(entry.key);
        }
        for (JsonFileStore.Entry<Student> entry : entries) {
            Student student = entry.value;
            String oldKey = entry.key;
            boolean changed = false;
            if (student.username == null || student.username.trim().isEmpty()) {
                String fallback = student.name == null || student.name.trim().isEmpty() ? oldKey : student.name;
                try {
                    student.username = normalizeUsername(fallback);
                } catch (IllegalArgumentException ignored) {
                    student.username = ("user" + Integer.toHexString(fallback.hashCode())).replace("-", "");
                }
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
                if (student.id != null && !student.id.equals(oldKey) && !student.id.equals(targetId))
                    moveLearningData(student.id, targetId);
                student.id = targetId;
                store.writeDocument("students", targetId, student);
                if (!oldKey.equals(targetId)) store.deleteDocument("students", oldKey);
                usedIds.add(targetId);
                changed = false;
            } else if (changed) {
                store.writeDocument("students", targetId, student);
            }
        }
    }

    private String resolveArchiveId(Student student, String oldKey, Set<String> usedIds) throws Exception {
        if (isValidArchiveId(student.id)) return student.id;
        if (isValidArchiveId(oldKey)) return oldKey;
        String preferred = idFor(student.username == null ? "user" : student.username);
        if (!usedIds.contains(preferred) || preferred.equals(oldKey) || preferred.equals(student.id)) return preferred;
        for (int i = 0; i < 16; i++) {
            String candidate = idFor(student.username + "#" + i);
            if (!usedIds.contains(candidate)) return candidate;
        }
        return idFor(student.username + "#" + System.nanoTime());
    }

    private void moveLearningData(String fromKey, String toKey) throws Exception {
        if (fromKey == null || toKey == null || fromKey.equals(toKey)) return;
        store.moveDocument("records", fromKey, toKey);
        store.moveDocument("mistakes", fromKey, toKey);
    }
}
