package com.gamer.data.excel.modelgen.domain;

/**
 * 服务器配置选择结果
 */
public class ServerConfigSelection {
    private final boolean gameServer;
    private final boolean worldServer;

    public ServerConfigSelection(boolean gameServer, boolean worldServer) {
        this.gameServer = gameServer;
        this.worldServer = worldServer;
    }

    public boolean isGameServer() {
        return gameServer;
    }

    public boolean isWorldServer() {
        return worldServer;
    }
}
