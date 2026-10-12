package com.cloud.hub.web.learning.controller.admin;

import java.util.Collections;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.cloud.hub.web.learning.model.Student;
import com.cloud.hub.web.learning.service.AuthService;

/**
 * 学习中心管理员鉴权守卫与响应辅助组件。
 *
 * @author cloud
 */
@Component
public class AdminAccess {

    /** 学习中心认证服务引用 */
    private final AuthService auth;

    public AdminAccess(AuthService auth) {
        this.auth = auth;
    }

    /**
     * 强校验传入 Token 对应的用户必须具有管理员权限。
     *
     * @param token 会话凭证
     * @return 管理员学生实体
     * @throws Exception 鉴权失败异常
     */
    public Student require(String token) throws Exception {
        return auth.requireAdmin(token);
    }

    /**
     * 构造统一的单行提示信息响应结构体。
     *
     * @param value 提示文本
     * @return 包含 message 属性的响应 Map
     */
    public static Map<String, Object> message(String value) {
        return Collections.singletonMap("message", value);
    }
}
