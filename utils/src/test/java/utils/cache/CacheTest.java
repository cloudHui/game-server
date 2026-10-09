package utils.cache;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Assert;
import org.junit.Test;

/**
 * 本地高性能带过期淘汰的内存缓存单元测试。
 */
public class CacheTest {

    @Test
    public void testPutGetAndExpire() throws InterruptedException {
        LocalExpiringCache<String, String> cache = new LocalExpiringCache<>(100);
        cache.put("k1", "v1");
        Assert.assertEquals("v1", cache.get("k1"));

        Thread.sleep(150);
        // 过期后惰性清除
        Assert.assertNull(cache.get("k1"));
    }

    @Test
    public void testLoaderProtection() throws Exception {
        LocalExpiringCache<String, Integer> cache = new LocalExpiringCache<>(500);
        AtomicInteger counter = new AtomicInteger(0);

        Integer v1 = cache.get("k2", counter::incrementAndGet);
        Integer v2 = cache.get("k2", counter::incrementAndGet);

        Assert.assertEquals(Integer.valueOf(1), v1);
        Assert.assertEquals(Integer.valueOf(1), v2);
        Assert.assertEquals(1, counter.get());
    }
}
