package manager.model;

import java.util.List;

public final class ServiceCatalog {
    public static final List<ServiceSpec> ALL = List.of(
        new ServiceSpec("center", "Center", "build/center/Center.jar", 5400, "64m"),
        new ServiceSpec("gate", "Gate", "build/gate/Gate.jar", 5600, "96m"),
        new ServiceSpec("lobby", "Lobby", "build/lobby/Lobby.jar", 5700, "128m"),
        new ServiceSpec("game", "Game", "build/game/Game.jar", 5500, "128m"),
        new ServiceSpec("web", "Web", "build/web/Web.jar", 8081, "192m")
    );

    private ServiceCatalog() {}

    public static ServiceSpec web() { return ALL.get(ALL.size() - 1); }
}
