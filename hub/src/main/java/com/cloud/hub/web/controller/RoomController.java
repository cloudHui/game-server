package com.cloud.hub.web.controller;

import com.cloud.hub.common.annotation.Log;
import com.cloud.hub.common.annotation.RequiresLogin;
import com.cloud.hub.common.core.domain.AjaxResult;
import com.cloud.hub.common.enums.BusinessType;
import com.cloud.hub.framework.security.LoginUser;
import com.cloud.hub.framework.security.SecurityUtils;
import com.cloud.hub.web.service.UserService;
import com.google.protobuf.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import proto.LobbyProto;
import proto.ModelProto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 房间与牌桌管理控制器。
 * <p>
 * 提供游戏大厅房间列表检索、加入已有桌子以及创建固定模板或自定义规则房间的能力。
 *
 * @author cloud
 */
@RestController
@RequestMapping("/api")
@RequiresLogin
public class RoomController {

    private static final Logger logger = LoggerFactory.getLogger(RoomController.class);

    private final UserService userService;
    private final LobbyAdminClient lobbyAdminClient;

    public RoomController(UserService userService, LobbyAdminClient lobbyAdminClient) {
        this.userService = userService;
        this.lobbyAdminClient = lobbyAdminClient;
    }

    /**
     * 获取房间列表。
     *
     * @param sessionId 可选会话标识
     * @return 房间数据列表
     */
    @GetMapping("/rooms")
    public AjaxResult getRooms(@RequestParam(required = false) String sessionId) {
        LoginUser user = SecurityUtils.getRequiredUser();
        String currentSessionId = (sessionId != null && !sessionId.isEmpty()) ? sessionId : user.getSessionId();
        try {
            CompletableFuture<Message> future = userService.getRoomList(currentSessionId);
            Message response = future.get(5, TimeUnit.SECONDS);
            if (response instanceof LobbyProto.AckRoomList) {
                AjaxResult result = AjaxResult.success();
                result.put("rooms", buildRoomListData((LobbyProto.AckRoomList) response, user.getUserId()));
                return result;
            }
            return AjaxResult.error(500, "获取房间列表失败");
        } catch (Exception e) {
            logger.error("获取房间列表异常, sessionId: {}", currentSessionId, e);
            return AjaxResult.error(500, "获取房间列表异常: " + e.getMessage());
        }
    }

    /**
     * 加入已有牌桌。
     *
     * @param request 包含 roomId 的请求载荷
     * @return 包含 tableId 的结果
     */
    @PostMapping("/rooms/join")
    @Log(title = "加入牌桌", businessType = BusinessType.OTHER)
    public AjaxResult joinTable(@RequestBody Map<String, Object> request) {
        LoginUser user = SecurityUtils.getRequiredUser();
        String sessionId = resolveSessionId(request, user);
        Number roomIdNum = (Number) request.get("roomId");
        if (roomIdNum == null) {
            return AjaxResult.error(400, "参数不完整");
        }
        int roomId = roomIdNum.intValue();
        return doJoinTable(user, sessionId, roomId);
    }

    private AjaxResult doJoinTable(LoginUser user, String sessionId, int roomId) {
        long startMs = System.currentTimeMillis();
        try {
            CompletableFuture<Message> future = userService.joinTable(sessionId, roomId);
            Message response = future.get(10, TimeUnit.SECONDS);
            if (response instanceof LobbyProto.AckJoinRoomTable) {
                LobbyProto.AckJoinRoomTable ack = (LobbyProto.AckJoinRoomTable) response;
                AjaxResult result = AjaxResult.success();
                result.put("tableId", ack.getTableId());
                return result;
            }
            return AjaxResult.error(500, "加入桌子失败");
        } catch (TimeoutException e) {
            logger.error("加入桌子超时, userId: {}, roomId: {}, costMs: {}",
                    user.getUserId(), roomId, System.currentTimeMillis() - startMs);
            return AjaxResult.error(500, "加入桌子超时");
        } catch (Exception e) {
            logger.error("加入桌子异常, userId: {}, roomId: {}, cause: {}",
                    user.getUserId(), roomId, e.toString(), e);
            return AjaxResult.error(500, "加入桌子异常: " + e.getMessage());
        }
    }

    /**
     * 创建房间：固定模板 或 自定义规则（拆分解析逻辑，所有方法 <= 30 行）。
     *
     * @param request 创建参数字典
     * @return 创房结果
     */
    @PostMapping("/rooms/create")
    @Log(title = "创建房间", businessType = BusinessType.INSERT)
    public AjaxResult createRoom(@RequestBody Map<String, Object> request) {
        LoginUser user = SecurityUtils.getRequiredUser();
        String sessionId = resolveSessionId(request, user);
        String mode = str(request.get("mode"));
        if (mode.isEmpty()) {
            mode = "fixed";
        }
        try {
            RoomCreation creation = resolveRoomCreation(request, mode, user);
            if (creation.error != null) {
                return creation.error;
            }
            return doCreateAndJoin(sessionId, creation.roomId, creation.gameType, mode);
        } catch (Exception e) {
            logger.error("创建房间异常, userId: {}", user.getUserId(), e);
            return AjaxResult.error(500, "创建房间异常: " + e.getMessage());
        }
    }

    private static class RoomCreation {
        int roomId;
        int gameType;
        AjaxResult error;
    }

    private RoomCreation resolveRoomCreation(Map<String, Object> request, String mode, LoginUser user) {
        RoomCreation c = new RoomCreation();
        if ("custom".equalsIgnoreCase(mode)) {
            return resolveCustomCreation(request, user);
        }
        Number roomIdNum = (Number) request.get("roomId");
        if (roomIdNum == null) {
            c.error = AjaxResult.error(400, "固定模板创房需要 roomId");
            return c;
        }
        c.roomId = roomIdNum.intValue();
        Number gt = (Number) request.get("gameType");
        c.gameType = gt != null ? gt.intValue() : 0;
        return c;
    }

    private RoomCreation resolveCustomCreation(Map<String, Object> request, LoginUser user) {
        RoomCreation c = new RoomCreation();
        Number gt = (Number) request.get("gameType");
        if (gt == null) {
            c.error = AjaxResult.error(400, "自定义创房需要 gameType");
            return c;
        }
        c.gameType = gt.intValue();
        Map<String, Object> payload = buildCustomPayload(request, c.gameType);
        Map<String, Object> prepared = lobbyAdminClient.createCustomRoom(user.getToken(), payload);
        if (prepared == null || !Integer.valueOf(0).equals(asInt(prepared.get("code")))) {
            c.error = prepared != null ? toAjax(prepared) : AjaxResult.error(502, "lobby 不可用");
            return c;
        }
        c.roomId = asInt(prepared.get("roomId"));
        return c;
    }

    private Map<String, Object> buildCustomPayload(Map<String, Object> request, int gameType) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("gameType", gameType);
        String[] keys = {"seatNum", "baseScore", "totalRounds", "maxFan", "allowChi",
                "allowDianPao", "allowPeng", "allowGang", "allowHu", "allowMultiHu",
                "autoPlay", "cardNum", "exCardNum"};
        for (String k : keys) {
            copyInt(request, payload, k);
        }
        return payload;
    }

    private AjaxResult doCreateAndJoin(String sessionId, int roomId, int gameType, String mode) throws Exception {
        CompletableFuture<Message> future = userService.joinTable(sessionId, roomId);
        Message response = future.get(10, TimeUnit.SECONDS);
        if (!(response instanceof LobbyProto.AckJoinRoomTable)) {
            return AjaxResult.error(500, "创建/加入失败");
        }
        LobbyProto.AckJoinRoomTable ack = (LobbyProto.AckJoinRoomTable) response;
        AjaxResult result = AjaxResult.success();
        result.put("tableId", ack.getTableId());
        result.put("roomId", roomId);
        result.put("gameType", gameType);
        result.put("mode", mode);
        return result;
    }

    private String resolveSessionId(Map<String, Object> request, LoginUser user) {
        return (request.get("sessionId") instanceof String) ? (String) request.get("sessionId") : user.getSessionId();
    }

    private static void copyInt(Map<String, Object> from, Map<String, Object> to, String key) {
        Object v = from.get(key);
        if (v instanceof Number) {
            to.put(key, ((Number) v).intValue());
        }
    }

    private static int asInt(Object o) {
        if (o instanceof Number) {
            return ((Number) o).intValue();
        }
        if (o == null) {
            return 0;
        }
        try {
            return Integer.parseInt(String.valueOf(o));
        } catch (Exception e) {
            return 0;
        }
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private AjaxResult toAjax(Map<String, Object> map) {
        AjaxResult result = new AjaxResult();
        result.putAll(map);
        return result;
    }

    private static List<Map<String, Object>> buildRoomListData(LobbyProto.AckRoomList ackRoomList, int currentUserId) {
        List<Map<String, Object>> rooms = new ArrayList<>();
        for (ModelProto.Room room : ackRoomList.getRoomListList()) {
            Map<String, Object> roomData = new HashMap<>();
            roomData.put("roomId", room.getRoomId());
            roomData.put("gameType", room.getGameType());

            List<Map<String, Object>> tables = new ArrayList<>();
            for (ModelProto.RoomTableInfo table : room.getTablesList()) {
                tables.add(buildTableData(table, currentUserId));
            }
            roomData.put("tables", tables);
            for (Map<String, Object> table : tables) {
                if (Boolean.TRUE.equals(table.get("mine"))) {
                    roomData.put("myTableId", table.get("tableId"));
                    break;
                }
            }
            rooms.add(roomData);
        }
        return rooms;
    }

    private static Map<String, Object> buildTableData(ModelProto.RoomTableInfo table, int currentUserId) {
        Map<String, Object> tableData = new HashMap<>();
        tableData.put("tableId", table.getTableId());
        tableData.put("stat", table.getStat());
        tableData.put("playerCount", table.getTableRolesCount());

        List<Map<String, Object>> players = new ArrayList<>();
        for (ModelProto.RoomRole role : table.getTableRolesList()) {
            Map<String, Object> playerData = new HashMap<>();
            playerData.put("roleId", role.getRoleId());
            playerData.put("nickName", role.getNickName().toStringUtf8());
            players.add(playerData);
        }
        tableData.put("players", players);
        tableData.put("mine", table.getTableRolesList().stream()
                .anyMatch(role -> role.getRoleId() == currentUserId));
        return tableData;
    }
}
