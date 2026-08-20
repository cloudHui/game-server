package com.cloud.web.arena;

import org.springframework.stereotype.Service;
import web.account.AccountDatabase;
import web.arena.ArenaRepository;
import web.arena.repository.ArenaInventoryRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ArenaAdminService {
    private static final Set<String> RESOURCES=new HashSet<>(Arrays.asList("liquid","coins","fate","stones"));
    private final AccountDatabase db;private final ArenaRepository arena;private final ArenaInventoryRepository inventory;private final ArenaJourneyService journey;
    public ArenaAdminService(AccountDatabase db,ArenaRepository arena,ArenaInventoryRepository inventory,ArenaJourneyService journey){this.db=db;this.arena=arena;this.inventory=inventory;this.journey=journey;}
    public List<Map<String,Object>> players()throws SQLException{List<Map<String,Object>> out=new ArrayList<>();try(Connection c=db.getConnection();PreparedStatement p=c.prepareStatement("SELECT u.id,u.username,u.nickname,a.liquid,a.coins,a.fate,a.stones,a.dungeon_cleared,a.formation_level FROM user u LEFT JOIN arena_player a ON a.user_id=u.id ORDER BY u.id");ResultSet r=p.executeQuery()){while(r.next()){Map<String,Object> x=new LinkedHashMap<>();x.put("userId",r.getLong("id"));x.put("username",r.getString("username"));x.put("nickname",r.getString("nickname"));x.put("initialized",r.getObject("liquid")!=null);x.put("liquid",r.getLong("liquid"));x.put("coins",r.getLong("coins"));x.put("fate",r.getLong("fate"));x.put("stones",r.getLong("stones"));x.put("dungeonCleared",r.getInt("dungeon_cleared"));x.put("formationLevel",r.getInt("formation_level"));out.add(x);}}return out;}
    public Map<String,Object> detail(long userId)throws SQLException{Map<String,Object>s=arena.state(userId);inventory.seed(userId);s.put("inventory",inventory.list(userId));s.put("journey",journey.state(userId));return s;}
    public Map<String,Object> adjustItem(long userId,String itemId,int delta)throws SQLException{if(itemId==null||!itemId.matches("[a-z0-9_-]{1,32}"))throw new IllegalArgumentException("物品 ID 非法");inventory.seed(userId);try(Connection c=db.getConnection()){try(PreparedStatement p=c.prepareStatement("INSERT OR IGNORE INTO arena_item(user_id,item_id,quantity) VALUES(?,?,0)")){p.setLong(1,userId);p.setString(2,itemId);p.executeUpdate();}try(PreparedStatement p=c.prepareStatement("UPDATE arena_item SET quantity=max(0,quantity+?) WHERE user_id=? AND item_id=?")){p.setInt(1,delta);p.setLong(2,userId);p.setString(3,itemId);p.executeUpdate();}}return detail(userId);}
    public Map<String,Object> adjust(long userId,String resource,long delta)throws SQLException{if(!RESOURCES.contains(resource))throw new IllegalArgumentException("未知资源");arena.state(userId);try(Connection c=db.getConnection();PreparedStatement p=c.prepareStatement("UPDATE arena_player SET "+resource+"=max(0,"+resource+"+?) WHERE user_id=?")){p.setLong(1,delta);p.setLong(2,userId);p.executeUpdate();}return arena.state(userId);}
    public Map<String,Object> hero(long userId,String heroId,int rank,int stars,int skill,int shards)throws SQLException{if(heroId==null||!heroId.matches("[a-z0-9_-]{1,32}"))throw new IllegalArgumentException("仙侣 ID 非法");arena.state(userId);try(Connection c=db.getConnection();PreparedStatement p=c.prepareStatement("INSERT INTO arena_hero(user_id,hero_id,rank,stars,skill_level,shards) VALUES(?,?,?,?,?,?) ON CONFLICT(user_id,hero_id) DO UPDATE SET rank=excluded.rank,stars=excluded.stars,skill_level=excluded.skill_level,shards=excluded.shards")){p.setLong(1,userId);p.setString(2,heroId);p.setInt(3,clamp(rank,1,80));p.setInt(4,clamp(stars,1,5));p.setInt(5,clamp(skill,1,100));p.setInt(6,Math.max(0,shards));p.executeUpdate();}return arena.state(userId);}
    private int clamp(int n,int min,int max){return Math.max(min,Math.min(max,n));}
}
