package com.cloud.hub.framework.metrics;

import com.cloud.hub.lobby.Lobby;
import com.cloud.hub.lobby.db.InviteRepository;
import com.cloud.hub.lobby.db.UserRepository;
import com.cloud.hub.lobby.manager.UserManager;
import com.cloud.hub.lobby.manager.table.TableManager;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Hub 服务业务指标统一采集与 Prometheus 导出器。
 * <p>
 * 基于 Micrometer 门面，向 Prometheus 提供生产级业务指标：
 * <ul>
 *   <li>实时活跃桌子数与在线玩家数（Gauge 水位度量）</li>
 *   <li>房间创桌总数（Counter 计数）</li>
 *   <li>玩法单局结算总数（带 gameType 维度标签的 Counter 计数）</li>
 *   <li>账号登录与注册频次（带 status 标签的 Counter 计数）</li>
 * </ul>
 *
 * @author cloud
 */
@Component
public class HubMetrics {

    private static final Logger logger = LoggerFactory.getLogger(HubMetrics.class);
    private static volatile HubMetrics instance;

    private final MeterRegistry registry;
    private final Counter tableCreatedCounter;
    private final Counter tableDestroyedCounter;
    private final Counter tableLoopCounter;
    private final Counter registerSuccessCounter;
    private final ConcurrentMap<String, Counter> settleCounters = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Counter> loginCounters = new ConcurrentHashMap<>();

    public HubMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.tableCreatedCounter = Counter.builder("game_tables_created_total")
                .description("游戏牌桌创建累计总次数")
                .register(registry);
        this.tableDestroyedCounter = Counter.builder("game_tables_destroyed_total")
                .description("游戏牌桌销毁累计总次数")
                .register(registry);
        this.tableLoopCounter = Counter.builder("game_table_loops_total")
                .description("牌桌主循环调度累计轮次")
                .register(registry);
        this.registerSuccessCounter = Counter.builder("game_auth_registers_total")
                .description("账号注册成功累计总数")
                .register(registry);
        registerGauges();
    }

    @PostConstruct
    public void init() {
        instance = this;
        logger.info("HubMetrics 业务指标采集器初始化就绪, 已对接 Micrometer MeterRegistry");
    }

    /**
     * 获取全局业务指标单例（便于非 Spring 托管的牌桌域对象调用）。
     *
     * @return HubMetrics 实例
     */
    public static HubMetrics getInstance() {
        return instance;
    }

    /**
     * 注册实时动态水位仪表（Gauge）。
     */
    private void registerGauges() {
        // 绑定大厅活跃桌子数水位
        registry.gauge("game_tables_active", this, m -> {
            try {
                return TableManager.getInstance().getTableCount();
            } catch (Exception e) {
                return 0;
            }
        });

        // 绑定实时在线用户数水位
        registry.gauge("game_online_users", this, m -> {
            try {
                return UserManager.getInstance().getUserCount();
            } catch (Exception e) {
                return 0;
            }
        });
    }

    /**
     * 记录创桌事件。
     */
    public void recordTableCreated() {
        tableCreatedCounter.increment();
    }

    /**
     * 记录牌桌销毁与解散事件。
     */
    public void recordTableDestroyed() {
        tableDestroyedCounter.increment();
    }

    /**
     * 记录牌桌主循环调度轮次。
     */
    public void recordTableLoop() {
        tableLoopCounter.increment();
    }

    /**
     * 记录新账号注册事件。
     */
    public void recordRegisterSuccess() {
        registerSuccessCounter.increment();
    }

    /**
     * 记录用户登录事件。
     *
     * @param success 登录是否成功
     */
    public void recordLogin(boolean success) {
        String status = success ? "success" : "fail";
        loginCounters.computeIfAbsent(status, s -> Counter.builder("game_auth_logins_total")
                .description("用户登录尝试总次数")
                .tag("status", s)
                .register(registry)).increment();
    }

    /**
     * 记录单局游戏结算。
     *
     * @param gameType 玩法类型标识（如 ddz, pdk, mj, tractor 等）
     */
    public void recordRoundSettled(String gameType) {
        String type = gameType != null ? gameType : "unknown";
        settleCounters.computeIfAbsent(type, t -> Counter.builder("game_rounds_settled_total")
                .description("对局结算累计总局数")
                .tag("gameType", t)
                .register(registry)).increment();
    }

    /**
     * 获取管理后台专用的系统全局度量概览。
     *
     * @return 包含业务与 JVM 核心指标的字典数据
     */
    public java.util.Map<String, Object> getSystemOverview() {
        java.util.Map<String, Object> overview = new java.util.LinkedHashMap<>();
        overview.put("onlineUsers", UserManager.getInstance().getUserCount());
        overview.put("activeTables", TableManager.getInstance().getTableCount());
        overview.put("tablesCreatedTotal", (long) tableCreatedCounter.count());
        overview.put("tablesDestroyedTotal", (long) tableDestroyedCounter.count());
        overview.put("tableLoopsTotal", (long) tableLoopCounter.count());
        overview.put("registerSuccessTotal", (long) registerSuccessCounter.count());

        try {
            UserRepository userRepo = Lobby.getInstance().getUserRepository();
            if (userRepo != null) {
                overview.put("totalUsers", userRepo.countUsers());
            }
            InviteRepository inviteRepo = Lobby.getInstance().getInviteRepository();
            if (inviteRepo != null) {
                overview.put("totalInvites", inviteRepo.countInvites());
            }
        } catch (Exception e) {
            logger.debug("读取仓储统计度量略过: {}", e.getMessage());
        }

        java.util.Map<String, Long> settles = new java.util.LinkedHashMap<>();
        settleCounters.forEach((k, v) -> settles.put(k, (long) v.count()));
        overview.put("roundsSettled", settles);

        java.util.Map<String, Long> logins = new java.util.LinkedHashMap<>();
        loginCounters.forEach((k, v) -> logins.put(k, (long) v.count()));
        overview.put("logins", logins);

        overview.put("jvm", getJvmStats());
        return overview;
    }

    /**
     * 采集当前 JVM 运行时的基础性能指标。
     */
    private java.util.Map<String, Object> getJvmStats() {
        Runtime rt = Runtime.getRuntime();
        java.util.Map<String, Object> jvm = new java.util.LinkedHashMap<>();
        long totalMb = rt.totalMemory() / (1024 * 1024);
        long freeMb = rt.freeMemory() / (1024 * 1024);
        long maxMb = rt.maxMemory() / (1024 * 1024);
        jvm.put("usedMemoryMb", totalMb - freeMb);
        jvm.put("totalMemoryMb", totalMb);
        jvm.put("maxMemoryMb", maxMb);
        jvm.put("availableProcessors", rt.availableProcessors());
        jvm.put("threadCount", java.lang.management.ManagementFactory.getThreadMXBean().getThreadCount());
        jvm.put("uptimeSeconds", java.lang.management.ManagementFactory.getRuntimeMXBean().getUptime() / 1000);
        return jvm;
    }
}
