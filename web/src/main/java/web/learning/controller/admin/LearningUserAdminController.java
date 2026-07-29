package web.learning.controller.admin;

import org.springframework.web.bind.annotation.*;
import web.account.AccountService;
import web.learning.model.Student;
import web.learning.service.StudentService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/learning/admin/users")
public class LearningUserAdminController {
    private final LearningAdminAccess access;
    private final StudentService students;
    private final AccountService accounts;

    public LearningUserAdminController(LearningAdminAccess access, StudentService students,
                                       AccountService accounts) {
        this.access = access;
        this.students = students;
        this.accounts = accounts;
    }

    @GetMapping
    public List<Map<String, Object>> list(@RequestHeader(value = "X-Session-Token", required = false) String token) throws Exception {
        access.require(token);
        return students.list().stream().map(students::view).collect(Collectors.toList());
    }

    @PostMapping
    public Map<String, Object> create(@RequestHeader(value = "X-Session-Token", required = false) String token,
                                      @RequestBody UserRequest request) throws Exception {
        access.require(token);
        if (!accounts.createManagedUser(request.username, request.name).isPresent()) {
            throw new IllegalArgumentException("用户名已存在或创建失败");
        }
        return students.view(students.create(
                request.username, request.name, request.role, request.permissions));
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@RequestHeader(value = "X-Session-Token", required = false) String token,
                                      @PathVariable String id,
                                      @RequestBody Student changes) throws Exception {
        access.require(token);
        Student updated = students.update(id, changes);
        accounts.setEnabled(updated.username, updated.enabled);
        return students.view(updated);
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@RequestHeader(value = "X-Session-Token", required = false) String token,
                                      @PathVariable String id) throws Exception {
        access.require(token);
        Student user = require(id);
        if (!accounts.deleteUser(user.username)) throw new IllegalArgumentException("不能删除该用户");
        students.delete(id);
        return message("用户已删除");
    }

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
        if (student == null) throw new IllegalArgumentException("找不到用户");
        return student;
    }

    private Map<String, Object> message(String value) {
        return java.util.Collections.<String, Object>singletonMap("message", value);
    }

    public static class UserRequest {
        public String username;
        public String name;
        public String role;
        public List<String> permissions = new ArrayList<>();
    }
}
