package com.cloud.hub.common.core.domain;

import java.util.LinkedHashMap;

/**
 * 操作消息提醒与统一响应结果（参照 RuoYi 设计）
 * 继承 LinkedHashMap 保障与现有前端直接读 key 的无缝兼容。
 */
public class AjaxResult extends LinkedHashMap<String, Object> {
    private static final long serialVersionUID = 1L;

    /** 状态码 */
    public static final String CODE_TAG = "code";

    /** 返回内容 */
    public static final String MSG_TAG = "msg";

    /** 数据对象 */
    public static final String DATA_TAG = "data";

    public AjaxResult() {
    }

    public AjaxResult(int code, String msg) {
        super.put(CODE_TAG, code);
        super.put(MSG_TAG, msg);
    }

    public AjaxResult(int code, String msg, Object data) {
        super.put(CODE_TAG, code);
        super.put(MSG_TAG, msg);
        if (data != null) {
            super.put(DATA_TAG, data);
        }
    }

    /**
     * 返回成功消息
     */
    public static AjaxResult success() {
        return AjaxResult.success("success");
    }

    /**
     * 返回成功数据
     */
    public static AjaxResult success(Object data) {
        return AjaxResult.success("success", data);
    }

    /**
     * 返回成功消息
     */
    public static AjaxResult success(String msg) {
        return AjaxResult.success(msg, null);
    }

    /**
     * 返回成功消息与数据
     */
    public static AjaxResult success(String msg, Object data) {
        return new AjaxResult(0, msg, data);
    }

    /**
     * 快捷添加自定义键值返回成功
     */
    public static AjaxResult successData(String key, Object value) {
        AjaxResult result = new AjaxResult(0, "success");
        result.put(key, value);
        return result;
    }

    /**
     * 快捷构建单键值成功结果
     */
    public static AjaxResult of(String key, Object value) {
        return successData(key, value);
    }

    /**
     * 返回默认失败消息
     */
    public static AjaxResult error() {
        return AjaxResult.error("操作失败");
    }

    /**
     * 返回错误消息
     */
    public static AjaxResult error(String msg) {
        return AjaxResult.error(500, msg);
    }

    /**
     * 返回错误码与错误消息
     */
    public static AjaxResult error(int code, String msg) {
        return new AjaxResult(code, msg, null);
    }

    /**
     * 链式添加键值
     */
    @Override
    public AjaxResult put(String key, Object value) {
        super.put(key, value);
        return this;
    }

    public int getCode() {
        Object code = get(CODE_TAG);
        if (code instanceof Number) {
            return ((Number) code).intValue();
        }
        return 500;
    }

    public String getMsg() {
        Object msg = get(MSG_TAG);
        return msg != null ? String.valueOf(msg) : "";
    }

    public boolean isSuccess() {
        return getCode() == 0;
    }
}
