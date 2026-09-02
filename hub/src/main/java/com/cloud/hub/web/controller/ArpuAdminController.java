package com.cloud.hub.web.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.cloud.hub.web.arpu.ArpuAverageCalculator;
import com.cloud.hub.web.arpu.ArpuLookupService;
import com.cloud.hub.web.service.UserService;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/** 管理员 ARPU 查询接口。 */
@RestController
@RequestMapping("/api/admin/arpu")
public class ArpuAdminController {
    private final UserService users;
    private final ArpuLookupService lookup;

    public ArpuAdminController(UserService users, ArpuLookupService lookup) {
        this.users = users;
        this.lookup = lookup;
    }

    @PostMapping("/check")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> check(@RequestBody Map<String, Object> body) {
        if (body == null || !isAdmin(text(body.get("sessionId")))) {
            return completed(error(403, "需要管理员账号"));
        }
        String phoneNo = text(body.get("phoneNo"));
        if (!phoneNo.matches("1[3-9]\\d{9}")) {
            return completed(error(400, "请输入 11 位有效手机号"));
        }
        return lookup.checkAsync(phoneNo).thenApply(data -> ResponseEntity.ok(success(phoneNo, data)))
                .exceptionally(failure -> ResponseEntity.ok(error(502, "ARPU 查询失败：" + cause(failure))));
    }

    private CompletableFuture<ResponseEntity<Map<String, Object>>> completed(Map<String, Object> body) {
        return CompletableFuture.completedFuture(ResponseEntity.ok(body));
    }

    private String cause(Throwable error) {
        Throwable current = error;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? "上游无错误信息" : current.getMessage();
    }

    private Map<String, Object> success(String phoneNo, Map<String, Object> data) {
        Object monthly = data.get("arpu");
        List<?> values = monthly instanceof List ? (List<?>) monthly : Collections.emptyList();
        ArpuAverageCalculator.Result averages = ArpuAverageCalculator.calculate(values);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", 0);
        result.put("phoneNo", phoneNo);
        result.put("requestUrl", lookup.requestUrl(phoneNo));
        result.put("data", data);
        result.put("calculated", averageMap(averages));
        return result;
    }

    private Map<String, Object> averageMap(ArpuAverageCalculator.Result averages) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("average3", averages.getAverage3());
        result.put("available3", averages.getAvailable3());
        result.put("average6", averages.getAverage6());
        result.put("available6", averages.getAvailable6());
        return result;
    }

    private boolean isAdmin(String sessionId) {
        UserService.UserInfo user = users.getSession(sessionId);
        return user != null && user.isAdmin();
    }

    private Map<String, Object> error(int code, String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", code);
        result.put("msg", message);
        return result;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}

