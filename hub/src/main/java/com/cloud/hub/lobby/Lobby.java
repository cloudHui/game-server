package com.cloud.hub.lobby;

import com.cloud.hub.lobby.db.InviteRepository;
import com.cloud.hub.lobby.db.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import threadtutil.thread.ExecutorPool;
import threadtutil.timer.Runner;
import threadtutil.timer.Timer;

import java.util.UUID;

/**
 * Lobby 大厅服务核心上下文与统一管理单例。
 * <p>
 * 聚合大厅异步任务线程池 {@link ExecutorPool}、内置定时器 {@link Timer}、
 * 账号仓储 {@link UserRepository} 以及邀请码仓储 {@link InviteRepository}。
 * </p>
 *
 * @author cloud
 */
public class Lobby {

    private static final Logger logger = LoggerFactory.getLogger(Lobby.class);
    private static final Lobby instance = new Lobby();

    /** 核心业务异步线程池 */
    private final ExecutorPool executorPool;
    /** 大厅高精度时间轮/定时器 */
    private final Timer timer;

    /** 当前大厅服务节点唯一 ID */
    private int serverId;
    /** 用户账号仓储 */
    private UserRepository userRepository;
    /** 注册邀请码仓储 */
    private InviteRepository inviteRepository;

    private Lobby() {
        executorPool = new ExecutorPool("Lobby");
        timer = new Timer().setRunners(executorPool);
    }

    /**
     * 获取大厅单例实例。
     *
     * @return Lobby 实例
     */
    public static Lobby getInstance() {
        return instance;
    }

    public int getServerId() {
        return serverId;
    }

    public void setServerId(int serverId) {
        this.serverId = serverId;
    }

    public UserRepository getUserRepository() {
        return userRepository;
    }

    public void setUserRepository(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public InviteRepository getInviteRepository() {
        return inviteRepository;
    }

    public void setInviteRepository(InviteRepository inviteRepository) {
        this.inviteRepository = inviteRepository;
    }

    /**
     * 投递异步任务至大厅业务线程池。
     *
     * @param task 待执行任务闭包
     */
    public void execute(Runnable task) {
        executorPool.execute(task);
    }

    /**
     * 注册大厅定时轮询任务。
     *
     * @param delay 首次执行延迟毫秒
     * @param interval 循环间隔毫秒
     * @param count 执行次数
     * @param runner 执行器
     * @param param 附带参数
     * @param <T> 参数泛型
     */
    public <T> void registerTimer(long delay, long interval, int count, Runner<T> runner, T param) {
        timer.register(delay, interval, count, runner, param);
    }

    /**
     * 生成系统全局唯一的会话 Token。
     *
     * @return 32 位 UUID 令牌字符串
     */
    public static String newToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}


