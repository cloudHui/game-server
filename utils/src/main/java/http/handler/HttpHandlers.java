package http.handler;

/**
 * HTTP 处理器注册与查找接口。
 *
 * @author cloud
 */
public interface HttpHandlers {

    /**
     * 获取指定路径的 HTTP 处理器
     *
     * @param path 请求路径
     * @return 匹配的处理器
     */
    HttpHandler<?> getHandler(String path);
}
