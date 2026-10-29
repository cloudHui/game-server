package utils.registry;

import net.handler.Handler;
import org.junit.Assert;
import org.junit.Test;
import utils.registry.fixture.MockMultiHandler;
import utils.registry.fixture.MockSingleHandler;

import java.util.HashMap;
import java.util.Map;

/**
 * 验证 HandlerRegistry 自动包扫描、多值注解绑定与直接无反射分发。
 */
public class HandlerRegistryTest {

    @Test
    public void testBindSingleAndMultiValues() {
        Map<Integer, Handler> handlerMap = new HashMap<>();
        HandlerRegistry.bind("utils.registry.fixture", handlerMap);

        // 1. 验证单值 @ProcessType(value = 10001)
        Assert.assertTrue(handlerMap.containsKey(10001));
        Handler singleHandler = handlerMap.get(10001);
        Assert.assertNotNull(singleHandler);
        Assert.assertTrue(singleHandler instanceof MockSingleHandler);
        Assert.assertTrue(singleHandler.handler(null, 1, null, 0L, 0));

        // 2. 验证多值 @ProcessType(values = {20001, 20002, 20003})
        Assert.assertTrue(handlerMap.containsKey(20001));
        Assert.assertTrue(handlerMap.containsKey(20002));
        Assert.assertTrue(handlerMap.containsKey(20003));

        Handler multi1 = handlerMap.get(20001);
        Handler multi2 = handlerMap.get(20002);
        Handler multi3 = handlerMap.get(20003);

        Assert.assertNotNull(multi1);
        Assert.assertTrue(multi1 instanceof MockMultiHandler);
        // 单例去重共享：相同实现类复用同一对象实例
        Assert.assertSame(multi1, multi2);
        Assert.assertSame(multi2, multi3);

        // 3. 验证直接多态执行
        Assert.assertTrue(multi1.handler(null, 2, null, 0L, 0));
    }

    @Test
    public void testEnsureMessageInitialized() {
        HandlerRegistry.ensureMessageInitialized();
        // 验证调用幂等性与初始化无异常
        HandlerRegistry.ensureMessageInitialized();
    }
}

