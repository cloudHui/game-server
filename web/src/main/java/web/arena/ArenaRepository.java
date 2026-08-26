package web.arena;

import org.springframework.stereotype.Repository;
import web.account.AccountDatabase;
import javax.annotation.PostConstruct;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;

@Repository
public class ArenaRepository {
    private final AccountDatabase db;
    public ArenaRepository(AccountDatabase db){this.db=db;}
    @PostConstruct public void init(){try(Connection c=db.getConnection();Statement s=c.createStatement()){
        ArenaSchema.initialize(s);
    }catch(SQLException e){throw new IllegalStateException("初始化剑气 SQLite 表失败",e);}}
    public synchronized Map<String,Object> state(long uid)throws SQLException{try(Connection c=db.getConnection()){c.setAutoCommit(false);ensure(c,uid);refresh(c,uid);Map<String,Object> out=read(c,uid);c.commit();return out;}}
    public synchronized Map<String,Object> action(long uid,String action,String id,int count,long now)throws SQLException{try(Connection c=db.getConnection()){c.setAutoCommit(false);ensure(c,uid);refresh(c,uid);Map<String,Object> result=null;switch(action){
        case "rank": rank(c,uid,id,false);break;case "skill":rank(c,uid,id,true);break;case "star":star(c,uid,id);break;case "dungeon":result=dungeon(c,uid,count);break;case "draw":draw(c,uid,count,now);break;case "claim":claim(c,uid,id);break;case "activity":activity(c,uid,id);break;case "formation":spend(c,uid,"stones","formation_level",100);progress(c,uid,"task_formation",1);break;case "grotto":grotto(c,uid,now);progress(c,uid,"task_grotto",1);break;case "arena":progress(c,uid,"task_arena",1);break;default:throw new IllegalArgumentException("未知操作");}Map<String,Object> out=read(c,uid);if(result!=null)out.put("dungeonResult",result);c.commit();return out;}}
    private void ensure(Connection c,long u)throws SQLException{try(PreparedStatement p=c.prepareStatement("INSERT OR IGNORE INTO arena_player(user_id) VALUES(?)")){p.setLong(1,u);p.executeUpdate();}try(PreparedStatement p=c.prepareStatement("INSERT OR IGNORE INTO arena_hero(user_id,hero_id,shards) VALUES(?,?,?)")){p.setLong(1,u);p.setString(2,"jianhuang");p.setInt(3,20);p.executeUpdate();p.setString(2,"leizun");p.setInt(3,0);p.executeUpdate();}}
    private void refresh(Connection c,long u)throws SQLException{String d=LocalDate.now().toString();try(PreparedStatement p=c.prepareStatement("UPDATE arena_player SET task_day=?,dungeon_attempts=5,task_login=1,task_dungeon=0,task_rank=0,task_recruit=0,task_arena=0,task_skill=0,task_formation=0,task_grotto=0,claimed='',activity_claimed='' WHERE user_id=? AND task_day<>?")){p.setString(1,d);p.setLong(2,u);p.setString(3,d);p.executeUpdate();}}
    private void rank(Connection c,long u,String h,boolean skill)throws SQLException{String col=skill?"skill_level":"rank",money=skill?"coins":"liquid";int unit=skill?150:200;int level;try(PreparedStatement p=c.prepareStatement("SELECT "+col+" FROM arena_hero WHERE user_id=? AND hero_id=?")){p.setLong(1,u);p.setString(2,h);try(ResultSet r=p.executeQuery()){if(!r.next())throw new IllegalArgumentException("尚未拥有该仙侣");level=r.getInt(1);}}spendAmount(c,u,money,(long)level*unit);try(PreparedStatement p=c.prepareStatement("UPDATE arena_hero SET "+col+"="+col+"+1 WHERE user_id=? AND hero_id=?")){p.setLong(1,u);p.setString(2,h);p.executeUpdate();}progress(c,u,skill?"task_skill":"task_rank",1);}
    private void star(Connection c,long u,String h)throws SQLException{int stars,shards;try(PreparedStatement p=c.prepareStatement("SELECT stars,shards FROM arena_hero WHERE user_id=? AND hero_id=?")){p.setLong(1,u);p.setString(2,h);try(ResultSet r=p.executeQuery()){if(!r.next())throw new IllegalArgumentException("尚未拥有该仙侣");stars=r.getInt(1);shards=r.getInt(2);}}if(stars>=6)throw new IllegalArgumentException("已达最高星级");int need=20+(stars-1)*15;if(shards<need)throw new IllegalArgumentException("仙侣碎片不足");try(PreparedStatement p=c.prepareStatement("UPDATE arena_hero SET stars=stars+1,shards=shards-? WHERE user_id=? AND hero_id=?")){p.setInt(1,need);p.setLong(2,u);p.setString(3,h);p.executeUpdate();}progress(c,u,"task_rank",1);}
    private Map<String,Object> dungeon(Connection c,long u,int stage)throws SQLException{int cleared,tries;try(PreparedStatement p=c.prepareStatement("SELECT dungeon_cleared,dungeon_attempts FROM arena_player WHERE user_id=?")){p.setLong(1,u);try(ResultSet r=p.executeQuery()){r.next();cleared=r.getInt(1);tries=r.getInt(2);}}if(stage<1||stage>12||stage>cleared+1)throw new IllegalArgumentException("请先通关前置副本");if(tries<1)throw new IllegalArgumentException("今日副本次数已用完");long power=teamPower(c,u),required=stage*18000L;if(power<required)throw new IllegalArgumentException("战力不足，需要 "+required);boolean first=stage>cleared;long liquid=(first?600:180)*stage,coins=(first?120:40)*stage;try(PreparedStatement p=c.prepareStatement("UPDATE arena_player SET dungeon_attempts=dungeon_attempts-1,dungeon_cleared=max(dungeon_cleared,?),liquid=liquid+?,coins=coins+?,stones=stones+? WHERE user_id=?")){p.setInt(1,stage);p.setLong(2,liquid);p.setLong(3,coins);p.setInt(4,first?stage*10:stage*3);p.setLong(5,u);p.executeUpdate();}progress(c,u,"task_dungeon",1);Map<String,Object> out=new LinkedHashMap<>();out.put("stage",stage);out.put("firstClear",first);out.put("power",power);out.put("requiredPower",required);out.put("liquid",liquid);out.put("coins",coins);out.put("rounds",Math.max(2,7-stage/2));return out;}
    private long teamPower(Connection c,long userId)throws SQLException{long power=0;try(PreparedStatement p=c.prepareStatement("SELECT rank,stars,skill_level FROM arena_hero WHERE user_id=? ORDER BY rank+stars+skill_level DESC LIMIT 3")){p.setLong(1,userId);try(ResultSet r=p.executeQuery()){while(r.next())power+=12000+(r.getInt(1)-1)*1800L+(r.getInt(2)-1)*4200L+(r.getInt(3)-1)*900L;}}return power;}
    private void draw(Connection c,long u,int n,long seed)throws SQLException{if(n!=1&&n!=10)throw new IllegalArgumentException("只支持单抽或十连");spendAmount(c,u,"fate",n);int pity;try(PreparedStatement p=c.prepareStatement("SELECT pity FROM arena_player WHERE user_id=?")){p.setLong(1,u);try(ResultSet r=p.executeQuery()){r.next();pity=r.getInt(1);}}String[] ids={"qinglan","xuanshuang","chixiao","taixu","yaohuang","leizun","jianhuang"};Random rng=new Random(seed);for(int i=0;i<n;i++){pity++;String q=pity>=90?"金":rng.nextInt(100)<2?"金":rng.nextInt(100)<20?"红":"橙";if(n==10&&i==9&&q.equals("橙"))q="红";if(q.equals("金"))pity=0;String h=ids[rng.nextInt(ids.length)];boolean dup=owned(c,u,h);if(dup){try(PreparedStatement p=c.prepareStatement("UPDATE arena_hero SET shards=shards+? WHERE user_id=? AND hero_id=?")){p.setInt(1,q.equals("金")?80:q.equals("红")?40:20);p.setLong(2,u);p.setString(3,h);p.executeUpdate();}}else try(PreparedStatement p=c.prepareStatement("INSERT INTO arena_hero(user_id,hero_id) VALUES(?,?)")){p.setLong(1,u);p.setString(2,h);p.executeUpdate();}try(PreparedStatement p=c.prepareStatement("INSERT INTO arena_draw_log(user_id,hero_id,quality,duplicate,created_at) VALUES(?,?,?,?,?)")){p.setLong(1,u);p.setString(2,h);p.setString(3,q);p.setInt(4,dup?1:0);p.setLong(5,System.currentTimeMillis());p.executeUpdate();}}try(PreparedStatement p=c.prepareStatement("UPDATE arena_player SET pity=? WHERE user_id=?")){p.setInt(1,pity);p.setLong(2,u);p.executeUpdate();}progress(c,u,"task_recruit",n);}
    private void claim(Connection c, long userId, String id) throws SQLException {
        ArenaTaskCatalog.Task task = ArenaTaskCatalog.task(id);
        if (task == null) throw new IllegalArgumentException("未知任务");
        int progress;
        String claimed;
        try (PreparedStatement statement = c.prepareStatement("SELECT task_" + id + ",claimed FROM arena_player WHERE user_id=?")) {
            statement.setLong(1, userId);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                progress = result.getInt(1);
                claimed = result.getString(2);
            }
        }
        if (has(claimed, id)) throw new IllegalArgumentException("奖励已领取");
        if (progress < task.target) throw new IllegalArgumentException("任务未完成");
        try (PreparedStatement statement = c.prepareStatement("UPDATE arena_player SET liquid=liquid+?,coins=coins+?,fate=fate+?,stones=stones+?,claimed=claimed||? WHERE user_id=?")) {
            statement.setInt(1, task.liquid);
            statement.setInt(2, task.coins);
            statement.setInt(3, task.fate);
            statement.setInt(4, task.stones);
            statement.setString(5, id + ",");
            statement.setLong(6, userId);
            statement.executeUpdate();
        }
    }

    private void activity(Connection c, long userId, String id) throws SQLException {
        int target;
        try { target = Integer.parseInt(id); }
        catch (NumberFormatException error) { throw new IllegalArgumentException("活跃宝箱非法"); }
        ArenaTaskCatalog.Chest chest = ArenaTaskCatalog.chest(target);
        if (chest == null) throw new IllegalArgumentException("活跃宝箱非法");
        int points = 0;
        String claimed;
        try (PreparedStatement statement = c.prepareStatement("SELECT * FROM arena_player WHERE user_id=?")) {
            statement.setLong(1, userId);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                for (ArenaTaskCatalog.Task task : ArenaTaskCatalog.tasks().values())
                    if (result.getInt("task_" + task.id) >= task.target) points += task.activity;
                claimed = result.getString("activity_claimed");
            }
        }
        if (has(claimed, id)) throw new IllegalArgumentException("活跃宝箱已领取");
        if (points < target) throw new IllegalArgumentException("活跃度不足");
        try (PreparedStatement statement = c.prepareStatement("UPDATE arena_player SET fate=fate+?,stones=stones+?,liquid=liquid+?,activity_claimed=activity_claimed||? WHERE user_id=?")) {
            statement.setInt(1, chest.fate);
            statement.setInt(2, chest.stones);
            statement.setInt(3, chest.liquid);
            statement.setString(4, id + ",");
            statement.setLong(5, userId);
            statement.executeUpdate();
        }
    }
    private boolean has(String csv,String id){return Arrays.asList(csv.split(",")).contains(id);}
    private void grotto(Connection c,long u,long now)throws SQLException{long last;int lv;try(PreparedStatement p=c.prepareStatement("SELECT grotto_claim_at,grotto_level FROM arena_player WHERE user_id=?")){p.setLong(1,u);try(ResultSet r=p.executeQuery()){r.next();last=r.getLong(1);lv=r.getInt(2);}}long hours=last==0?4:Math.max(1,Math.min(12,(now-last)/3600000));try(PreparedStatement p=c.prepareStatement("UPDATE arena_player SET liquid=liquid+?,grotto_claim_at=? WHERE user_id=?")){p.setLong(1,hours*240*lv);p.setLong(2,now);p.setLong(3,u);p.executeUpdate();}}
    private void spend(Connection c,long u,String money,String level,int unit)throws SQLException{int lv;try(PreparedStatement p=c.prepareStatement("SELECT "+level+" FROM arena_player WHERE user_id=?")){p.setLong(1,u);try(ResultSet r=p.executeQuery()){r.next();lv=r.getInt(1);}}spendAmount(c,u,money,(long)lv*unit);try(PreparedStatement p=c.prepareStatement("UPDATE arena_player SET "+level+"="+level+"+1 WHERE user_id=?")){p.setLong(1,u);p.executeUpdate();}}
    private void spendAmount(Connection c,long u,String col,long amount)throws SQLException{try(PreparedStatement p=c.prepareStatement("UPDATE arena_player SET "+col+"="+col+"-? WHERE user_id=? AND "+col+">=?")){p.setLong(1,amount);p.setLong(2,u);p.setLong(3,amount);if(p.executeUpdate()!=1)throw new IllegalArgumentException("资源不足");}}
    private void progress(Connection c,long u,String col,int n)throws SQLException{try(PreparedStatement p=c.prepareStatement("UPDATE arena_player SET "+col+"="+col+"+? WHERE user_id=?")){p.setInt(1,n);p.setLong(2,u);p.executeUpdate();}}
    private boolean owned(Connection c,long u,String h)throws SQLException{try(PreparedStatement p=c.prepareStatement("SELECT 1 FROM arena_hero WHERE user_id=? AND hero_id=?")){p.setLong(1,u);p.setString(2,h);try(ResultSet r=p.executeQuery()){return r.next();}}}
    private Map<String,Object> read(Connection c,long u)throws SQLException{Map<String,Object> o=new LinkedHashMap<>();try(PreparedStatement p=c.prepareStatement("SELECT * FROM arena_player WHERE user_id=?")){p.setLong(1,u);try(ResultSet r=p.executeQuery()){r.next();for(String k:new String[]{"liquid","coins","fate","stones","pity","dungeon_cleared","dungeon_attempts","formation_level","grotto_level"})o.put(camel(k),r.getLong(k));String claimed=r.getString("claimed"),activityClaimed=r.getString("activity_claimed");Map<String,Object> tasks=new LinkedHashMap<>();int activity=0;for(ArenaTaskCatalog.Task spec:ArenaTaskCatalog.tasks().values()){String id=spec.id;Map<String,Object> t=new LinkedHashMap<>();int progress=r.getInt("task_"+id);t.put("progress",progress);t.put("claimed",has(claimed,id));tasks.put(id,t);if(progress>=spec.target)activity+=spec.activity;}o.put("tasks",tasks);o.put("activity",activity);o.put("activityClaimed",Arrays.asList(activityClaimed.split(",")));}}List<Map<String,Object>> hs=new ArrayList<>();try(PreparedStatement p=c.prepareStatement("SELECT * FROM arena_hero WHERE user_id=? ORDER BY hero_id")){p.setLong(1,u);try(ResultSet r=p.executeQuery()){while(r.next()){Map<String,Object> h=new LinkedHashMap<>();h.put("id",r.getString("hero_id"));h.put("rank",r.getInt("rank"));h.put("stars",r.getInt("stars"));h.put("skill",r.getInt("skill_level"));h.put("shards",r.getInt("shards"));hs.add(h);}}}o.put("heroes",hs);o.put("teamPower",teamPower(c,u));return o;}
    private String camel(String s){StringBuilder b=new StringBuilder();boolean up=false;for(char c:s.toCharArray())if(c=='_')up=true;else{b.append(up?Character.toUpperCase(c):c);up=false;}return b.toString();}
}
