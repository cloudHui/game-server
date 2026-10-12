package utils.time;

/**
 * 性能跟踪工具类，用于测量代码执行时间并提供灵活的异常处理机制。
 *
 * <p>主要特性：
 * <ul>
 *   <li>精确测量代码执行时间</li>
 *   <li>支持带返回值和无返回值的方法跟踪</li>
 *   <li>提供两种异常处理模式</li>
 *   <li>支持自动资源管理</li>
 * </ul>
 *
 * <h2>使用示例：</h2>
 * <pre>{@code
 * try (TimeTracker ignored = TimeTracker.of("网络请求")) {
 *     httpClient.sendRequest();
 *     httpClient.receiveResponse();
 * }
 * }</pre>
 *
 * @author cloud
 */
public class TimeTracker implements AutoCloseable {

    /** 操作名称 */
    private final String operationName;
    /** 开始时间（纳秒） */
    private final long startTime;
    /** 是否启用日志 */
    private final boolean logEnabled;

    public TimeTracker(String operationName) {
        this(operationName, true);
    }

    private TimeTracker(String operationName, boolean logEnabled) {
        this.operationName = operationName;
        this.startTime = System.nanoTime();
        this.logEnabled = logEnabled;
        if (logEnabled) {
            System.out.printf("开始执行: %s%n", operationName);
        }
    }

    public static TimeTracker of(String operationName) {
        return new TimeTracker(operationName);
    }

    public static <T> T track(String operationName, ThrowableSupplier<T> execution) {
        try {
            return trackThrows(operationName, execution);
        } catch (Exception e) {
            throw new RuntimeException("执行失败: " + operationName, e);
        }
    }

    public static <T> T trackThrows(String operationName, ThrowableSupplier<T> execution) throws Exception {
        try (TimeTracker ignored = new TimeTracker(operationName, true)) {
            return execution.get();
        }
    }

    public static void track(String operationName, ThrowableRunnable execution) {
        try {
            trackThrows(operationName, execution);
        } catch (Exception e) {
            throw new RuntimeException("执行失败: " + operationName, e);
        }
    }

    public static void trackThrows(String operationName, ThrowableRunnable execution) throws Exception {
        try (TimeTracker ignored = new TimeTracker(operationName, true)) {
            execution.run();
        }
    }

    @Override
    public void close() {
        if (logEnabled) {
            long timeElapsed = (System.nanoTime() - startTime) / 1_000_000;
            System.out.printf("%s 执行完成，耗时: %d ms%n", operationName, timeElapsed);
        }
    }

    @FunctionalInterface
    public interface ThrowableSupplier<T> {
        T get() throws Exception;
    }

    @FunctionalInterface
    public interface ThrowableRunnable {
        void run() throws Exception;
    }
}
