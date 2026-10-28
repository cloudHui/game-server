package utils.concurrent.thread;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import utils.trace.TraceContext;

/**
 * 统一装配的任务包装器（DecoratedRunnable）。
 * <p>
 * <b>核心职责：</b>
 * <ul>
 * <li><b>上下文自动快照与跨线程透传：</b> 捕获提交线程的 {@link TraceContext} 与 SLF4J MDC 上下文，并在 Worker 执行时还原；</li>
 * <li><b>慢任务雷达监控：</b> 记录执行耗时，超过阈值（默认 1000ms）自动输出带上下文的警告日志；</li>
 * <li><b>安全闭环与零污染清理：</b> 在 {@code finally} 块中必定安全清理当前线程的 Trace 与 MDC，防止线程池复用引发脏上下文串染；</li>
 * <li><b>统一未捕获异常日志切面：</b> 捕获未处理的运行时异常并输出结构化 ERROR 日志后重新抛出。</li>
 * </ul>
 * </p>
 *
 * @author cloud
 */
public class DecoratedRunnable implements Runnable {

    private static final Logger LOGGER = LoggerFactory.getLogger(DecoratedRunnable.class);

    /** 慢任务警告耗时阈值（毫秒） */
    public static final long DEFAULT_SLOW_TASK_THRESHOLD_MS = 1000L;

    private final Runnable delegate;
    private final String taskName;
    private final String traceId;
    private final Long tableId;
    private final Map<String, String> mdcContext;
    private final long slowThresholdMs;

    public DecoratedRunnable(Runnable delegate) {
        this(delegate, null, null, DEFAULT_SLOW_TASK_THRESHOLD_MS);
    }

    public DecoratedRunnable(Runnable delegate, String taskName) {
        this(delegate, taskName, null, DEFAULT_SLOW_TASK_THRESHOLD_MS);
    }

    public DecoratedRunnable(Runnable delegate, String taskName, Long tableId) {
        this(delegate, taskName, tableId, DEFAULT_SLOW_TASK_THRESHOLD_MS);
    }

    public DecoratedRunnable(Runnable delegate, String taskName, Long tableId, long slowThresholdMs) {
        this.delegate = delegate;
        this.taskName = (taskName != null && !taskName.trim().isEmpty())
                ? taskName
                : delegate.getClass().getSimpleName();
        this.traceId = TraceContext.getTraceId();
        this.tableId = tableId;
        this.mdcContext = MDC.getCopyOfContextMap();
        this.slowThresholdMs = slowThresholdMs > 0 ? slowThresholdMs : DEFAULT_SLOW_TASK_THRESHOLD_MS;
    }

    /**
     * 静态工厂便捷构造方法。
     *
     * @param runnable 具体执行体
     * @return 包装后的任务
     */
    public static DecoratedRunnable of(Runnable runnable) {
        return new DecoratedRunnable(runnable);
    }

    /**
     * 静态工厂便捷构造方法（带任务名称）。
     *
     * @param runnable 具体执行体
     * @param taskName 任务名称（用于慢任务排查与异常定位）
     * @return 包装后的任务
     */
    public static DecoratedRunnable of(Runnable runnable, String taskName) {
        return new DecoratedRunnable(runnable, taskName);
    }

    /**
     * 静态工厂便捷构造方法（带任务名称与桌号）。
     *
     * @param runnable 具体执行体
     * @param taskName 任务名称
     * @param tableId  业务桌号
     * @return 包装后的任务
     */
    public static DecoratedRunnable of(Runnable runnable, String taskName, Long tableId) {
        return new DecoratedRunnable(runnable, taskName, tableId);
    }

    @Override
    public void run() {
        Map<String, String> previousMdc = MDC.getCopyOfContextMap();
        String previousTraceId = TraceContext.getTraceId();
        long start = System.currentTimeMillis();

        try {
            // 1. 还原提交时刻的 MDC 上下文
            if (mdcContext != null && !mdcContext.isEmpty()) {
                MDC.setContextMap(mdcContext);
            }
            // 2. 还原或设置 traceId
            if (traceId != null) {
                TraceContext.setTraceId(traceId);
            }
            // 3. 显式补充设置 tableId
            if (tableId != null && tableId > 0) {
                TraceContext.setTableId(tableId);
            }

            // 4. 执行核心业务逻辑
            delegate.run();
        } catch (Throwable t) {
            LOGGER.error("[DecoratedRunnable] 任务执行发生未捕获异常: taskName={}, tableId={}, traceId={}",
                    taskName, tableId, TraceContext.getTraceId(), t);
            throw t;
        } finally {
            // 5. 慢任务监控统计
            long cost = System.currentTimeMillis() - start;
            if (cost >= slowThresholdMs) {
                LOGGER.warn("[SLOW-TASK] 慢任务告警: taskName='{}', tableId={}, cost={}ms (阈值: {}ms)",
                        taskName, tableId, cost, slowThresholdMs);
            }

            // 6. 彻底清理或恢复上下文现场，防止 Worker 线程复用时数据串染
            if (previousMdc != null && !previousMdc.isEmpty()) {
                MDC.setContextMap(previousMdc);
            } else {
                MDC.clear();
            }

            if (previousTraceId != null) {
                TraceContext.setTraceId(previousTraceId);
            } else {
                TraceContext.endTrace();
            }
        }
    }

    /**
     * 包装带有返回值的 Supplier，透传链路上下文与慢任务监控。
     */
    public static <T> Supplier<T> decorateSupplier(Supplier<T> supplier, String taskName, Long tableId) {
        String capturedTraceId = TraceContext.getTraceId();
        Map<String, String> capturedMdc = MDC.getCopyOfContextMap();
        String name = (taskName != null && !taskName.trim().isEmpty()) ? taskName : supplier.getClass().getSimpleName();

        return () -> {
            Map<String, String> previousMdc = MDC.getCopyOfContextMap();
            String previousTraceId = TraceContext.getTraceId();
            long start = System.currentTimeMillis();
            try {
                if (capturedMdc != null && !capturedMdc.isEmpty()) {
                    MDC.setContextMap(capturedMdc);
                }
                if (capturedTraceId != null) {
                    TraceContext.setTraceId(capturedTraceId);
                }
                if (tableId != null && tableId > 0) {
                    TraceContext.setTableId(tableId);
                }
                return supplier.get();
            } catch (Throwable t) {
                LOGGER.error("[DecoratedSupplier] 计算任务发生未捕获异常: taskName={}, tableId={}", name, tableId, t);
                throw t;
            } finally {
                long cost = System.currentTimeMillis() - start;
                if (cost >= DEFAULT_SLOW_TASK_THRESHOLD_MS) {
                    LOGGER.warn("[SLOW-TASK] 慢计算任务告警: taskName='{}', tableId={}, cost={}ms", name, tableId, cost);
                }
                if (previousMdc != null && !previousMdc.isEmpty()) {
                    MDC.setContextMap(previousMdc);
                } else {
                    MDC.clear();
                }
                if (previousTraceId != null) {
                    TraceContext.setTraceId(previousTraceId);
                } else {
                    TraceContext.endTrace();
                }
            }
        };
    }

    /**
     * 包装带有返回值的 Callable，透传链路上下文与慢任务监控。
     */
    public static <T> Callable<T> decorateCallable(Callable<T> callable, String taskName, Long tableId) {
        String capturedTraceId = TraceContext.getTraceId();
        Map<String, String> capturedMdc = MDC.getCopyOfContextMap();
        String name = (taskName != null && !taskName.trim().isEmpty()) ? taskName : callable.getClass().getSimpleName();

        return () -> {
            Map<String, String> previousMdc = MDC.getCopyOfContextMap();
            String previousTraceId = TraceContext.getTraceId();
            long start = System.currentTimeMillis();
            try {
                if (capturedMdc != null && !capturedMdc.isEmpty()) {
                    MDC.setContextMap(capturedMdc);
                }
                if (capturedTraceId != null) {
                    TraceContext.setTraceId(capturedTraceId);
                }
                if (tableId != null && tableId > 0) {
                    TraceContext.setTableId(tableId);
                }
                return callable.call();
            } catch (Throwable t) {
                LOGGER.error("[DecoratedCallable] 管理任务发生未捕获异常: taskName={}, tableId={}", name, tableId, t);
                throw t;
            } finally {
                long cost = System.currentTimeMillis() - start;
                if (cost >= DEFAULT_SLOW_TASK_THRESHOLD_MS) {
                    LOGGER.warn("[SLOW-TASK] 慢管理任务告警: taskName='{}', tableId={}, cost={}ms", name, tableId, cost);
                }
                if (previousMdc != null && !previousMdc.isEmpty()) {
                    MDC.setContextMap(previousMdc);
                } else {
                    MDC.clear();
                }
                if (previousTraceId != null) {
                    TraceContext.setTraceId(previousTraceId);
                } else {
                    TraceContext.endTrace();
                }
            }
        };
    }
}
