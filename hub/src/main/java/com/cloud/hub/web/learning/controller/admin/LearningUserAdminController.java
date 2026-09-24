package com.cloud.hub.web.learning.controller.admin;

import com.cloud.hub.web.account.AccountService;
import com.cloud.hub.web.learning.model.Student;
import com.cloud.hub.web.learning.service.StudentService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 学习中心学员账号与权限管理控制器。
 *
 * @author cloud
 */
@RestController
@RequestMapping("/api/learning/admin/users")
public class LearningUserAdminController {

    private final LearningAdminAccess access;
    private final StudentService students;
    private final AccountService accounts;

    public LearningUserAdminController(LearningAdminAccess access,
                                       StudentService students,
                                       AccountService accounts) {
        this.access = access;
        this.students = students;
        this.accounts = accounts;
    }

    /**
     * 查询学习中心全部学员档案列表。
     */
    @GetMapping
    public List<Map<String, Object>> list(@RequestHeader(value = "X-Session-Token", required = false) String token)
            throws Exception {
        access.require(token);
        return students.list().stream().map(students::view).collect(Collectors.toList());
    }

    /**
     * 管理员开通新建学员账号。
     */
    @PostMapping
    public Map<String, Object> create(@RequestHeader(value = "X-Session-Token", required = false) String token,
                                      @RequestBody UserRequest request) throws Exception {
        access.require(token);
        if (!accounts.createManagedUser(request.username, request.name).isPresent()) {
            throw new IllegalArgumentException("用户名已存在或创建失败");
        }
        return students.view(students.create(request.username, request.name, request.role, request.permissions));
    }

    /**
     * 更新学员姓名、学段、角色与功能权限。
     */
    @PutMapping("/{id}")
    public Map<String, Object> update(@RequestHeader(value = "X-Session-Token", required = false) String token,
                                      @PathVariable String id,
                                      @RequestBody Student changes) throws Exception {
        access.require(token);
        Student updated = students.update(id, changes);
        accounts.setEnabled(updated.username, updated.enabled);
        return students.view(updated);
    }

    /**
     * 删除指定学员档案。
     */
    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@RequestHeader(value = "X-Session-Token", required = false) String token,
                                      @PathVariable String id) throws Exception {
        access.require(token);
        Student user = require(id);
        if (!accounts.deleteUser(user.username)) {
            throw new IllegalArgumentException("不能删除该用户");
        }
        students.delete(id);
        return message("用户已删除");
    }

    /**
     * 管理员重置学员登录密码（默认恢复为 123456）。
     */
    @PostMapping("/{id}/reset-password")
    public Map<String, Object> resetPassword(@RequestHeader(value = "X-Session-Token", required = false) String token,
                                             @PathVariable String id) throws Exception {
        access.require(token);
        if (!accounts.resetPassword(require(id).username)) {
            throw new IllegalArgumentException("密码重置失败");
        }
        return message("密码已重置为123456");
    }

    private Student require(String id) throws Exception {
        Student student = students.get(id);
        if (student == null) {
            throw new IllegalArgumentException("找不到用户");
        }
        return student;
    }

    private Map<String, Object> message(String value) {
        return Collections.singletonMap("message", value);
    }

    public static class UserRequest {
        public String username;
        public String name;
        public String role;
        public List<String> permissions = new ArrayList<>();
    }
}
