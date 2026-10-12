package http.handler;

import http.Linker;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

/**
 * HTTP 路径请求处理器接口。
 *
 * @param <T> 请求消息体解析类型
 */
public interface HttpHandler<T> {
    String path();

    T parser(String msg);

    /**
     * HTTP 请求处理业务方法
     *
     * @param linker 链接抽象
     * @param t      请求参数
     * @return 是否保持长连接
     */
    boolean handler(Linker linker, T t);

    default T paras(String msg, T t) {
        String deData = null;
        try {
            deData = URLDecoder.decode(msg, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        assert deData != null;
        String[] keyValues = deData.split("&");
        for (String kv : keyValues) {
            String[] keyValue = kv.split("=");
            ReflectUtils.setValue(t, keyValue[0], keyValue[1]);
        }
        return t;
    }
}
