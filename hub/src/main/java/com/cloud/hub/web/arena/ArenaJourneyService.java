package com.cloud.hub.web.arena;

import com.cloud.hub.game.arena.journey.JourneyRules;
import org.springframework.stereotype.Service;
import com.cloud.hub.web.account.AccountDatabase;
import com.cloud.hub.web.arena.repository.ArenaInventoryRepository;

import javax.annotation.PostConstruct;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
@Service public class ArenaJourneyService{private final AccountDatabase db;private final ArenaInventoryRepository items;public ArenaJourneyService(AccountDatabase db,ArenaInventoryRepository items){this.db=db;this.items=items;}@PostConstruct public void init(){try(Connection c=db.getConnection();Statement s=c.createStatement()){s.execute("CREATE TABLE IF NOT EXISTS arena_journey(user_id INTEGER PRIMARY KEY,stamina INTEGER NOT NULL DEFAULT 120,max_map INTEGER NOT NULL DEFAULT 1,updated_at INTEGER NOT NULL DEFAULT 0)");}catch(Exception e){throw new IllegalStateException(e);}}public Map<String,Object> state(long u)throws SQLException{ensure(u);try(Connection c=db.getConnection();PreparedStatement p=c.prepareStatement("SELECT stamina,max_map FROM arena_journey WHERE user_id=?")){p.setLong(1,u);try(ResultSet r=p.executeQuery()){r.next();Map<String,Object>m=new LinkedHashMap<>();m.put("stamina",r.getInt(1));m.put("maxMap",r.getInt(2));m.put("items",items.list(u));return m;}}}public synchronized Map<String,Object> explore(long u,int map,int runs)throws SQLException{ensure(u);Map<String,Integer> rewards=JourneyRules.rewards(map,runs);int cost=JourneyRules.staminaCost(runs);try(Connection c=db.getConnection()){c.setAutoCommit(false);try(PreparedStatement p=c.prepareStatement("UPDATE arena_journey SET stamina=stamina-?,max_map=max(max_map,?) WHERE user_id=? AND stamina>=? AND max_map>=?")){p.setInt(1,cost);p.setInt(2,Math.min(6,map+1));p.setLong(3,u);p.setInt(4,cost);p.setInt(5,map);if(p.executeUpdate()!=1)throw new IllegalArgumentException("体力不足或地图未解锁");}for(Map.Entry<String,Integer>e:rewards.entrySet())try(PreparedStatement p=c.prepareStatement("INSERT INTO arena_item(user_id,item_id,quantity) VALUES(?,?,?) ON CONFLICT(user_id,item_id) DO UPDATE SET quantity=quantity+excluded.quantity")){p.setLong(1,u);p.setString(2,e.getKey());p.setInt(3,e.getValue());p.executeUpdate();}c.commit();}return state(u);}private void ensure(long u)throws SQLException{items.seed(u);try(Connection c=db.getConnection();PreparedStatement p=c.prepareStatement("INSERT OR IGNORE INTO arena_journey(user_id) VALUES(?)")){p.setLong(1,u);p.executeUpdate();}}}
