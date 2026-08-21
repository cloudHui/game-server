package com.cloud.hub.web.arena.repository;

import org.springframework.stereotype.Repository;import com.cloud.hub.web.account.AccountDatabase;import javax.annotation.PostConstruct;import java.sql.*;import java.util.*;

import org.springframework.stereotype.Repository;
import com.cloud.hub.web.account.AccountDatabase;

import javax.annotation.PostConstruct;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
@Repository public class ArenaInventoryRepository{
 private final AccountDatabase db;public ArenaInventoryRepository(AccountDatabase db){this.db=db;}@PostConstruct public void init(){try(Connection c=db.getConnection();Statement s=c.createStatement()){s.execute("CREATE TABLE IF NOT EXISTS arena_item(user_id INTEGER NOT NULL,item_id TEXT NOT NULL,quantity INTEGER NOT NULL DEFAULT 0,PRIMARY KEY(user_id,item_id))");}catch(SQLException e){throw new IllegalStateException("初始化剑气背包失败",e);}}
 public List<Map<String,Object>> list(long uid)throws SQLException{List<Map<String,Object>>o=new ArrayList<>();try(Connection c=db.getConnection();PreparedStatement p=c.prepareStatement("SELECT item_id,quantity FROM arena_item WHERE user_id=? AND quantity>0 ORDER BY item_id")){p.setLong(1,uid);try(ResultSet r=p.executeQuery()){while(r.next()){Map<String,Object>x=new LinkedHashMap<>();x.put("id",r.getString(1));x.put("quantity",r.getInt(2));o.add(x);}}}return o;}
 public void seed(long uid)throws SQLException{try(Connection c=db.getConnection();PreparedStatement p=c.prepareStatement("INSERT OR IGNORE INTO arena_item(user_id,item_id,quantity) VALUES(?,?,?)")){String[] ids={"herb","ore","star_dust"};int[] n={12,12,6};for(int i=0;i<ids.length;i++){p.setLong(1,uid);p.setString(2,ids[i]);p.setInt(3,n[i]);p.addBatch();}p.executeBatch();}}
 public synchronized void craft(long uid,String input,int need,String output,int gain,int coins)throws SQLException{try(Connection c=db.getConnection()){c.setAutoCommit(false);try(PreparedStatement p=c.prepareStatement("UPDATE arena_player SET coins=coins-? WHERE user_id=? AND coins>=?")){p.setInt(1,coins);p.setLong(2,uid);p.setInt(3,coins);if(p.executeUpdate()!=1)throw new IllegalArgumentException("灵币不足");}try(PreparedStatement p=c.prepareStatement("UPDATE arena_item SET quantity=quantity-? WHERE user_id=? AND item_id=? AND quantity>=?")){p.setInt(1,need);p.setLong(2,uid);p.setString(3,input);p.setInt(4,need);if(p.executeUpdate()!=1)throw new IllegalArgumentException("材料不足");}try(PreparedStatement p=c.prepareStatement("INSERT INTO arena_item(user_id,item_id,quantity) VALUES(?,?,?) ON CONFLICT(user_id,item_id) DO UPDATE SET quantity=quantity+excluded.quantity")){p.setLong(1,uid);p.setString(2,output);p.setInt(3,gain);p.executeUpdate();}c.commit();}}
}
