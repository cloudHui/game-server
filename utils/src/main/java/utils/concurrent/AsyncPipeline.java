package utils.concurrent;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 通用带背压控制的异步作业处理流水线。
 * <p>
 * 用于将高 CPU / 高 IO 消耗任务（如大图 WebP 转换、EXIF 提取、复杂对局复盘导出等）
 * 从网关和业务主线程安全解耦剥离，提供带队列容量上限保护的异步执行环境。
 *
 * @param <I> 输入载荷类型
 * @param <O> 输出结果类型
 * @author cloud
 */
public class AsyncPipeline<I, O> {

    private static final Logger logger = LoggerFactory.getLogger(AsyncPipeline.class);

    /** 流水线业务名称 */
    private final String pipelineName;

    /** 核心处理器逻辑函数 */
    private final Function<I, O> processor;

    /** 专用执行工作线程池 */
    private final ExecutorService executor;

    /** 正在处理中的活动任务计数器 */
    private final AtomicInteger activeTaskCount = new AtomicInteger(0);

    /**
     * 构建异步流水线实例。
     *
     * @param pipelineName 流水线标识名称
     * @param threadCount  工作线程数量
     * @param queueCapacity 任务等待队列容量上限（超过后采取调用方自运行或拒绝保护）
     * @param processor    实际执行的同步处理函数
     */
    public AsyncPipeline(String pipelineName, int threadCount, int queueCapacity, Function<I, O> processor) {
        this.pipelineName = pipelineName;
        this.processor = processor;
        this.executor = new ThreadPoolExecutor(
                threadCount,
                threadCount,
                60L,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                new NamedThreadFactory(pipelineName),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    /**
     * 向流水线异步提交一个作业项。
     *
     * @param input 输入任务参数
     * @return 包含最终处理结果的 CompletableFuture
     */
    public CompletableFuture<O> submit(I input) {
        CompletableFuture<O> future = new CompletableFuture<>();
        activeTaskCount.incrementAndGet();

        // 异步提交到专用池执行，即便内部抛出非受检异常也能在 Future 中安全捕获
        executor.submit(() -> {
            try {
                O result = processor.apply(input);
                future.complete(result);
            } catch (Throwable t) {
                logger.error("流水线 [{}] 处理作业异常: {}", pipelineName, t.getMessage(), t);
                future.completeExceptionally(t);
            } finally {
                activeTaskCount.decrementAndGet();
            }
        });

        return future;
    }

    /**
     * 获取当前处于排队或运行中的任务总数。
     *
     * @return 活跃任务数
     */
    public int getActiveTaskCount() {
        return activeTaskCount.get();
    }

    /**
     * 优雅关闭流水线线程池。
     */
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(3, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException ignored) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 命名守护线程工厂。
     */
    private static final class NamedThreadFactory implements ThreadFactory {
        private final String prefix;
        private final AtomicInteger seq = new AtomicInteger(1);

        private NamedThreadFactory(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, prefix + "-worker-" + seq.getAndIncrement());
            t.setDaemon(true);
            return t;
        }
    }
}
