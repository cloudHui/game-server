package utils.concurrent;

import org.junit.Assert;
import org.junit.Test;
import org.slf4j.MDC;
import utils.concurrent.thread.DecoratedRunnable;
import utils.trace.TraceContext;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class DecoratedRunnableTest {

    @Test
    public void testTraceAndMdcContextPropagation() {
        TraceContext.beginTrace();
        String originTraceId = TraceContext.getTraceId();
        TraceContext.setTableId(8888L);
        MDC.put("customKey", "customValue");

        AtomicReference<String> innerTraceId = new AtomicReference<>();
        AtomicReference<String> innerTableId = new AtomicReference<>();
        AtomicReference<String> innerCustom = new AtomicReference<>();

        DecoratedRunnable decorated = DecoratedRunnable.of(() -> {
            innerTraceId.set(TraceContext.getTraceId());
            innerTableId.set(MDC.get("tableId"));
            innerCustom.set(MDC.get("customKey"));
        }, "UnitTestTask", 8888L);

        // 清空外层现场以模拟切换到 Worker 线程
        TraceContext.endTrace();
        MDC.clear();

        Assert.assertNull(TraceContext.getTraceId());
        Assert.assertNull(MDC.get("customKey"));

        // 在新上下文执行
        decorated.run();

        // 验证执行体内透传成功
        Assert.assertEquals(originTraceId, innerTraceId.get());
        Assert.assertEquals("8888", innerTableId.get());
        Assert.assertEquals("customValue", innerCustom.get());

        // 验证执行后 Worker 现场彻底清理
        Assert.assertNull(TraceContext.getTraceId());
        Assert.assertNull(MDC.get("tableId"));
        Assert.assertNull(MDC.get("customKey"));
    }

    @Test
    public void testExceptionCleanup() {
        TraceContext.beginTrace();
        TraceContext.setTableId(9999L);
        MDC.put("foo", "bar");

        DecoratedRunnable decorated = DecoratedRunnable.of(() -> {
            throw new RuntimeException("test failure");
        }, "ExceptionTask", 9999L);

        TraceContext.endTrace();
        MDC.clear();

        AtomicBoolean threw = new AtomicBoolean(false);
        try {
            decorated.run();
        } catch (RuntimeException e) {
            threw.set(true);
        }

        Assert.assertTrue(threw.get());
        // 验证抛出异常后 Worker 现场同样被安全清理
        Assert.assertNull(TraceContext.getTraceId());
        Assert.assertNull(MDC.get("tableId"));
        Assert.assertNull(MDC.get("foo"));
    }
}
