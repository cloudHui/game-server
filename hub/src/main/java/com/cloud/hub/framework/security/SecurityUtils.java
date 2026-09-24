package com.cloud.hub.framework.security;

import com.cloud.hub.common.exception.ServiceException;

/**
 * 安全上下文与当前会话用户工具类。
 * <p>
 * 基于 {@link ThreadLocal} 维护当前 HTTP 请求线程绑定的 {@link LoginUser} 凭据，
 * 提供获取用户 ID、判断管理员身份及权限断言等便捷静态方法。
 * </p>
 *
 * @author cloud
 */
public class SecurityUtils {

    /** 线程本地登录用户上下文容器 */
    private static final ThreadLocal<LoginUser> CONTEXT_HOLDER = new ThreadLocal<>();

    private SecurityUtils() {
    }

    /**
     * 设置当前线程关联的登录用户信息。
     *
     * @param user 登录用户对象
     */
    public static void setLoginUser(LoginUser user) {
        CONTEXT_HOLDER.set(user);
    }

    /**
     * 获取当前线程关联的登录用户（未登录时返回 null）。
     *
     * @return 登录用户对象，可能为空
     */
    public static LoginUser getLoginUser() {
        return CONTEXT_HOLDER.get();
    }

    /**
     * 清除当前线程上下文，防止 Tomcat/Jetty 线程池复用引发的用户串号污染。
     */
    public static void clear() {
        CONTEXT_HOLDER.remove();
    }

    /**
     * 获取当前登录用户，若未登录则抛出 401 业务异常。
     *
     * @return 必须存在的登录用户对象
     * @throws ServiceException 当未登录时抛出
     */
    public static LoginUser getRequiredUser() {
        LoginUser user = getLoginUser();
        if (user == null) {
            throw new ServiceException(401, "请先登录");
        }
        return user;
    }

    /**
     * 获取当前登录用户 ID。
     *
     * @return 用户 ID
     */
    public static int getUserId() {
        return getRequiredUser().getUserId();
    }

    /**
     * 获取当前登录用户名。
     *
     * @return 用户名
     */
    public static String getUsername() {
        return getRequiredUser().getUsername();
    }

    /**
     * 检查当前用户是否具备系统管理员权限。
     *
     * @return true 表示为管理员，未登录或普通用户返回 false
     */
    public static boolean isAdmin() {
        LoginUser user = getLoginUser();
        return user != null && user.isAdmin();
    }

    /**
     * 校验当前用户必须为管理员，若非管理员则抛出 403 业务异常拒绝访问。
     *
     * @throws ServiceException 当无管理员权限时抛出
     */
    public static void requireAdmin() {
        LoginUser user = getRequiredUser();
        if (!user.isAdmin()) {
            throw new ServiceException(403, "需要管理员账号");
        }
    }
}

