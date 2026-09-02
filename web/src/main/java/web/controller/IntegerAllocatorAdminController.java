package web.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import web.arena.IntegerAllocator;
import web.service.UserService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 管理员整数分配计算接口。 */
@RestController
@RequestMapping("/api/admin/integer-allocator")
public class IntegerAllocatorAdminController {
    private final UserService users;

    public IntegerAllocatorAdminController(UserService users) {
        this.users = users;
    }

    @PostMapping("/calculate")
    public ResponseEntity<Map<String, Object>> calculate(@RequestBody Map<String, Object> body) {
        if (body == null || !isAdmin(text(body.get("sessionId")))) {
            return ResponseEntity.ok(error(403, "需要管理员账号"));
        }
        try {
            List<Integer> knownValues = integers(body.get("knownValues"));
            double totalAverage = decimal(body.get("totalAverage"), "总期望均值");
            Double subAverage = optionalDecimal(body.get("subAverage"), "连续 3 个值的期望均值");
            IntegerAllocator.Result allocation = IntegerAllocator.calculate(
                    knownValues, totalAverage, subAverage);
            if (!allocation.isSuccess()) {
                return ResponseEntity.ok(error(422, allocation.getErrorMessage()));
            }
            return ResponseEntity.ok(success(allocation, totalAverage, subAverage));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.ok(error(400, e.getMessage()));
        }
    }

    private Map<String, Object> success(IntegerAllocator.Result allocation,
                                         double totalAverage, Double subAverage) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", 0);
        result.put("values", asList(allocation.getValues()));
        result.put("knownCount", allocation.getKnownCount());
        result.put("totalTargetSum", allocation.getTotalTargetSum());
        result.put("totalSum", allocation.getTotalSum());
        result.put("actualTotalAverage", allocation.getTotalSum() / 6.0);
        if (allocation.hasSubConstraint()) {
            result.put("subStartIndex", allocation.getSubStartIndex());
            result.put("subTargetSum", allocation.getSubTargetSum());
            result.put("subSum", allocation.getSubSum());
            result.put("actualSubAverage", allocation.getSubSum() / 3.0);
            result.put("requestedSubAverage", subAverage);
        }
        result.put("requestedTotalAverage", totalAverage);
        return result;
    }

    private List<Integer> integers(Object raw) {
        if (raw == null) {
            return Collections.emptyList();
        }
        if (!(raw instanceof List)) {
            throw new IllegalArgumentException("已知值必须是整数列表");
        }
        List<Integer> values = new ArrayList<>();
        for (Object item : (List<?>) raw) {
            double value = decimal(item, "已知值");
            if (value != Math.rint(value) || value > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("已知值必须是整数");
            }
            values.add((int) value);
        }
        return values;
    }

    private Double optionalDecimal(Object raw, String label) {
        return raw == null || text(raw).isEmpty() ? null : decimal(raw, label);
    }

    private double decimal(Object raw, String label) {
        if (raw == null || text(raw).isEmpty()) {
            throw new IllegalArgumentException(label + "不能为空");
        }
        try {
            return Double.parseDouble(text(raw));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(label + "必须是有效数字");
        }
    }

    private List<Integer> asList(int[] values) {
        List<Integer> result = new ArrayList<>();
        for (int value : values) {
            result.add(value);
        }
        return result;
    }

    private boolean isAdmin(String sessionId) {
        UserService.UserInfo user = users.getSession(sessionId);
        return user != null && user.isAdmin();
    }

    private Map<String, Object> error(int code, String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", code);
        result.put("msg", message == null ? "请求参数不正确" : message);
        return result;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
