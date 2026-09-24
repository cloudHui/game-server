package com.cloud.hub.web.learning.controller.admin;

import com.cloud.hub.web.learning.model.Student;
import com.cloud.hub.web.learning.service.AuthService;
import org.springframework.stereotype.Component;

/**
 * 学习中心管理员鉴权守卫辅助组件。
 *
 * @author cloud
 */
@Component
public class LearningAdminAccess {

    private final AuthService auth;

    public LearningAdminAccess(AuthService auth) {
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
}
