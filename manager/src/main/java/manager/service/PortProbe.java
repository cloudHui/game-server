package manager.service;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

final class PortProbe {
    boolean isOpen(int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", port), 250);
            return true;
        } catch (IOException ignored) { return false; }
    }
}
