package com.cloud.hub.web.learning.model;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 每日系统使用统计数据实体。
 *
 * @author cloud
 */
public class DailyUsage {
    /** 统计日期 (yyyy-MM-dd) */
    public String date;
    /** 总登录次数 */
    public int loginCount;
    /** 新注册用户数 */
    public int newUsers;
    /** 最高同时在线峰值 */
    public int peakOnline;
    /** 累计在线时长（秒） */
    public long totalSeconds;
    /** 前端异常上报计数 */
    public int frontendErrors;
    /** 后端异常计数 */
    public int backendErrors;
    /** 活跃用户 ID 集合 */
    public Set<String> activeUserIds = new LinkedHashSet<>();
    /** 每个用户的登录次数分布 */
    public Map<String, Integer> userLogins = new LinkedHashMap<>();
    /** 每个用户的在线时长分布（秒） */
    public Map<String, Long> userSeconds = new LinkedHashMap<>();
    /** 页面停留时长统计（秒） */
    public Map<String, Long> pageSeconds = new LinkedHashMap<>();
    /** 功能模块使用时长统计（秒） */
    public Map<String, Long> featureSeconds = new LinkedHashMap<>();
    /** 页面访问 PV 计数 */
    public Map<String, Integer> pageViews = new LinkedHashMap<>();
    /** 功能启动点击次数 */
    public Map<String, Integer> featureStarts = new LinkedHashMap<>();
    /** 客户端终端设备类型分布 */
    public Map<String, Integer> devices = new LinkedHashMap<>();
}
