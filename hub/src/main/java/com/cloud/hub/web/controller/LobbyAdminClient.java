package com.cloud.hub.web.controller;

import com.cloud.hub.web.account.AccountDatabase;
import com.cloud.hub.web.account.AccountService;
import com.cloud.hub.web.account.AccountUser;
import com.cloud.hub.game.manager.TableInspectorService;
import com.cloud.hub.lobby.manager.UserManager;
import com.cloud.hub.lobby.manager.table.TableManager;
import model.tablemodel.TableModel;
import model.tablemodel.TableModelJson;
import model.tablemodel.RobotRoomTemplates;
import com.cloud.hub.game.Game;
import proto.ModelProto;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 大厅与管理后台进程内调用适配客户端。
 * <p>
 * 为管理后台提供邀请码维护、玩家启停、牌桌巡检、机器人对局压测验收以及积分记录查询。
 *
 * @author cloud
 */
@Component
public class LobbyAdminClient {

    private static final AtomicInteger ROBOT_ID = new AtomicInteger(-2000000000);
    private final AccountDatabase database;
    private final AccountService accounts;
    private final TableInspectorService tableInspectorService;

    public LobbyAdminClient(AccountDatabase database, AccountService accounts, TableInspectorService tableInspectorService) {
        this.database = database;
        this.accounts = accounts;
        this.tableInspectorService = tableInspectorService;
    }

    public Map<String, Object> listInvites(String token) {
        if (!admin(token)) {
            return error(401, "需要 admin 登录");
        }
        List<Map<String, Object>> invites = new ArrayList<>();
        try (Connection c = database.getConnection();
             Statement s = c.createStatement();
             ResultSet r = s.executeQuery("SELECT * FROM invite ORDER BY id DESC")) {
            while (r.next()) {
                invites.add(mapInviteRow(r));
            }
            return ok("invites", invites);
        } catch (Exception e) {
            return error(500, e.getMessage());
        }
    }

    private Map<String, Object> mapInviteRow(ResultSet r) throws Exception {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", r.getLong("id"));
        row.put("token", r.getString("token"));
        row.put("note", r.getString("note"));
        row.put("createdBy", r.getString("created_by"));
        row.put("createdAt", r.getLong("created_at"));
        long expires = r.getLong("expires_at");
        row.put("expiresAt", r.wasNull() ? null : expires);
        row.put("maxUses", r.getInt("max_uses"));
        row.put("usedCount", r.getInt("used_count"));
        row.put("enabled", r.getInt("enabled") == 1);
        return row;
    }

    public Map<String, Object> createInvite(String token, Map<String, Object> body) {
        AccountUser admin = adminUser(token);
        if (admin == null) {
            return error(401, "需要 admin 登录");
        }
        int maxUses = integer(body.get("maxUses"), 1);
        int days = integer(body.get("expiresDays"), 7);
        long now = System.currentTimeMillis();
        Long expires = days > 0 ? now + days * 86400000L : null;
        String invite = UUID.randomUUID().toString().replace("-", "");
        return insertInvite(admin.username, invite, string(body.get("note")), now, expires, maxUses);
    }

    private Map<String, Object> insertInvite(String adminUsername, String invite, String note,
                                             long now, Long expires, int maxUses) {
        String sql = "INSERT INTO invite(token,note,created_by,created_at,expires_at,max_uses,used_count,enabled) VALUES(?,?,?,?,?,?,0,1)";
        try (Connection c = database.getConnection();
             PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, invite);
            p.setString(2, note);
            p.setString(3, adminUsername);
            p.setLong(4, now);
            if (expires == null) {
                p.setNull(5, java.sql.Types.BIGINT);
            } else {
                p.setLong(5, expires);
            }
            p.setInt(6, Math.max(1, maxUses));
            p.executeUpdate();

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("code", 0);
            Map<String, Object> created = new LinkedHashMap<>();
            created.put("token", invite);
            created.put("expiresAt", expires);
            created.put("maxUses", Math.max(1, maxUses));
            result.put("invite", created);
            return result;
        } catch (Exception e) {
            return error(500, e.getMessage());
        }
    }

    public Map<String, Object> revokeInvite(String token, Map<String, Object> body) {
        return updateInvite(token, body, false);
    }

    public Map<String, Object> reactivateInvite(String token, Map<String, Object> body) {
        if (!admin(token)) {
            return error(401, "需要 admin 登录");
        }
        String invite = string(body.get("token"));
        int days = integer(body.get("expiresDays"), 7);
        int uses = Math.max(1, integer(body.get("additionalUses"), 1));
        Long expires = days > 0 ? System.currentTimeMillis() + days * 86400000L : null;
        String sql = "UPDATE invite SET enabled=1,expires_at=?,max_uses=max_uses+? WHERE token=?";
        try (Connection c = database.getConnection();
             PreparedStatement p = c.prepareStatement(sql)) {
            if (expires == null) {
                p.setNull(1, java.sql.Types.BIGINT);
            } else {
                p.setLong(1, expires);
            }
            p.setInt(2, uses);
            p.setString(3, invite);
            return p.executeUpdate() == 1 ? ok() : error(404, "邀请码不存在");
        } catch (Exception e) {
            return error(500, e.getMessage());
        }
    }

    public Map<String, Object> listUsers(String token) {
        if (!admin(token)) {
            return error(401, "需要 admin 登录");
        }
        List<Map<String, Object>> users = new ArrayList<>();
        for (AccountUser user : accounts.listUsers()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", user.id);
            row.put("username", user.username);
            row.put("nickname", user.nickname);
            row.put("enabled", user.enabled);
            row.put("createdAt", user.createdAt);
            row.put("lastLoginAt", user.lastLoginAt);
            users.add(row);
        }
        return ok("users", users);
    }

    public Map<String, Object> enableUser(String token, Map<String, Object> body) {
        if (!admin(token)) {
            return error(401, "需要 admin 登录");
        }
        long id = longValue(body.get("userId"), -1);
        boolean enabled = Boolean.parseBoolean(string(body.get("enabled")));
        try (Connection c = database.getConnection();
             PreparedStatement p = c.prepareStatement("UPDATE user SET enabled=? WHERE id=?")) {
            p.setInt(1, enabled ? 1 : 0);
            p.setLong(2, id);
            return p.executeUpdate() == 1 ? ok() : error(404, "用户不存在");
        } catch (Exception e) {
            return error(500, e.getMessage());
        }
    }

    public Map<String, Object> listTables(String token) {
        if (!admin(token)) {
            return error(401, "需要 admin 登录");
        }
        List<Map<String, Object>> tables = tableInspectorService.listAllTablesOverview();
        Map<String, Object> result = ok("tables", tables);
        // 兼容前端旧 rooms 字段与在线人数统计
        result.put("rooms", tables);
        result.put("onlineUsers", UserManager.getInstance().getUserCount());
        return result;
    }

    public Map<String, Object> getTableDetail(String token, long tableId) {
        if (!admin(token)) {
            return error(401, "需要 admin 登录");
        }
        Map<String, Object> detail = tableInspectorService.getTableDetail(tableId);
        if (detail == null) {
            return error(404, "桌子不存在或已解散");
        }
        return ok("table", detail);
    }

    public Map<String, Object> createRobotMatch(String token, Map<String, Object> body) {
        if (!admin(token)) {
            return error(401, "需要 admin 登录");
        }
        int roomId = integer(body.get("roomId"), RobotRoomTemplates.MAHJONG_ROOM_ID);
        TableModel source = TableManager.getInstance().getTableModel(roomId);
        if (source == null || !RobotRoomTemplates.isRobotRoom(roomId)) {
            return error(400, "不支持的机器人验收玩法");
        }
        try {
            return startRobotMatch(roomId, source, body);
        } catch (IllegalArgumentException e) {
            return error(400, e.getMessage());
        } catch (Exception e) {
            return error(500, "启动失败: " + e.getMessage());
        }
    }

    private Map<String, Object> startRobotMatch(int roomId, TableModel source, Map<String, Object> body) throws Exception {
        Map<String, String> values = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : body.entrySet()) {
            if (entry.getValue() != null) {
                values.put(entry.getKey(), String.valueOf(entry.getValue()));
            }
        }
        TableModel model = AdminRobotMatchRules.create(source, values);
        model.setId(30000 + roomId);
        TableManager.getInstance().putRuntimeModel(model, "admin-robot-test");
        int robotId = ROBOT_ID.incrementAndGet();
        ModelProto.RoomRole role = ModelProto.RoomRole.newBuilder().setRoleId(robotId)
                .setNickName(com.google.protobuf.ByteString.copyFromUtf8("管理员验收"))
                .setAvatar(com.google.protobuf.ByteString.copyFromUtf8("TMJSON:" + TableModelJson.toJson(model)))
                .build();
        com.cloud.hub.game.domain.table.Table table = Game.getInstance().getTableManager()
                .createTableAsync(model.getId(), role).get(5, TimeUnit.SECONDS);
        table.execute("管理后台填充机器人", table::fillRobotSeats).get(5, TimeUnit.SECONDS);
        ModelProto.RoomTableInfo info = ModelProto.RoomTableInfo.newBuilder()
                .setTableId(table.getTableId()).setRoomId(model.getId()).setGameType(model.getType())
                .setCreatorId(robotId).setOwnerId(robotId).addTableRoles(role).build();
        TableManager.getInstance().putRoomInfo(info);

        Map<String, Object> result = ok();
        result.put("tableId", table.getTableId());
        result.put("roomId", model.getId());
        result.put("gameType", model.getType());
        return result;
    }

    public Map<String, Object> createCustomRoom(String token, Map<String, Object> body) {
        AccountUser user = accounts.findByToken(token).orElse(null);
        if (user == null || !user.enabled) {
            return error(401, "需要登录");
        }
        int gameType = integer(body.get("gameType"), 1);
        if (gameType != 1 && gameType != 2) {
            return error(400, "gameType 须为 1麻将 或 2斗地主");
        }
        try {
            TableManager manager = TableManager.getInstance();
            int modelId = manager.nextRuntimeModelId();
            TableModel model = TableModelJson.parse(rulesJson(body, gameType, modelId));
            if (model == null) {
                return error(400, "规则无效");
            }
            model.setId(modelId);
            model.setType(gameType);
            manager.putRuntimeModel(model, user.username);

            Map<String, Object> result = ok();
            result.put("roomId", modelId);
            result.put("gameType", gameType);
            return result;
        } catch (Exception e) {
            return error(500, e.getMessage());
        }
    }

    public List<Map<String, Object>> listRecords(String token, int page, int size) {
        if (!admin(token)) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        String sql = "SELECT * FROM score_record ORDER BY created_at DESC LIMIT ? OFFSET ?";
        try (Connection c = database.getConnection(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setInt(1, Math.min(Math.max(size, 1), 100));
            p.setInt(2, Math.max(0, (page - 1) * size));
            try (ResultSet r = p.executeQuery()) {
                while (r.next()) {
                    rows.add(mapRecordRow(r));
                }
            }
        } catch (Exception ignored) {
        }
        return rows;
    }

    private Map<String, Object> mapRecordRow(ResultSet r) throws Exception {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("tableId", r.getLong("table_id"));
        row.put("roomId", r.getInt("room_id"));
        row.put("gameType", r.getInt("game_type"));
        row.put("round", r.getInt("round"));
        row.put("userId", r.getInt("user_id"));
        row.put("seat", r.getInt("seat"));
        row.put("score", r.getInt("score"));
        row.put("totalScore", r.getInt("total_score"));
        row.put("createdAt", r.getLong("created_at"));
        return row;
    }

    private Map<String, Object> updateInvite(String token, Map<String, Object> body, boolean enabled) {
        if (!admin(token)) {
            return error(401, "需要 admin 登录");
        }
        try (Connection c = database.getConnection();
             PreparedStatement p = c.prepareStatement("UPDATE invite SET enabled=? WHERE token=?")) {
            p.setInt(1, enabled ? 1 : 0);
            p.setString(2, string(body.get("token")));
            return p.executeUpdate() == 1 ? ok() : error(404, "邀请码不存在");
        } catch (Exception e) {
            return error(500, e.getMessage());
        }
    }

    private boolean admin(String token) {
        return adminUser(token) != null;
    }

    private AccountUser adminUser(String token) {
        AccountUser user = accounts.findByToken(token).orElse(null);
        return user != null && "admin".equals(user.username) && user.enabled ? user : null;
    }

    private static Map<String, Object> ok() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", 0);
        m.put("msg", "ok");
        return m;
    }

    private static Map<String, Object> ok(String key, Object value) {
        Map<String, Object> m = ok();
        m.put(key, value);
        return m;
    }

    private static Map<String, Object> error(int code, String msg) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", code);
        m.put("msg", msg == null ? "error" : msg);
        return m;
    }

    private static String string(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static int integer(Object value, int fallback) {
        try {
            return Integer.parseInt(string(value));
        } catch (Exception e) {
            return fallback;
        }
    }

    private static long longValue(Object value, long fallback) {
        try {
            return Long.parseLong(string(value));
        } catch (Exception e) {
            return fallback;
        }
    }

    private static String rulesJson(Map<String, Object> b, int type, int id) {
        StringBuilder s = new StringBuilder("{\"id\":").append(id).append(",\"type\":").append(type);
        append(s, "seatNum", integer(b.get("seatNum"), 3));
        append(s, "cardNum", integer(b.get("cardNum"), type == 2 ? 17 : 13));
        append(s, "exCardNum", integer(b.get("exCardNum"), type == 2 ? 3 : 0));
        append(s, "baseScore", integer(b.get("baseScore"), 1));
        append(s, "maxFan", integer(b.get("maxFan"), 16));
        append(s, "allowChi", integer(b.get("allowChi"), 1));
        append(s, "allowDianPao", integer(b.get("allowDianPao"), 1));
        append(s, "allowPeng", integer(b.get("allowPeng"), 1));
        append(s, "allowGang", integer(b.get("allowGang"), 1));
        append(s, "allowHu", integer(b.get("allowHu"), 1));
        append(s, "allowSevenPairs", integer(b.get("allowSevenPairs"), 1));
        append(s, "gameSubType", integer(b.get("gameSubType"), 0));
        append(s, "gangScore", integer(b.get("gangScore"), 1));
        append(s, "allowGangMing", integer(b.get("allowGangMing"), 1));
        append(s, "allowGangAn", integer(b.get("allowGangAn"), 1));
        append(s, "allowGangBu", integer(b.get("allowGangBu"), 1));
        append(s, "totalRounds", integer(b.get("totalRounds"), 4));
        append(s, "autoNextRound", integer(b.get("autoNextRound"), 0));
        append(s, "autoPlay", integer(b.get("autoPlay"), 0));
        append(s, "allowMultiHu", integer(b.get("allowMultiHu"), 0));
        return s.append('}').toString();
    }

    private static void append(StringBuilder s, String key, int value) {
        s.append(",\"").append(key).append("\":").append(value);
    }

    /**
     * 管理员机器人测试的规则覆盖助手。
     */
    private static final class AdminRobotMatchRules {
        private AdminRobotMatchRules() {
        }

        static TableModel create(TableModel source, Map<String, String> input) {
            TableModel model = TableModelJson.parse(TableModelJson.toJson(source));
            if (model == null) {
                throw new IllegalArgumentException("规则无效");
            }
            model.setTotalRounds(value(input, "totalRounds", 3, 1, 20));
            model.setBaseScore(value(input, "baseScore", source.getBaseScore(), 1, 100));
            model.setMaxFan(value(input, "maxFan", source.getMaxFan(), 1, 128));
            if (model.getType() == 1) {
                model.setAllowChi(flag(input, "allowChi", source.getAllowChi()));
                model.setAllowDianPao(flag(input, "allowDianPao", source.getAllowDianPao()));
                model.setAllowGang(flag(input, "allowGang", source.getAllowGang()));
                model.setAllowSevenPairs(flag(input, "allowSevenPairs", source.getAllowSevenPairs()));
                model.setAllowMultiHu(flag(input, "allowMultiHu", source.getAllowMultiHu()));
            }
            model.setAutoNextRound(1);
            model.setAutoPlay(1);
            return model;
        }

        private static int flag(Map<String, String> input, String key, int fallback) {
            return value(input, key, fallback, 0, 1);
        }

        private static int value(Map<String, String> input, String key, int fallback, int min, int max) {
            String raw = input.get(key);
            int value;
            try {
                value = raw == null ? fallback : Integer.parseInt(raw);
            } catch (NumberFormatException error) {
                throw new IllegalArgumentException(key + " 格式错误");
            }
            if (value < min || value > max) {
                throw new IllegalArgumentException(key + " 超出范围");
            }
            return value;
        }
    }
}

