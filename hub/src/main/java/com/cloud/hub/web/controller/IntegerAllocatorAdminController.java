package com.cloud.hub.web.controller;

import com.cloud.hub.common.annotation.Log;
import com.cloud.hub.common.annotation.RequiresAdmin;
import com.cloud.hub.common.core.domain.AjaxResult;
import com.cloud.hub.common.enums.BusinessType;
import com.cloud.hub.web.arena.IntegerAllocator;
import com.cloud.hub.web.dto.IntegerAllocatorDto;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 管理员整数分配与均值反推计算控制器。
 * <p>
 * 用于在固定总期望均值或连续子区间均值约束下，自动反向推导并填充未知的整数数值序列。
 *
 * @author cloud
 */
@RestController
@RequestMapping("/api/admin/integer-allocator")
@RequiresAdmin
public class IntegerAllocatorAdminController {

    /**
     * 计算并反推满足均值约束的整数序列。
     *
     * @param body 计算请求参数
     * @return 成功返回填充后的数值列表与统计信息，失败返回校验信息
     */
    @PostMapping("/calculate")
    @Log(title = "整数分配计算", businessType = BusinessType.OTHER)
    public AjaxResult calculate(@RequestBody IntegerAllocatorDto body) {
        if (body == null) {
            return AjaxResult.error(400, "请求体不能为空");
        }
        List<Integer> knownValues = integers(body.getKnownValues());
        double totalAverage = decimal(body.getTotalAverage(), "总期望均值");
        Double subAverage = optionalDecimal(body.getSubAverage(), "连续 3 个值的期望均值");

        IntegerAllocator.Result allocation = IntegerAllocator.calculate(knownValues, totalAverage, subAverage);
        if (!allocation.isSuccess()) {
            return AjaxResult.error(422, allocation.getErrorMessage());
        }
        return success(allocation, totalAverage, subAverage);
    }

    private AjaxResult success(IntegerAllocator.Result allocation,
                               double totalAverage, Double subAverage) {
        AjaxResult result = AjaxResult.success();
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
            if (item == null || text(item).isEmpty()) {
                values.add(null);
                continue;
            }
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

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
