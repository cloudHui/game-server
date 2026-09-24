package com.cloud.hub.game.inspector;

import com.cloud.hub.game.Game;
import com.cloud.hub.game.domain.cards.CardFormatter;
import com.cloud.hub.game.domain.mj.MjTable;
import com.cloud.hub.game.domain.mj.card.MjTilePool;
import com.cloud.hub.game.domain.table.Table;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 麻将牌墙调试换牌服务。
 * <p>
 * 遵循严格的物理牌墙守恒与目标玩家任务机制：
 * <ul>
 *   <li>仅允许从牌墙中真实存在的未摸牌中进行换牌，数量绝对守恒；</li>
 *   <li>支持指定目标玩家座位（如 "0 摸 1万" 或 "draw 1万"）；</li>
 *   <li>若指令到达时该玩家本轮已摸牌，指令持续挂起等待下一轮轮到该玩家摸牌时自动生效；</li>
 *   <li>若该牌在轮到前已耗尽，指令自动作废并报警。</li>
 * </ul>
 * </p>
 *
 * @author cloud
 */
@Service
public class MjTileCheatService {

    private static final Logger logger = LoggerFactory.getLogger(MjTileCheatService.class);
    private static final Map<String, Integer> NAME_TO_ID = new HashMap<>();

    static {
        initTileNames();
    }

    private static void initTileNames() {
        for (int i = 1; i <= 9; i++) {
            NAME_TO_ID.put(i + "万", 100 + i);
            NAME_TO_ID.put(i + "条", 200 + i);
            NAME_TO_ID.put(i + "筒", 300 + i);
        }
        NAME_TO_ID.put("东风", 401);
        NAME_TO_ID.put("南风", 402);
        NAME_TO_ID.put("西风", 403);
        NAME_TO_ID.put("北风", 404);
        NAME_TO_ID.put("红中", 501);
        NAME_TO_ID.put("发财", 502);
        NAME_TO_ID.put("白板", 503);
    }

    /**
     * 解析并执行麻将换牌指令。
     *
     * @param tableId 桌号
     * @param cmd     指令（如 "0 draw 1万", "gang 红中", "seat 1 摸 5条", "clear"）
     * @return 执行反馈消息
     */
    public String executeCommand(long tableId, String cmd) {
        if (cmd == null || cmd.trim().isEmpty()) {
            return "错误：命令不能为空";
        }
        MjTilePool pool = getTilePool(tableId);
        if (pool == null) {
            return "错误：桌号 " + tableId + " 不是进行中的麻将牌桌";
        }

        String raw = cmd.trim();
        if ("clear".equalsIgnoreCase(raw) || "重置".equals(raw)) {
            pool.clearCheatCues();
            return "已成功清除桌号 " + tableId + " 的所有控牌换牌预设";
        }

        // 解析命令参数
        String[] parts = raw.split("\\s+");
        int targetSeat = -1;
        String action = "";
        String tileStr = "";

        if (parts.length >= 3 && ("seat".equalsIgnoreCase(parts[0]) || parts[0].matches("\\d+"))) {
            // 如 "seat 0 draw 1万" 或 "0 draw 1万"
            int pIndex = 0;
            if ("seat".equalsIgnoreCase(parts[0])) {
                pIndex = 1;
            }
            try {
                targetSeat = Integer.parseInt(parts[pIndex]);
                pIndex++;
            } catch (NumberFormatException e) {
                return "错误：无法识别的座位号 '" + parts[pIndex] + "'";
            }
            if (pIndex < parts.length) action = parts[pIndex++];
            if (pIndex < parts.length) tileStr = parts[pIndex];
        } else if (parts.length >= 2) {
            // 如 "draw 1万" 或 "0 1万"
            if (parts[0].matches("\\d+")) {
                targetSeat = Integer.parseInt(parts[0]);
                action = "draw";
                tileStr = parts[1];
            } else {
                action = parts[0];
                tileStr = parts[1];
            }
        } else {
            return "错误：格式应为 '[座位] draw <牌>' 或 '[座位] gang <牌>'，如: '0 摸 1万' 或 'gang 红中'";
        }

        boolean isGang = "gang".equalsIgnoreCase(action) || "杠".equals(action);
        boolean isDraw = "draw".equalsIgnoreCase(action) || "摸".equals(action);
        if (!isGang && !isDraw) {
            return "错误：仅支持摸牌(draw/摸)或杠牌(gang/杠)，收到: '" + action + "'";
        }

        int tileId = parseTile(tileStr);
        if (tileId <= 0) {
            return "错误：无法识别的麻将牌 '" + tileStr + "'";
        }

        // 核心安全校验：必须是当前牌墙真实存在的未摸余牌！数量守恒！
        int remainingInWall = pool.countTileInWall(tileId);
        String tileName = CardFormatter.formatMahjong(tileId);
        if (remainingInWall <= 0) {
            return "设置失败：当前牌墙中已无牌 [" + tileName + "]（剩余 0 张），无法凭空凭造，必须选择未摸的余牌！";
        }

        // 创建挂起任务
        MjCheatCue cue = new MjCheatCue(targetSeat, tileId, isGang);
        pool.addCheatCue(cue);

        String seatDesc = (targetSeat < 0) ? "全局下一个人" : ("座位 " + targetSeat);
        String actionDesc = isGang ? "杠牌补摸" : "普通摸牌";
        logger.info("后台成功挂起麻将控牌指令: tableId={}, targetSeat={}, isGang={}, tileId={}, wallCount={}",
                tableId, targetSeat, isGang, tileId, remainingInWall);

        return "控牌指令已挂起：" + seatDesc + " 的" + actionDesc + "预设为 [" + tileName + "] (牌墙现有余牌: "
                + remainingInWall + "张)。若本轮已摸过牌，将在该玩家下一次摸牌时自动从牌墙调配并物理扣除！";
    }

    /**
     * 查询指定桌号的待执行控牌任务队列与牌墙余牌存量。
     *
     * @param tableId 桌号
     * @return 预设状态字典
     */
    public Map<String, Object> getCheatStatus(long tableId) {
        Map<String, Object> map = new LinkedHashMap<>();
        MjTilePool pool = getTilePool(tableId);
        if (pool == null) {
            return map;
        }

        // 1. 待生效指令列表
        List<Map<String, Object>> cuesList = new ArrayList<>();
        for (MjCheatCue cue : pool.getPendingCues()) {
            int remaining = pool.countTileInWall(cue.getTargetTileId());
            cuesList.add(cue.toMap(remaining));
        }
        map.put("pendingCues", cuesList);

        // 2. 牌墙余牌各牌存量统计
        Map<String, Integer> inventory = new LinkedHashMap<>();
        for (Integer tileId : pool.getWallTiles()) {
            if (tileId != null) {
                String name = CardFormatter.formatMahjong(tileId);
                inventory.put(name, inventory.getOrDefault(name, 0) + 1);
            }
        }
        map.put("wallInventory", inventory);
        map.put("totalWallRemaining", pool.remaining());
        return map;
    }

    private MjTilePool getTilePool(long tableId) {
        Table table = Game.getInstance().getTableManager().getTable(tableId);
        if (table instanceof MjTable) {
            return ((MjTable) table).getMjTilePool();
        }
        return null;
    }

    private int parseTile(String token) {
        try {
            return Integer.parseInt(token);
        } catch (NumberFormatException ignored) {
            return NAME_TO_ID.getOrDefault(token.trim(), -1);
        }
    }
}
