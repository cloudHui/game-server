package com.cloud.hub.game.inspector;

import com.cloud.hub.game.Game;
import com.cloud.hub.game.domain.cards.Card;
import com.cloud.hub.game.domain.cards.CardFormatter;
import com.cloud.hub.game.domain.ddz.DdzTable;
import com.cloud.hub.game.domain.mj.MjTable;
import com.cloud.hub.game.domain.pdk.PdkTable;
import com.cloud.hub.game.domain.table.Table;
import com.cloud.hub.game.domain.table.TableUser;
import com.cloud.hub.game.domain.tractor.TractorTable;
import com.cloud.hub.game.manager.TableManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import utils.registry.enums.TableState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 牌桌实时状态透视与巡检服务。
 * <p>
 * 提供外部牌桌列表概览（支持桌号快速过滤）与单牌局全景深度透视（含手牌按牌局理牌规范排序、底牌/牌山透视与麻将换牌状态联动）。
 * </p>
 *
 * @author cloud
 */
@Service
public class TableInspectorService {

    private static final Logger logger = LoggerFactory.getLogger(TableInspectorService.class);

    private final MjTileCheatService mjTileCheatService;

    public TableInspectorService(MjTileCheatService mjTileCheatService) {
        this.mjTileCheatService = mjTileCheatService;
    }

    /**
     * 巡检活跃牌桌概览列表，支持可选的桌号精确检索。
     *
     * @param queryTableId 可选桌号过滤，null 表示不过滤
     * @return 牌桌概览列表
     */
    public List<Map<String, Object>> listAllTablesOverview(Long queryTableId) {
        TableManager tm = Game.getInstance().getTableManager();
        if (tm == null) return Collections.emptyList();

        List<Table> tables = tm.getAllTables();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Table table : tables) {
            if (table == null) continue;
            if (queryTableId != null && table.getTableId() != queryTableId) {
                continue;
            }
            try {
                result.add(buildTableOverview(table));
            } catch (Exception e) {
                logger.error("构建牌桌概览异常, tableId: {}", table.getTableId(), e);
            }
        }
        return result;
    }

    public List<Map<String, Object>> listAllTablesOverview() {
        return listAllTablesOverview(null);
    }

    /**
     * 获取指定牌桌的完整上帝视角透视详情。
     *
     * @param tableId 桌号
     * @return 完整透视字典，不存在返回 null
     */
    public Map<String, Object> getTableDetail(long tableId) {
        TableManager tm = Game.getInstance().getTableManager();
        if (tm == null) return null;

        Table table = tm.getTable(tableId);
        if (table == null) return null;

        return buildTableDetail(table);
    }

    private Map<String, Object> buildTableOverview(Table table) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("tableId", table.getTableId());
        int gameType = table.getTableModel() != null ? table.getTableModel().getType() : table.getGameType();
        map.put("gameType", gameType);
        map.put("gameTypeName", table.getGameDisplayName());
        map.put("roomId", table.getTableModel() != null ? table.getTableModel().getId() : 0);
        map.put("ownerId", table.getOwnerId());

        TableState state = table.getTableState();
        map.put("stateCode", state != null ? state.getId() : 0);
        map.put("stateName", state != null ? state.name() : "UNKNOWN");
        map.put("stateDesc", state != null ? state.getDes() : "未知状态");

        map.put("currentRound", table.getCurrentRound());
        map.put("totalRounds", table.getTableModel() != null ? table.getTableModel().getTotalRounds() : 1);
        map.put("currOpSeat", table.getOp() != null ? table.getOp().getCurrOpSeat() : -1);

        Map<Integer, TableUser> seatUsers = table.getSeatUsers();
        map.put("playerCount", seatUsers != null ? seatUsers.size() : 0);

        List<String> playerSummary = new ArrayList<>();
        if (seatUsers != null) {
            for (Map.Entry<Integer, TableUser> entry : seatUsers.entrySet()) {
                TableUser u = entry.getValue();
                if (u != null) {
                    playerSummary.add("座位" + entry.getKey() + ": " + (u.getNick() != null ? u.getNick() : u.getUserId())
                            + "(" + u.getCards().size() + "张" + (u.isRobot() ? ",机" : "") + ")");
                }
            }
        }
        map.put("players", playerSummary);
        long elapsedSec = (System.currentTimeMillis() - table.getStateStartTime()) / 1000;
        map.put("durationSeconds", Math.max(0, elapsedSec));
        return map;
    }

    private Map<String, Object> buildTableDetail(Table table) {
        Map<String, Object> detail = buildTableOverview(table);
        int gameType = (int) detail.get("gameType");

        // 1. 玩家手牌精准理牌排序展示
        List<Map<String, Object>> playerDetails = new ArrayList<>();
        Map<Integer, TableUser> seatUsers = table.getSeatUsers();
        if (seatUsers != null) {
            for (Map.Entry<Integer, TableUser> entry : seatUsers.entrySet()) {
                int seat = entry.getKey();
                TableUser user = entry.getValue();
                if (user == null) continue;

                Map<String, Object> p = new LinkedHashMap<>();
                p.put("seat", seat);
                p.put("userId", user.getUserId());
                p.put("nick", user.getNick());
                p.put("online", user.isOnline());
                p.put("robot", user.isRobot());
                p.put("score", table.getGameResult() != null ? table.getGameResult().getTotalScore(seat) : 0);

                // 使用与牌局内一致的专用理牌器进行排序
                List<Card> cards = new ArrayList<>(user.getCards());
                CardSortUtil.sortCards(gameType, cards);

                List<Map<String, Object>> cardList = new ArrayList<>();
                for (Card c : cards) {
                    cardList.add(CardFormatter.toCardMap(gameType, c.getId()));
                }
                p.put("cardCount", cards.size());
                p.put("cards", cardList);
                playerDetails.add(p);
            }
        }
        detail.put("playerDetails", playerDetails);

        // 2. 牌堆、底牌与特殊上下文透视
        Map<String, Object> deckInfo = new LinkedHashMap<>();
        if (table instanceof DdzTable) {
            buildDdzDeckInfo((DdzTable) table, gameType, deckInfo);
        } else if (table instanceof MjTable) {
            buildMjDeckInfo((MjTable) table, gameType, deckInfo);
            // 附带当前麻将桌换牌预设状态
            detail.put("mjCheatStatus", mjTileCheatService.getCheatStatus(table.getTableId()));
        } else if (table instanceof TractorTable) {
            buildTractorDeckInfo((TractorTable) table, gameType, deckInfo);
        } else if (table instanceof PdkTable) {
            buildPdkDeckInfo((PdkTable) table, gameType, deckInfo);
        }
        detail.put("deckInfo", deckInfo);

        return detail;
    }

    private void buildDdzDeckInfo(DdzTable table, int gameType, Map<String, Object> deckInfo) {
        deckInfo.put("type", "ddz");
        deckInfo.put("landlordSeat", table.getDdz().getLandlordSeat());
        List<Map<String, Object>> bottom = new ArrayList<>();
        if (table.getCardPool() != null) {
            for (Card c : table.getCardPool().getBottomCards()) {
                bottom.add(CardFormatter.toCardMap(gameType, c.getId()));
            }
        }
        deckInfo.put("bottomCards", bottom);
    }

    private void buildMjDeckInfo(MjTable table, int gameType, Map<String, Object> deckInfo) {
        deckInfo.put("type", "mj");
        deckInfo.put("dealerSeat", table.getMjContext().getDealerSeat());
        int laiZi = table.getMjContext().getLaiZiTileId();
        deckInfo.put("laiZiTile", laiZi > 0 ? CardFormatter.toCardMap(gameType, laiZi) : null);

        if (table.getMjTilePool() != null) {
            deckInfo.put("remainingCount", table.getMjTilePool().remaining());
            List<Map<String, Object>> wall = new ArrayList<>();
            for (Integer tileId : table.getMjTilePool().getWallTiles()) {
                wall.add(CardFormatter.toCardMap(gameType, tileId));
            }
            deckInfo.put("wallTiles", wall);
        }
    }

    private void buildTractorDeckInfo(TractorTable table, int gameType, Map<String, Object> deckInfo) {
        deckInfo.put("type", "tractor");
        deckInfo.put("bankerSeat", table.getTractor().getBankerSeat());
        deckInfo.put("trumpSuit", table.getTractor().getTrumpSuit());
        List<Map<String, Object>> bottom = new ArrayList<>();
        if (table.getCardPool() != null) {
            for (Card c : table.getCardPool().getBottomCards()) {
                bottom.add(CardFormatter.toCardMap(gameType, c.getId()));
            }
        }
        deckInfo.put("bottomCards", bottom);
    }

    private void buildPdkDeckInfo(PdkTable table, int gameType, Map<String, Object> deckInfo) {
        deckInfo.put("type", "pdk");
        deckInfo.put("firstSeat", table.getOp() != null ? table.getOp().getCurrOpSeat() : -1);
        List<Map<String, Object>> pool = new ArrayList<>();
        if (table.getCardPool() != null) {
            for (Card c : table.getCardPool().getPoolCards()) {
                pool.add(CardFormatter.toCardMap(gameType, c.getId()));
            }
        }
        deckInfo.put("remainingCards", pool);
        deckInfo.put("remainingCount", pool.size());
    }
}
