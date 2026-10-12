package com.cloud.hub.web.learning.controller.admin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cloud.hub.web.account.AccountService;
import com.cloud.hub.web.learning.model.Student;
import com.cloud.hub.web.learning.service.StudentService;

/**
 * 学习中心学员账号与权限管理控制器。
 * <p>
 * 提供学生档案清单查询、学员创建、信息与权限变更、档案移除以及登录密码重置支持。
 *
 * @author cloud
 */
@RestController
@RequestMapping("/api/learning/admin/users")
public class UserAdminController {

    /** 学习中心鉴权守卫 */
    private final AdminAccess access;
    /** 学员档案维护服务 */
    private final StudentService students;
    /** 账号与权限底座服务 */
    private final AccountService accounts;

    public UserAdminController(AdminAccess access,
                               StudentService students,
                               AccountService accounts) {
        this.access = access;
        this.students = students;
        this.accounts = accounts;
    }

    /**
     * 查询学习中心全部学员档案列表。
     *
     * @param token 会话凭证 Header
     * @return 学员档案脱敏展示键值对列表
     * @throws Exception 鉴权未通过或查询异常
     */
    @GetMapping
    public List<Map<String, Object>> list(@RequestHeader(value = "X-Session-Token", required = false) String token)
            throws Exception {
        access.require(token);
        return students.list().stream().map(students::view).collect(Collectors.toList());
    }

    /**
     * 管理员开通新建学员账号。
     *
     * @param token   会话凭证 Header
     * @param request 学员开通请求体
     * @return 新增学员档案脱敏展示键值对
     * @throws Exception 鉴权未通过或创建异常
     */
    @PostMapping
    public Map<String, Object> create(@RequestHeader(value = "X-Session-Token", required = false) String token,
                                      @RequestBody UserRequest request) throws Exception {
        access.require(token);
        // 先在全局账号服务建立托管凭据，失败则阻断
        if (!accounts.createManagedUser(request.username, request.name).isPresent()) {
            throw new IllegalArgumentException("用户名已存在或创建失败");
        }
        return students.view(students.create(request.username, request.name, request.role, request.permissions));
    }

    /**
     * 更新学员姓名、学段、角色与功能权限。
     *
     * @param token   会话凭证 Header
     * @param id      学员唯一档案 ID
     * @param changes 变更属性实体
     * @return 更新完成后的学员档案脱敏展示键值对
     * @throws Exception 鉴权未通过或更新异常
     */
    @PutMapping("/{id}")
    public Map<String, Object> update(@RequestHeader(value = "X-Session-Token", required = false) String token,
                                      @PathVariable String id,
                                      @RequestBody Student changes) throws Exception {
        access.require(token);
        Student updated = students.update(id, changes);
        // 同步启停状态至全局账号底座
        accounts.setEnabled(updated.username, updated.enabled);
        return students.view(updated);
    }

    /**
     * 删除指定学员档案。
     *
     * @param token 会话凭证 Header
     * @param id    学员唯一档案 ID
     * @return 操作成功提示
     * @throws Exception 鉴权未通过或删除异常
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
        return AdminAccess.message("用户已删除");
    }

    /**
     * 管理员重置学员登录密码（默认恢复为 123456）。
     *
     * @param token 会话凭证 Header
     * @param id    学员唯一档案 ID
     * @return 操作成功提示
     * @throws Exception 鉴权未通过或重置异常
     */
    @PostMapping("/{id}/reset-password")
    public Map<String, Object> resetPassword(@RequestHeader(value = "X-Session-Token", required = false) String token,
                                             @PathVariable String id) throws Exception {
        access.require(token);
        if (!accounts.resetPassword(require(id).username)) {
            throw new IllegalArgumentException("密码重置失败");
        }
        return AdminAccess.message("密码已重置为123456");
    }

    /**
     * 检索指定学员档案，不存在时抛出语义化异常。
     *
     * @param id 学员档案唯一 ID
     * @return 学员档案实体
     * @throws Exception 数据库查询异常或找不到用户异常
     */
    private Student require(String id) throws Exception {
        Student student = students.get(id);
        if (student == null) {
            throw new IllegalArgumentException("找不到用户");
        }
        return student;
    }

    /**
     * 学员账号开通请求载荷 DTO。
     */
    public static class UserRequest {
        /** 用户名登录标识 */
        public String username;
        /** 真实姓名/显示昵称 */
        public String name;
        /** 角色定位 (student/parent/admin) */
        public String role;
        /** 分配的功能模块权限列表 */
        public List<String> permissions = new ArrayList<>();
    }
}
