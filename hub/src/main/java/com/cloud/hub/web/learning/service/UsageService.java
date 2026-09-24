package com.cloud.hub.web.learning.service;

import com.cloud.hub.web.learning.model.DailyUsage;
import com.cloud.hub.web.learning.model.OnlineState;
import com.cloud.hub.web.learning.model.Student;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 用户在线心跳与平台日活使用量监控服务。
 * <p>
 * 追踪在线人数、心跳累计页面停留时长、功能点击启动次数、错误上报及定时写入 SQLite WAL 存储。
 *
 * @author cloud
 */
@Service
public class UsageService {

    private final JsonFileStore store;
    private final Map<String, OnlineState> online = new ConcurrentHashMap<>();
    private DailyUsage daily;

    public UsageService(JsonFileStore store) {
        this.store = store;
    }

    /**
     * 记录用户登录事件并激活在线状态。
     *
     * @param user    登录学生
     * @param device  设备/浏览器类型
     * @param created 是否为新创建账号
     * @throws Exception 存储异常
     */
    public synchronized void login(Student user, String device, boolean created) throws Exception {
        ensureDay();
        daily.loginCount++;
        if (created) {
            daily.newUsers++;
        }
        daily.activeUserIds.add(user.id);
        increment(daily.userLogins, user.id, 1);
        increment(daily.devices, clean(device, "未知设备"), 1);

        OnlineState state = new OnlineState();
        state.userId = user.id;
        state.username = user.username;
        state.name = user.name;
        state.page = "登录";
        state.feature = "";
        state.device = clean(device, "未知设备");
        state.loginAt = LocalDateTime.now();
        state.lastSeenAt = state.loginAt;
        online.put(user.id, state);

        updatePeak();
        flush();
    }

    /**
     * 处理客户端定时心跳并累加页面与功能使用时长。
     *
     * @param user    心跳用户
     * @param page    当前页面路由
     * @param feature 当前功能代码
     * @param device  当前设备
     * @throws Exception 存储异常
     */
    public synchronized void heartbeat(Student user, String page, String feature, String device) throws Exception {
        ensureDay();
        LocalDateTime now = LocalDateTime.now();
        OnlineState previous = online.get(user.id);

        if (previous == null) {
            registerNewOnline(user, page, feature, device, now);
        } else {
            updateExistingOnline(user, previous, page, feature, device, now);
        }
        updatePeak();
    }

    private void registerNewOnline(Student user, String page, String feature, String device, LocalDateTime now) {
        OnlineState state = new OnlineState();
        state.userId = user.id;
        state.username = user.username;
        state.name = user.name;
        state.loginAt = now;
        state.lastSeenAt = now;
        state.page = clean(page, "首页");
        state.feature = clean(feature, "");
        state.device = clean(device, "未知设备");
        online.put(user.id, state);
        daily.activeUserIds.add(user.id);
        increment(daily.pageViews, state.page, 1);
    }

    private void updateExistingOnline(Student user, OnlineState prev, String page, String feature, String device, LocalDateTime now) {
        long seconds = Math.min(30, Math.max(0, Duration.between(prev.lastSeenAt, now).getSeconds()));
        daily.totalSeconds += seconds;
        increment(daily.userSeconds, user.id, seconds);
        increment(daily.pageSeconds, clean(prev.page, "首页"), seconds);

        if (prev.feature != null && !prev.feature.isEmpty()) {
            increment(daily.featureSeconds, prev.feature, seconds);
        }
        String nextPage = clean(page, "首页");
        String nextFeature = clean(feature, "");
        if (!nextPage.equals(prev.page)) {
            increment(daily.pageViews, nextPage, 1);
        }
        if (!nextFeature.isEmpty() && !nextFeature.equals(prev.feature)) {
            increment(daily.featureStarts, nextFeature, 1);
        }
        prev.page = nextPage;
        prev.feature = nextFeature;
        prev.device = clean(device, prev.device);
        prev.lastSeenAt = now;
    }

    /**
     * 用户主动登出或断开连接。
     *
     * @param userId 用户 ID
     */
    public synchronized void logout(String userId) {
        online.remove(userId);
    }

    /**
     * 前端异常事件计数累加。
     */
    public synchronized void frontendError() throws Exception {
        ensureDay();
        daily.frontendErrors++;
    }

    /**
     * 后端异常事件计数累加。
     */
    public synchronized void backendError() {
        try {
            ensureDay();
            daily.backendErrors++;
        } catch (Exception ignored) {
        }
    }

    /**
     * 获取当前处于活跃在线状态的用户列表（过滤超时连接）。
     *
     * @return 按最近活跃时间倒序排列的列表
     */
    public synchronized List<OnlineState> onlineUsers() {
        prune();
        List<OnlineState> result = new ArrayList<>(online.values());
        result.sort(Comparator.comparing((OnlineState state) -> state.lastSeenAt).reversed());
        return result;
    }

    /**
     * 获取今日系统使用汇总快照。
     */
    public synchronized DailyUsage today() throws Exception {
        ensureDay();
        return daily;
    }

    /**
     * 查询历史特定日期的使用量报告。
     */
    public synchronized DailyUsage day(LocalDate date) throws Exception {
        DailyUsage value = store.read(store.path("usage", date.toString()), DailyUsage.class);
        if (value == null) {
            value = new DailyUsage();
            value.date = date.toString();
        }
        return value;
    }

    /**
     * 每 60 秒定时清理过期连接并持久化当天指标。
     */
    @Scheduled(fixedDelay = 60000)
    public synchronized void scheduledFlush() {
        try {
            prune();
            ensureDay();
            flush();
        } catch (Exception ignored) {
        }
    }

    /**
     * 优雅停机前强制刷盘。
     */
    @PreDestroy
    public synchronized void shutdown() {
        try {
            if (daily != null) {
                flush();
            }
        } catch (Exception ignored) {
        }
    }

    private void ensureDay() throws Exception {
        String today = LocalDate.now().toString();
        if (daily == null || !today.equals(daily.date)) {
            if (daily != null) {
                flush();
            }
            daily = store.read(store.path("usage", today), DailyUsage.class);
            if (daily == null) {
                daily = new DailyUsage();
                daily.date = today;
            }
        }
    }

    private void flush() throws Exception {
        if (daily != null) {
            store.write(store.path("usage", daily.date), daily);
        }
    }

    private void updatePeak() {
        int current = online.size();
        if (current > daily.peakOnline) {
            daily.peakOnline = current;
        }
    }

    private void prune() {
        LocalDateTime cutoff = LocalDateTime.now().minusSeconds(90);
        online.entrySet().removeIf(entry -> entry.getValue().lastSeenAt.isBefore(cutoff));
    }

    private <K> void increment(Map<K, Long> map, K key, long delta) {
        map.put(key, map.getOrDefault(key, 0L) + delta);
    }

    private <K> void increment(Map<K, Integer> map, K key, int delta) {
        map.put(key, map.getOrDefault(key, 0) + delta);
    }

    private String clean(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }
}
