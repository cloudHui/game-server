package com.gamer.data.mpcserver.core;

import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gamer.data.log.Log;

/** 所有工具共用的运行上下文。 */
public class CommandContext {
    private final ObjectMapper mapper;
    private final Log log;
    private final Map<String, DbDefaults> databases;
    private final FileSandbox files;
    private final RedisDefaults redis;
    private final GitDefaults git;
    private final PlayerDatabases playerDatabases; // 独立玩家只读连接

    public CommandContext(ObjectMapper mapper, Log log, Map<String, DbDefaults> databases, FileSandbox files,
        RedisDefaults redis, GitDefaults git) {
        this(mapper, log, databases, files, redis, git, null);
    }

    /** @param playerDatabases 启动时加载的玩家连接；其余参数与本地工具共用 */
    public CommandContext(ObjectMapper mapper, Log log, Map<String, DbDefaults> databases, FileSandbox files,
        RedisDefaults redis, GitDefaults git, PlayerDatabases playerDatabases) {
        this.mapper = mapper;
        this.log = log;
        this.databases = databases;
        this.files = files;
        this.redis = redis;
        this.git = git;
        this.playerDatabases = playerDatabases;
    }

    /** @return 独立配置连接 */
    public PlayerDatabases playerDatabases() {
        if (playerDatabases == null) {
            throw new IllegalArgumentException("玩家数据库未配置");
        }
        return playerDatabases;
    }

    public ObjectMapper mapper() {
        return mapper;
    }

    public Log log() {
        return log;
    }


    public DbDefaults dbDefaults(String target) {
        return databases == null ? null : databases.get(target);
    }

    public FileSandbox fileSandbox() {
        return files;
    }

    public RedisDefaults redisDefaults() {
        return redis;
    }

    public GitDefaults gitDefaults() {
        return git;
    }
}
