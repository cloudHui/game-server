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
    @Test public void dungeonConsumesAttemptAndGrantsFirstClearReward() throws Exception {Map<String,Object> out=repository.action(8,"dungeon","",1,System.currentTimeMillis());assertEquals(1L,out.get("dungeonCleared"));assertEquals(4L,out.get("dungeonAttempts"));assertEquals(5600L,out.get("liquid"));}
    private void delete(File f){if(f==null||!f.exists())return;if(f.isDirectory())for(File x:f.listFiles())delete(x);f.delete();}
}
