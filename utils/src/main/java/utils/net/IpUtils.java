package utils.net;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.system.ExecCommand;

import java.net.*;
import java.util.Enumeration;
import java.util.List;

/**
 * 本地与公网 IP 地址解析工具类。
 * <p>
 * 提供本机局域网 IPv4 探测、主机名获取、回环地址校验与外网出口 IP 安全检测。
 * </p>
 *
 * @author cloud
 */
public class IpUtils {

    /** 统一日志记录器 */
    private static final Logger LOGGER = LoggerFactory.getLogger(IpUtils.class);

    public IpUtils() {
    }

    /**
     * 获取外网 IP 地址（通过安全轻量终端命令探测，带超时与容错）。
     *
     * @return 外网 IP 地址字符串，若未检测到或探测失败则返回空串
     */
    public static String getOutIp() {
        LOGGER.debug("[getOutIp] 尝试通过外部公共探针探测外网出口 IP");
        try {
            // 使用设置了 5 秒超时的安全命令执行，避免挂死
            List<String> results = ExecCommand.exeCommand("curl -s --connect-timeout 3 icanhazip.com", 5);
            if (results.isEmpty() || results.get(0).trim().isEmpty()) {
                LOGGER.warn("[getOutIp] 外部公共探针未返回有效 IP 地址");
                return "";
            }
            String ip = results.get(0).trim();
            LOGGER.info("[getOutIp] 成功解析外网出口 IP: {}", ip);
            return ip;
        } catch (Exception e) {
            LOGGER.error("[getOutIp] 探测外网 IP 发生异常", e);
            return "";
        }
    }

    /**
     * 获取默认回环 IP。
     *
     * @return "127.0.0.1"
     */
    public static String getLocalIP() {
        LOGGER.trace("[getLocalIP] 返回默认回环 IP 127.0.0.1");
        return "127.0.0.1";
    }

    /**
     * 遍历本机所有物理网卡，获取第一个活动的非回环 IPv4 地址。
     *
     * @return 本机内网 IPv4 地址字符串，若无可用网卡则返回 null
     */
    public static String getIP() {
        LOGGER.debug("[getIP] 开始探测本机有效活动网卡 IPv4 地址");
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces == null) {
                LOGGER.warn("[getIP] 未获取到任何 NetworkInterface");
                return null;
            }
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                if (networkInterface.isLoopback() || !networkInterface.isUp()) {
                    continue;
                }
                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (address instanceof Inet4Address && !address.isLoopbackAddress()) {
                        String hostAddress = address.getHostAddress();
                        LOGGER.info("[getIP] 探测到本机活动 IPv4 地址: iface={}, ip={}",
                                networkInterface.getDisplayName(), hostAddress);
                        return hostAddress;
                    }
                }
            }
        } catch (SocketException e) {
            LOGGER.error("[getIP] 获取网卡配置异常", e);
        }
        LOGGER.warn("[getIP] 未能探测到任何有效的非回环 IPv4 地址");
        return null;
    }

    /**
     * 获取本机非 127.0.0.1 的局域网内网 IP。
     *
     * @return 局域网 IP 字符串，若未检测到则返回 null
     */
    public static String getLocal() {
        LOGGER.debug("[getLocal] 扫描非 127.0.0.1 本地局域网地址");
        try {
            Enumeration<NetworkInterface> allNetInterfaces = NetworkInterface.getNetworkInterfaces();
            if (allNetInterfaces == null) {
                return null;
            }
            while (allNetInterfaces.hasMoreElements()) {
                NetworkInterface netInterface = allNetInterfaces.nextElement();
                if (netInterface.isLoopback() || !netInterface.isUp()) {
                    continue;
                }
                Enumeration<InetAddress> addresses = netInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress ip = addresses.nextElement();
                    if (ip instanceof Inet4Address) {
                        String addr = ip.getHostAddress();
                        if ("127.0.0.1".equals(addr) || ip.isLoopbackAddress()) {
                            continue;
                        }
                        LOGGER.info("[getLocal] 命中有效内网地址: iface={}, ip={}", netInterface.getName(), addr);
                        return addr;
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("[getLocal] 扫描局域网地址异常", e);
        }
        return null;
    }

    /**
     * 获取当前物理机主机名称。
     *
     * @return 计算机名称字符串，获取失败则返回 null
     */
    public static String getHostName() {
        try {
            InetAddress addr = InetAddress.getLocalHost();
            String hostName = addr.getHostName();
            LOGGER.debug("[getHostName] 本机主机名: {}", hostName);
            return hostName;
        } catch (UnknownHostException e) {
            LOGGER.error("[getHostName] 解析本机主机名失败", e);
            return null;
        }
    }
}
