package com.cloud.hub.game.db;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 数据库专用异步线程池管理器。
 * <p>
 * 独立于游戏业务逻辑线程池与网络 I/O 线程池，专用于承载 SQLite 等慢 I/O 落库任务，
 * 避免因单次磁盘写入延迟造成对局卡顿。
 * </p>
 *
 * @author cloud
 */
public class DatabaseExecutorManager {

    /** 底层工作线程池 */
    private final ExecutorService executor;

    /**
     * 根据指定线程数构建专用固定线程池。
     *
     * @param poolSize 线程数
     */
    public DatabaseExecutorManager(int poolSize) {
        executor = Executors.newFixedThreadPool(Math.max(1, poolSize), runnable ->
                new Thread(runnable, "Game-Database"));
    }

    /**
     * 包装已有 ExecutorService 实例。
     *
     * @param executor 外部线程池
     */
    public DatabaseExecutorManager(ExecutorService executor) {
        this.executor = executor;
    }

    /**
     * 提交异步落库任务。
     *
     * @param task 落库闭包任务
     * @return 异步执行结果 CompletableFuture
     */
    public CompletableFuture<Void> submit(Runnable task) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        executor.execute(() -> runTask(task, future));
        return future;
    }

    /**
     * 停服时关闭线程池。
     */
    public void shutdown() {
        executor.shutdownNow();
    }

    private void runTask(Runnable task, CompletableFuture<Void> future) {
        try {
            task.run();
            future.complete(null);
        } catch (Throwable error) {
            future.completeExceptionally(error);
        }
    }
}

