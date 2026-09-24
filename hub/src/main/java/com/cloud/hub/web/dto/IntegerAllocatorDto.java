package com.cloud.hub.web.dto;

import java.util.List;

/**
 * 整数分配算法试算请求传输对象。
 *
 * @author cloud
 */
public class IntegerAllocatorDto {

    /** 已知固定数值列表 */
    private List<Object> knownValues;

    /** 目标总平均值 */
    private Object totalAverage;

    /** 子集合目标平均值 */
    private Object subAverage;

    /** 可选的会话标识 */
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
