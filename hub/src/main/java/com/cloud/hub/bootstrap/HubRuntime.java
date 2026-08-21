package com.cloud.hub.bootstrap;

import com.cloud.hub.game.Game;
import com.cloud.hub.game.config.GameRuntimeConfig;
import com.cloud.hub.game.domain.replay.ReplayDirectories;
import com.cloud.hub.storage.DataPathResolver;
import com.cloud.hub.lobby.manager.table.TableManager;
import com.cloud.hub.lobby.db.SqliteDatabase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
public class HubRuntime {
    private final HubLifecycle lifecycle;
    private final DataPathResolver paths;
    private final String accountDatabase;
    private final String tableConfigDir;
    private final String replayDir;
    private final int webOfflineTimeoutSeconds;
    private final int robotDelayMinMs;
    private final int robotDelayMaxMs;
    private final int gameWorkers;
    private final int gameQueueCapacity;
    private final int gameDatabaseThreads;

    public HubRuntime(HubLifecycle lifecycle, DataPathResolver paths,
                         @Value("$"+"{account.db-path:data/lobby.db}") String accountDatabase,
                         @Value("$"+"{hub.table-config-dir:config}") String tableConfigDir,
                         @Value("$"+"{game.replay-dir:data/replay}") String replayDir,
                         @Value("$"+"{game.web-offline-timeout-seconds:30}") int webOfflineTimeoutSeconds,
                         @Value("$"+"{game.robot-operation-delay-min-ms:3000}") int robotDelayMinMs,
                         @Value("$"+"{game.robot-operation-delay-max-ms:6000}") int robotDelayMaxMs,
                         @Value("$"+"{game.worker-threads:4}") int gameWorkers,
                         @Value("$"+"{game.queue-capacity:100000}") int gameQueueCapacity,
                         @Value("$"+"{game.database-threads:2}") int gameDatabaseThreads) {
        this.lifecycle = lifecycle;
        this.paths = paths;
        this.accountDatabase = accountDatabase;
        this.tableConfigDir = tableConfigDir;
        this.replayDir = replayDir;
        this.webOfflineTimeoutSeconds = webOfflineTimeoutSeconds;
        this.robotDelayMinMs = robotDelayMinMs;
        this.robotDelayMaxMs = robotDelayMaxMs;
        this.gameWorkers = gameWorkers;
        this.gameQueueCapacity = gameQueueCapacity;
        this.gameDatabaseThreads = gameDatabaseThreads;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        String databasePath = paths.resolve(accountDatabase).toString();
        ReplayDirectories.configure(paths.resolve(replayDir));
        System.setProperty("table.config.dir", paths.resolve(tableConfigDir).toString());
        System.setProperty("tableConfigDir", paths.resolve(tableConfigDir).toString());
        GameRuntimeConfig.configure(webOfflineTimeoutSeconds, robotDelayMinMs, robotDelayMaxMs);
        lifecycle.start(Arrays.asList(
                managed(HubComponent.STORAGE, () -> SqliteDatabase.initialize(databasePath), () -> { }),
                managed(HubComponent.LOBBY, () -> TableManager.getInstance().init(),
                        () -> TableManager.getInstance().shutdown()),
                managed(HubComponent.GAME,
                        () -> Game.getInstance().startEmbedded(databasePath, gameWorkers,
                                gameQueueCapacity, gameDatabaseThreads),
                        () -> Game.getInstance().shutdown()),
                managed(HubComponent.GATEWAY, () -> { }, () -> { })));
        lifecycle.markReady(HubComponent.WEB);
    }

    private static ManagedComponent managed(HubComponent component, CheckedAction start, CheckedAction stop) {
        return new ManagedComponent() {
            public HubComponent component() { return component; }
            public void start() throws Exception { start.run(); }
            public void stop() throws Exception { stop.run(); }
        };
    }

    private interface CheckedAction { void run() throws Exception; }
}
