package com.cloud.hub.web.arena;

import com.cloud.hub.storage.DataPathResolver;
import com.cloud.hub.web.account.AccountDatabase;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.Map;

import static org.junit.Assert.*;

public class ArenaRepositoryDecomposedTest {
    private File tempDir;
    private ArenaRepository repo;

    @Before
    public void setUp() throws Exception {
        tempDir = Files.createTempDirectory("arena-decomp-test").toFile();
        DataPathResolver resolver = new DataPathResolver(tempDir.getAbsolutePath());
        AccountDatabase db = new AccountDatabase(resolver);
        Field f = AccountDatabase.class.getDeclaredField("dbPath");
        f.setAccessible(true);
        f.set(db, "lobby.db");
        db.init();

        repo = new ArenaRepository(db);
        repo.init();
    }

    @After
    public void tearDown() {
        if (tempDir != null && tempDir.exists()) {
            for (File file : tempDir.listFiles()) {
                file.delete();
            }
            tempDir.delete();
        }
    }

    @Test
    public void testFullDomainFlow() throws Exception {
        long uid = 1001L;

        // 1. 验证初始状态
        Map<String, Object> state = repo.state(uid);
        assertEquals(5000L, state.get("liquid"));
        assertEquals(2500L, state.get("coins"));
        assertEquals(12L, state.get("fate"));
        assertEquals(800L, state.get("stones"));
        assertEquals(1L, state.get("beastLevel"));
        assertEquals(1L, state.get("grindLevel"));

        // 2. 突破与功法提升
        Map<String, Object> ranked = repo.action(uid, "rank", "jianhuang", 1, System.currentTimeMillis());
        assertEquals(4800L, ranked.get("liquid")); // 消耗 1 * 200

        Map<String, Object> skilled = repo.action(uid, "skill", "jianhuang", 1, System.currentTimeMillis());
        assertEquals(2350L, skilled.get("coins")); // 消耗 1 * 150

        // 3. 通天塔挑战
        Map<String, Object> d1 = repo.action(uid, "dungeon", "", 1, System.currentTimeMillis());
        assertEquals(1L, d1.get("dungeonCleared"));
        assertEquals(4L, d1.get("dungeonAttempts"));

        // 4. 每日俸禄领取
        Map<String, Object> settled = repo.action(uid, "dungeon_settle", "", 0, System.currentTimeMillis());
        assertEquals(1L, settled.get("dungeonSettled"));

        // 5. 丹药服用与购买
        Map<String, Object> pill1 = repo.action(uid, "consume_pill", "", 1, System.currentTimeMillis());
        assertEquals(1L, pill1.get("pillsTier1"));

        Map<String, Object> pillBuy = repo.action(uid, "buy_pill", "", 1, System.currentTimeMillis());
        assertEquals(1L, pillBuy.get("pillsTier4"));
        assertEquals(4L, pillBuy.get("fate")); // 12 - 8 = 4

        // 6. 兽神塔挑战与神兽碎片
        Map<String, Object> bt = repo.action(uid, "beast_tower", "", 1, System.currentTimeMillis());
        assertTrue(((Number) bt.get("beastShards")).intValue() >= 5);

        // 7. 神通装配
        Map<String, Object> equipped = repo.action(uid, "equip_skill", "defense", 1, System.currentTimeMillis());
        assertEquals("defense", equipped.get("equippedSkill"));

        // 8. 每日登录任务领取
        Map<String, Object> claimed = repo.action(uid, "claim", "login", 1, System.currentTimeMillis());
        assertNotNull(claimed);
    }
}
