package web.arena;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import web.account.AccountDatabase;
import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.Map;
import static org.junit.Assert.*;

public class ArenaRepositoryTest {
    private File dir;
    private ArenaRepository repository;
    @Before public void setUp() throws Exception {dir=Files.createTempDirectory("arena-db").toFile();AccountDatabase db=new AccountDatabase();Field f=AccountDatabase.class.getDeclaredField("dbPath");f.setAccessible(true);f.set(db,new File(dir,"lobby.db").getPath());db.init();repository=new ArenaRepository(db);repository.init();}
    @After public void tearDown(){delete(dir);}
    @Test public void playerProgressPersistsInSharedSqlite() throws Exception {Map<String,Object> initial=repository.state(7);assertEquals(5000L,initial.get("liquid"));Map<String,Object> ranked=repository.action(7,"rank","jianhuang",1,System.currentTimeMillis());assertEquals(4800L,ranked.get("liquid"));Map<String,Object> loaded=repository.state(7);assertEquals(4800L,loaded.get("liquid"));}
    @Test public void dungeonConsumesAttemptAndGrantsFirstClearReward() throws Exception {Map<String,Object> out=repository.action(8,"dungeon","",1,System.currentTimeMillis());assertEquals(1L,out.get("dungeonCleared"));assertEquals(4L,out.get("dungeonAttempts"));assertEquals(5600L,out.get("liquid"));assertEquals(24000L,out.get("teamPower"));assertEquals(out.get("teamPower"),((Map<?,?>)out.get("dungeonResult")).get("power"));}
    @Test public void starUpgradeConsumesShardsAndPersists() throws Exception {
        Map<String,Object> out=repository.action(9,"star","jianhuang",1,System.currentTimeMillis());
        Map<String,Object> hero=hero(out,"jianhuang");assertEquals(2,hero.get("stars"));assertEquals(0,hero.get("shards"));
    }
    @Test public void activityChestIsARealOneTimeReward() throws Exception {
        repository.action(10,"rank","jianhuang",1,System.currentTimeMillis());
        Map<String,Object> first=repository.action(10,"activity","20",1,System.currentTimeMillis());
        assertEquals(13L,first.get("fate"));
        try{repository.action(10,"activity","20",1,System.currentTimeMillis());fail("duplicate claim");}catch(IllegalArgumentException expected){assertEquals("活跃宝箱已领取",expected.getMessage());}
    }
    @SuppressWarnings("unchecked") private Map<String,Object> hero(Map<String,Object> state,String id){for(Map<String,Object> h:(java.util.List<Map<String,Object>>)state.get("heroes"))if(id.equals(h.get("id")))return h;throw new AssertionError(id);}
    private void delete(File f){if(f==null||!f.exists())return;if(f.isDirectory())for(File x:f.listFiles())delete(x);f.delete();}
}
