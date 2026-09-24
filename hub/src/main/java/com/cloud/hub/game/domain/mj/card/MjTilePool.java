package com.cloud.hub.game.domain.mj.card;

import com.cloud.hub.game.domain.table.Table;
import com.cloud.hub.game.domain.table.TableUser;
import com.cloud.hub.game.domain.cards.Card;
import msg.registor.message.GMsg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import proto.GameProto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 麻将牌墙管理
 * 负责初始化牌墙、洗牌、发牌、摸牌
 */
public class MjTilePool {

    private static final Logger logger = LoggerFactory.getLogger(MjTilePool.class);

    private final List<Integer> wallTiles = new ArrayList<>();
    private final Table table;
    /**
     * 兼容旧接口：卡五星等显式花色；null 时按座位规则由 MjWallComposer 编排
     */
    private int[] allowedSuits;

    public MjTilePool(Table table) {
        this.table = table;
    }

    /**
     * 初始化牌墙并洗牌(全花色或按桌子配置)
     */
    public void initTiles() {
        initTiles(allowedSuits);
    }

    /**
     * 初始化牌墙并洗牌。
     * allowedSuits 非空时按指定花色（卡五星）；否则按 seatNum 规则编排。
     *
     * @param allowedSuits 允许的花色数组, null表示按座位规则
     */
    public void initTiles(int[] allowedSuits) {
        this.allowedSuits = allowedSuits;
        wallTiles.clear();
        if (allowedSuits != null && allowedSuits.length > 0) {
            fillBySuits(allowedSuits);
        } else {
            wallTiles.addAll(MjWallComposer.compose(table.getTableModel()));
        }
        Collections.shuffle(wallTiles);
        logger.info("麻将牌墙初始化完成, 总数: {}, seatNum: {}", wallTiles.size(),
                table.getTableModel().getSeatNum());
    }

    private void fillBySuits(int[] suits) {
        for (int suit : suits) {
            int maxVal = suit <= MjConst.SUIT_TONG ? MjConst.NUM_COUNT
                    : (suit == MjConst.SUIT_FENG ? MjConst.FENG_COUNT : MjConst.JIAN_COUNT);
            for (int val = 1; val <= maxVal; val++) {
                int tileId = MjConst.encode(suit, val);
                for (int copy = 0; copy < MjConst.COPY_COUNT; copy++) {
                    wallTiles.add(tileId);
                }
            }
        }
    }

    /**
     * 设置允许的花色(在dealInitTiles之前调用)；卡五星用。
     */
    public void setAllowedSuits(int[] allowedSuits) {
        this.allowedSuits = allowedSuits;
    }

    public int[] getAllowedSuits() {
        return allowedSuits;
    }

    /**
     * 待执行的换牌/控牌指令列表（支持指定目标座位与杠牌类型）
     */
    private final List<com.cloud.hub.game.inspector.MjCheatCue> cheatCues = new ArrayList<>();

    public synchronized void addCheatCue(com.cloud.hub.game.inspector.MjCheatCue newCue) {
        cheatCues.removeIf(c -> c.isPending() && c.getTargetSeat() == newCue.getTargetSeat() && c.isGang() == newCue.isGang());
        cheatCues.add(newCue);
    }

    public synchronized List<com.cloud.hub.game.inspector.MjCheatCue> getPendingCues() {
        List<com.cloud.hub.game.inspector.MjCheatCue> list = new ArrayList<>();
        for (com.cloud.hub.game.inspector.MjCheatCue c : cheatCues) {
            if (c.isPending()) {
                list.add(c);
            }
        }
        return list;
    }

    public synchronized void clearCheatCues() {
        cheatCues.clear();
    }

    /**
     * 统计指定牌在当前未摸牌墙中的剩余数量。
     *
     * @param tileId 牌 ID
     * @return 牌墙中的实际剩余张数
     */
    public synchronized int countTileInWall(int tileId) {
        int count = 0;
        for (Integer id : wallTiles) {
            if (id != null && id == tileId) {
                count++;
            }
        }
        return count;
    }

    /**
     * 摸一张牌，支持指定摸牌座位与是否杠牌，严格依据牌墙现有余牌生效指令并物理扣除。
     *
     * @param seat   当前摸牌玩家座位号（-1 表示未知）
     * @param isGang 是否为杠牌后的补摸
     * @return 摸到的牌 ID，-1 表示牌墙已空
     */
    public synchronized int drawTile(int seat, boolean isGang) {
        if (wallTiles.isEmpty()) {
            return -1;
        }

        // 查找是否有匹配当前座位与摸牌类型的待生效控牌指令
        com.cloud.hub.game.inspector.MjCheatCue matchedCue = null;
        for (com.cloud.hub.game.inspector.MjCheatCue cue : cheatCues) {
            if (cue.isPending() && (cue.getTargetSeat() == -1 || cue.getTargetSeat() == seat) && cue.isGang() == isGang) {
                matchedCue = cue;
                break;
            }
        }

        if (matchedCue != null) {
            int tileId = matchedCue.getTargetTileId();
            int idx = wallTiles.indexOf(tileId);
            if (idx >= 0) {
                // 牌墙中确实存在该牌：真实扣除物理牌，数量 100% 绝对守恒，指令完成
                wallTiles.remove(idx);
                matchedCue.setStatus("COMPLETED");
                logger.info("麻将控牌指令精准生效: seat={}, isGang={}, 摸得牌={}, 牌墙剩余={}",
                        seat, isGang, tileId, wallTiles.size());
                return tileId;
            } else {
                // 牌墙中该牌已在此前的摸牌中耗尽：指令失效作废，恢复自然摸牌
                matchedCue.setStatus("EXHAUSTED");
                logger.warn("麻将控牌指令失效(牌墙已无此牌): seat={}, isGang={}, 目标牌={}, 恢复自然摸牌",
                        seat, isGang, tileId);
            }
        }

        // 无匹配指令或指令失效：从牌墙尾部自然摸牌
        return wallTiles.remove(wallTiles.size() - 1);
    }

    public synchronized int drawTile(boolean isGang) {
        return drawTile(-1, isGang);
    }

    public synchronized int drawTile() {
        return drawTile(-1, false);
    }

    /**
     * 牌墙剩余牌数
     */
    public int remaining() {
        return wallTiles.size();
    }

    /**
     * 获取牌墙中所有剩余牌的只读列表（用于调试与后台上帝视角透视）。
     *
     * @return 牌墙余牌 ID 列表
     */
    public List<Integer> getWallTiles() {
        return Collections.unmodifiableList(new ArrayList<>(wallTiles));
    }

    /**
     * 发初始手牌(每人13张)，并通知每个玩家自己的手牌
     */
    public void dealInitTiles() {
        initTiles(allowedSuits);
        Map<Integer, TableUser> seatUsers = table.getSeatUsers();
        int seatNum = table.getTableModel().getSeatNum();

        TreeMap<Integer, TableUser> ordered = new TreeMap<>(seatUsers);
        for (int round = 0; round < MjConst.INIT_HAND; round++) {
            for (int s = 0; s < seatNum; s++) {
                TableUser u = ordered.get(s);
                if (u != null) {
                    u.addCards(new Card(drawTile()));
                }
            }
        }

        sendHandNotice(seatUsers);
        logger.info("麻将发牌完成, tableId: {}, 剩余: {}", table.getTableId(), remaining());
    }

    /**
     * 通知所有玩家手牌(自己的牌有值，别人的牌值为0)
     */
    public void sendHandNotice(Map<Integer, TableUser> seatUsers) {
        for (Map.Entry<Integer, TableUser> entry : seatUsers.entrySet()) {
            TableUser sendUser = entry.getValue();
            GameProto.NotCard.Builder builder = GameProto.NotCard.newBuilder();
            for (Map.Entry<Integer, TableUser> userEntry : seatUsers.entrySet()) {
                TableUser otherUser = userEntry.getValue();
                GameProto.NCardsInfo.Builder nCards = GameProto.NCardsInfo.newBuilder()
                        .setRoleId(otherUser.getUserId());
                boolean owner = otherUser.equals(sendUser);
                for (Card card : otherUser.getCards()) {
                    nCards.addCards(GameProto.Card.newBuilder()
                            .setValue(owner ? card.getId() : 0).build());
                }
                builder.addNCards(nCards.build());
            }
            sendUser.sendRoleMessage(builder.build(), GMsg.NOT_CARD, table.getTableId());
        }
    }
}
