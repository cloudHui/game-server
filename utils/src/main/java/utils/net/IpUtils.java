package utils.net;

import utils.system.ExecCommand;

import java.net.*;
import java.util.Enumeration;
import java.util.List;

public class IpUtils {

    /**
     * 获取外网ip
     *
     * @return 外网ip地址
     */
    public static String getOutIp() {
        List<String> results = ExecCommand.exeCommand("curl icanhazip.com");
        if (results.isEmpty()) {
            return "";
        }
        return results.get(0);
    }

    /**
     * 获取本机ip
     *
     * @return 内网ip
     */
    public static String getLocalIP() {
        return "127.0.0.1";
    }

    /**
     * 获取本机
     */
    public static String getIP() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                if (networkInterface.isLoopback() || !networkInterface.isUp()) {
                    continue;
                }
                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (address instanceof Inet4Address) {
                        return address.getHostAddress();
                    }
                }
            }
        } catch (SocketException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 获取内网ip
     */
    public static String getLocal() {
        try {
            StringBuilder sb = new StringBuilder();
            Enumeration<NetworkInterface> allNetInterfaces = NetworkInterface.getNetworkInterfaces();
            while (allNetInterfaces.hasMoreElements()) {
                NetworkInterface netInterface = allNetInterfaces.nextElement();
                Enumeration<InetAddress> addresses = netInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress ip = addresses.nextElement();
                    if (ip instanceof Inet4Address) {
                        if ("127.0.0.1".equals(ip.getHostAddress())) {
                            continue;
                        }
                        sb.append(ip.getHostAddress());
                        break;
                    }
                }
                if (sb.length() > 0) {
                    break;
                }
            }
            return sb.toString();
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * 获取计算机名称
     *
     * @return 计算机名称
     */
    public static String getHostName() {
        try {
            InetAddress adar = InetAddress.getLocalHost();
            return adar.getHostName();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
        return null;
    }
}
