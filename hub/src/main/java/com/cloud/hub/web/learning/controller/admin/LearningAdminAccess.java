package com.cloud.hub.web.learning.controller.admin;

import org.springframework.stereotype.Component;
import com.cloud.hub.web.learning.model.Student;
import com.cloud.hub.web.learning.service.AuthService;

@Component
public class LearningAdminAccess {
    private final AuthService auth;

    public LearningAdminAccess(AuthService auth) {
        this.auth = auth;
    }

    public Student require(String token) throws Exception {
        return auth.requireAdmin(token);
    }
}
