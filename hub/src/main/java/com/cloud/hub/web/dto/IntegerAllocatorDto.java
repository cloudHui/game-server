package com.cloud.hub.web.dto;

import java.util.List;

/**
 * 整数分配计算请求体（对齐 RuoYi 接口入参规范）
 */
public class IntegerAllocatorDto {
    private List<Object> knownValues;
    private Object totalAverage;
    private Object subAverage;
    private String sessionId;

    public List<Object> getKnownValues() {
        return knownValues;
    }

    public void setKnownValues(List<Object> knownValues) {
        this.knownValues = knownValues;
    }

    public Object getTotalAverage() {
        return totalAverage;
    }

    public void setTotalAverage(Object totalAverage) {
        this.totalAverage = totalAverage;
    }

    public Object getSubAverage() {
        return subAverage;
    }

    public void setSubAverage(Object subAverage) {
        this.subAverage = subAverage;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }
}
