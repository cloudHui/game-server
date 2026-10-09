package utils.concurrent;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.Assert;
import org.junit.Test;

/**
 * 异步流水线单元测试。
 */
public class AsyncPipelineTest {

    @Test
    public void testAsyncPipelineExecution() throws Exception {
        AsyncPipeline<Integer, String> pipeline = new AsyncPipeline<>("test-pipe", 2, 10, i -> "res-" + (i * 2));
        try {
            CompletableFuture<String> f1 = pipeline.submit(10);
            CompletableFuture<String> f2 = pipeline.submit(20);

            Assert.assertEquals("res-20", f1.get(2, TimeUnit.SECONDS));
            Assert.assertEquals("res-40", f2.get(2, TimeUnit.SECONDS));
        } finally {
            pipeline.shutdown();
        }
    }
}
