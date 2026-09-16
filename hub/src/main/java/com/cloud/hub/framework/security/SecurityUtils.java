package com.cloud.hub.framework.security;

import com.cloud.hub.common.exception.ServiceException;

/**
 * 安全服务与当前登录线程上下文工具类（参照 RuoYi 设计）
 */
public class SecurityUtils {
    private static final ThreadLocal<LoginUser> CONTEXT_HOLDER = new ThreadLocal<>();

    /**
     * 设置当前线程登录用户
     */
    public static void setLoginUser(LoginUser user) {
        CONTEXT_HOLDER.set(user);
    }

    /**
     * 获取当前线程登录用户（可能为 null）
     */
    public static LoginUser getLoginUser() {
        return CONTEXT_HOLDER.get();
    }

    /**
     * 清除当前线程上下文，防止线程池复用污染
     */
    public static void clear() {
        CONTEXT_HOLDER.remove();
    }

    /**
     * 获取当前登录用户，若未登录抛出 401 业务异常
     */
    public static LoginUser getRequiredUser() {
        LoginUser user = getLoginUser();
        if (user == null) {
            throw new ServiceException(401, "请先登录");
        }
        return user;
    }

    /**
     * 获取当前登录用户 ID
     */
    public static int getUserId() {
        return getRequiredUser().getUserId();
    }

    /**
     * 获取当前登录用户名
     */
    public static String getUsername() {
        return getRequiredUser().getUsername();
    }

    /**
     * 当前登录用户是否为管理员
     */
    public static boolean isAdmin() {
        LoginUser user = getLoginUser();
        return user != null && user.isAdmin();
    }

    /**
     * 校验当前用户必须为管理员，否则抛出 403 业务异常
     */
    public static void requireAdmin() {
        LoginUser user = getRequiredUser();
        if (!user.isAdmin()) {
            throw new ServiceException(403, "需要管理员账号");
        }
    }
}
