package com.cloud.hub.web.controller;

import com.cloud.hub.common.annotation.Log;
import com.cloud.hub.common.annotation.RequiresAdmin;
import com.cloud.hub.common.core.domain.AjaxResult;
import com.cloud.hub.common.enums.BusinessType;
import com.cloud.hub.web.arpu.ArpuAverageCalculator;
import com.cloud.hub.web.arpu.ArpuLookupService;
import com.cloud.hub.web.dto.ArpuCheckDto;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * 管理员 ARPU（用户平均消费）查询控制器。
 * <p>
 * 支持根据手机号异步请求上游接口，并自动计算近 3 个月与近 6 个月的平均消费均值。
 *
 * @author cloud
 */
@RestController
@RequestMapping("/api/admin/arpu")
@RequiresAdmin
public class ArpuAdminController {

    private final ArpuLookupService lookup;

    public ArpuAdminController(ArpuLookupService lookup) {
        this.lookup = lookup;
    }

    /**
     * 根据手机号异步查询并核算历史 ARPU 数据。
     *
     * @param body 查询请求体（含手机号）
     * @return 包含近 3 个月、6 个月均值的异步结果
     */
    @PostMapping("/check")
    @Log(title = "ARPU查询", businessType = BusinessType.QUERY)
    public CompletableFuture<AjaxResult> check(@RequestBody @Valid ArpuCheckDto body) {
        String phoneNo = body.getPhoneNo();
        return lookup.checkAsync(phoneNo)
                .thenApply(data -> success(phoneNo, data))
                .exceptionally(failure -> AjaxResult.error(502, "ARPU 查询失败：" + cause(failure)));
    }

    private String cause(Throwable error) {
        Throwable current = error;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? "上游无错误信息" : current.getMessage();
    }

    private AjaxResult success(String phoneNo, Map<String, Object> data) {
        Object monthly = data.get("arpu");
        List<?> values = monthly instanceof List ? (List<?>) monthly : Collections.emptyList();
        ArpuAverageCalculator.Result averages = ArpuAverageCalculator.calculate(values);

        AjaxResult result = AjaxResult.success();
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
}
