package com.cloud.hub.lobby.manager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 大厅统一在线用户管理器。
 * <p>
 * 基于高并发 {@link ConcurrentHashMap} 维护全局在线用户实例，
 * 负责登录入表、跨网关换会话更新、登出清理以及指标度量上报。
 * </p>
 *
 * @author cloud
 */
public class UserManager {

    private static final Logger logger = LoggerFactory.getLogger(UserManager.class);
    private static final UserManager instance = new UserManager();

    /** 在线哈希表默认容量 */
    private static final int MAX_CAPACITY = 4096;
    /** 用户 ID 到大厅在线 User 映射表 */
    private final Map<Long, User> users;

    private UserManager() {
        users = new ConcurrentHashMap<>(MAX_CAPACITY);
        logger.info("用户管理器初始化完成,最大容量: {}", MAX_CAPACITY);
    }

    /**
     * 获取用户管理器全局单例。
     *
     * @return 单例实例
     */
    public static UserManager getInstance() {
        return instance;
    }

    /**
     * 根据 64 位长整型用户 ID 查询在线用户。
     *
     * @param userId 用户 ID
     * @return 在线用户对象，不在线时返回 null
     */
    public User getUser(long userId) {
        return users.get(userId);
    }

    /**
     * 根据 32 位整型用户 ID 查询在线用户。
     *
     * @param userId 用户 ID
     * @return 在线用户对象，不在线时返回 null
     */
    public User getUser(int userId) {
        return users.get((long) userId);
    }

    /**
     * 移除下线用户并销毁其关联桌面引用。
     *
     * @param userId 用户 ID
     */
    public void removeUser(long userId) {
        User removed = users.remove(userId);
        if (removed != null) {
            removed.destroy();
            logger.info("移除用户, userId: {}", userId);
        }
    }

    /**
     * 添加或覆盖更新在线用户（同 userId 更换登录网关会话）。
     *
     * @param user 新用户实例
     * @return 保存后的用户实例
     */
    public User putOrUpdate(User user) {
        if (user == null) {
            return null;
        }
        User existing = users.put(user.getUserId(), user);
        if (existing != null) {
            logger.info("更新在线用户会话, userId: {}", user.getUserId());
        } else {
            logger.debug("添加在线用户, userId: {}", user.getUserId());
        }
        return user;
    }

    /**
     * 尝试新增在线用户（若已在线则拒绝）。
     *
     * @param user 用户实例
     * @return true 表示成功加入，false 表示已有相同 ID 用户在线
     */
    public boolean addUser(User user) {
        if (user == null) {
            return false;
        }
        User existing = users.putIfAbsent(user.getUserId(), user);
        if (existing != null) {
            logger.warn("用户已在线, userId: {}", user.getUserId());
            return false;
        }
        return true;
    }

    /**
     * 获取当前总在线用户数。
     *
     * @return 在线玩家总数
     */
    public int getUserCount() {
        return users.size();
    }

    /**
     * 获取全部在线用户 Map 原型引用。
     *
     * @return 用户哈希表引用
     */
    public Map<Long, User> getAllUsers() {
        return users;
    }
}

