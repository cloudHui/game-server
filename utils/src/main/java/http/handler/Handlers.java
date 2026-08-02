package http.handler;

/**
 * 处理器接口
 *
 * @author cloud
 * @version 1.0
 * @date 2026-05-03
 * @since 1.0
 */
public interface Handlers {
    /**
     * 获取处理器
     *
     * @param path 路径
     * @return 处理器
     */
    Handler getHandler(String path);
}
