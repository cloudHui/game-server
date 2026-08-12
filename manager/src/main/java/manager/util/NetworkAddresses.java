package manager.util;

import java.net.*;
import java.util.Collections;

public final class NetworkAddresses {
    private NetworkAddresses() {}

    public static String localIpv4() throws SocketException {
        return Collections.list(NetworkInterface.getNetworkInterfaces()).stream()
            .filter(NetworkAddresses::usable)
            .flatMap(item -> Collections.list(item.getInetAddresses()).stream())
            .filter(address -> address instanceof Inet4Address && address.isSiteLocalAddress())
            .map(InetAddress::getHostAddress)
            .findFirst().orElse("127.0.0.1");
    }

    private static boolean usable(NetworkInterface item) {
        try { return item.isUp() && !item.isLoopback() && !item.isVirtual(); }
        catch (SocketException ignored) { return false; }
    }
}
