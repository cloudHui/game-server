package com.cloud.hub.common.core.domain;

import java.util.LinkedHashMap;

/**
 * 操作消息提醒与统一响应结果。
 * <p>
 * 继承自 {@link LinkedHashMap}，保障与现有前端在 JSON 序列化时直接读取
 * {@code code}、{@code msg}、{@code data} 等属性的平滑兼容，同时支持链式调用追加键值。
 * </p>
 *
 * @author cloud
 */
public class AjaxResult extends LinkedHashMap<String, Object> {
    private static final long serialVersionUID = 1L;

    /** 响应状态码键名 */
    public static final String CODE_TAG = "code";

    /** 提示消息键名 */
    public static final String MSG_TAG = "msg";

    /** 响应数据主体键名 */
    public static final String DATA_TAG = "data";

    /**
     * 初始化一个空的 AjaxResult 对象。
     */
    public AjaxResult() {
    }

    /**
     * 初始化带有状态码和提示信息的 AjaxResult。
     *
     * @param code 响应状态码
     * @param msg 提示消息
     */
    public AjaxResult(int code, String msg) {
        super.put(CODE_TAG, code);
        super.put(MSG_TAG, msg);
    }

    /**
     * 初始化带有状态码、提示信息和数据对象的 AjaxResult。
     *
     * @param code 响应状态码
     * @param msg 提示消息
     * @param data 数据载荷
     */
    public AjaxResult(int code, String msg, Object data) {
        super.put(CODE_TAG, code);
        super.put(MSG_TAG, msg);
        if (data != null) {
            super.put(DATA_TAG, data);
        }
    }

    /**
     * 返回默认成功消息（code=0, msg="success"）。
     *
     * @return 成功结果
     */
    public static AjaxResult success() {
        return AjaxResult.success("success");
    }

    /**
     * 返回带有数据的成功响应。
     *
     * @param data 数据载荷
     * @return 成功结果
     */
    public static AjaxResult success(Object data) {
        return AjaxResult.success("success", data);
    }

    /**
     * 返回带有自定义提示消息的成功响应。
     *
     * @param msg 提示消息
     * @return 成功结果
     */
    public static AjaxResult success(String msg) {
        return AjaxResult.success(msg, null);
    }

    /**
     * 返回带有自定义提示消息与数据的成功响应。
     *
     * @param msg 提示消息
     * @param data 数据载荷
     * @return 成功结果
     */
    public static AjaxResult success(String msg, Object data) {
        return new AjaxResult(0, msg, data);
    }

    /**
     * 快捷添加单键值对的成功响应。
     *
     * @param key 字段名
     * @param value 字段值
     * @return 成功结果
     */
    public static AjaxResult successData(String key, Object value) {
        AjaxResult result = new AjaxResult(0, "success");
        result.put(key, value);
        return result;
    }

    /**
     * 快捷构建单键值成功结果，等价于 {@link #successData(String, Object)}。
     *
     * @param key 字段名
     * @param value 字段值
     * @return 成功结果
     */
    public static AjaxResult of(String key, Object value) {
        return successData(key, value);
    }

    /**
     * 返回默认失败消息（code=500, msg="操作失败"）。
     *
     * @return 失败结果
     */
    public static AjaxResult error() {
        return AjaxResult.error("操作失败");
    }

    /**
     * 返回指定错误消息的失败响应。
     *
     * @param msg 错误消息
     * @return 失败结果
     */
    public static AjaxResult error(String msg) {
        return AjaxResult.error(500, msg);
    }

    /**
     * 返回指定错误码与错误消息的失败响应。
     *
     * @param code 业务错误码
     * @param msg 错误消息
     * @return 失败结果
     */
    public static AjaxResult error(int code, String msg) {
        return new AjaxResult(code, msg, null);
    }

    /**
     * 链式添加键值对。
     *
     * @param key 键
     * @param value 值
     * @return 当前对象实例
     */
    @Override
    public AjaxResult put(String key, Object value) {
        super.put(key, value);
        return this;
    }

    /**
     * 获取状态码。
     *
     * @return 状态码数值，不存在时返回 500
     */
    public int getCode() {
        Object code = get(CODE_TAG);
        if (code instanceof Number) {
            return ((Number) code).intValue();
        }
        return 500;
    }

    /**
     * 获取提示消息。
     *
     * @return 提示字符串
     */
    public String getMsg() {
        Object msg = get(MSG_TAG);
        return msg != null ? String.valueOf(msg) : "";
    }

    /**
     * 判断当前响应是否为成功状态（code == 0）。
     *
     * @return true 表示成功
     */
    public boolean isSuccess() {
        return getCode() == 0;
    }
}

