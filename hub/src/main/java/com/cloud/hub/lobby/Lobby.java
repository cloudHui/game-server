package com.cloud.hub.lobby;

import com.cloud.hub.lobby.admin.LobbyAdminHttp;
import com.cloud.hub.lobby.db.InviteRepository;
import com.cloud.hub.lobby.db.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import proto.ModelProto;
import threadtutil.thread.ExecutorPool;
import threadtutil.timer.Runner;
import threadtutil.timer.Timer;
import tools.ServerClientManager;
import tools.ServerManager;
import utils.metrics.MetricsHttpServer;

import java.util.UUID;

/**
 * Lobby 服务器（原 hall + room 合并）
 */
public class Lobby {
    private static final Logger logger = LoggerFactory.getLogger(Lobby.class);
    private static final Lobby instance = new Lobby();

    private final ExecutorPool executorPool;
    private final Timer timer;
    public final ServerClientManager serverClientManager = new ServerClientManager();

    private int serverId;
    private String center;
    private String innerIp;
    private int port;
    private boolean openRegister;
    private ModelProto.ServerInfo serverInfo;
    private ServerManager serverManager;
    private MetricsHttpServer metricsHttpServer;
    private UserRepository userRepository;
    private InviteRepository inviteRepository;
    private LobbyAdminHttp adminHttp;

    private Lobby() {
        executorPool = new ExecutorPool("Lobby");
        timer = new Timer().setRunners(executorPool);
    }

    public static Lobby getInstance() {
        return instance;
    }

    public int getServerId() {
        return serverId;
    }

    public void setServerId(int serverId) {
        this.serverId = serverId;
    }

    public void setCenter(String center) {
        this.center = center;
    }

    public String getInnerIp() {
        return innerIp;
    }

    public void setInnerIp(String innerIp) {
        this.innerIp = innerIp;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public boolean isOpenRegister() {
        return openRegister;
    }

    public ServerManager getServerManager() {
        return serverManager;
    }

    public ServerClientManager getServerClientManager() {
        return serverClientManager;
    }

    public ModelProto.ServerInfo getServerInfo() {
        return serverInfo;
    }

    public UserRepository getUserRepository() {
        return userRepository;
    }

    public InviteRepository getInviteRepository() {
        return inviteRepository;
    }

    public void execute(Runnable task) {
        executorPool.execute(task);
    }

    public <T> void registerTimer(long delay, long interval, int count, Runner<T> runner, T param) {
        timer.register(delay, interval, count, runner, param);
    }

    public static String newToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
