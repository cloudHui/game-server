package com.cloud.hub.web.handler;

import com.google.protobuf.Message;
import msg.registor.message.GMsg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import proto.GameProto;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 将网关推送的 Protobuf 数据结构序列化为前端可直接识别的 WebSocket JSON 结构。
 * <p>
 * 基于 {@link PushMapping} 方法注解在类加载时自动反射扫描注册，解耦连接管理与协议转换。
 *
 * @author cloud
 */
public final class GameWsPushFormatter {

    private static final Logger logger = LoggerFactory.getLogger(GameWsPushFormatter.class);

    private GameWsPushFormatter() {
    }

    /** 推送消息格式化函数接口 */
    @FunctionalInterface
    private interface PushFormatter {
        Object format(Message proto);
    }

    /** 单条注册项 */
    private static final class PushEntry {
        final String action;
        final PushFormatter formatter;

        PushEntry(String action, PushFormatter formatter) {
            this.action = action;
            this.formatter = formatter;
        }
    }

    /** msgId 到推送处理实体的映射表 */
    private static final Map<Integer, PushEntry> PUSH_REGISTRY = new HashMap<>();

    static {
        initPushMappings();
    }

    /**
     * 反射扫描所有带有 @PushMapping 的格式化方法并注册。
     */
    private static void initPushMappings() {
        for (Method method : GameWsPushFormatter.class.getDeclaredMethods()) {
            PushMapping mapping = method.getAnnotation(PushMapping.class);
            if (mapping == null) {
                continue;
            }
            method.setAccessible(true);
            registerMapping(method, mapping);
        }
        logger.info("GameWsPushFormatter 自动绑定完成，注册项数量: {}", PUSH_REGISTRY.size());
    }

    /**
     * 注册单个方法的映射关系。
     */
    private static void registerMapping(Method method, PushMapping mapping) {
        Class<?> paramType = method.getParameterTypes().length > 0 ? method.getParameterTypes()[0] : null;
        PushFormatter formatter = proto -> {
            if (paramType != null && !paramType.isInstance(proto)) {
                logger.warn("推送消息类型不匹配: method={}, expected={}, actual={}",
                        method.getName(), paramType.getSimpleName(), proto != null ? proto.getClass().getSimpleName() : "null");
                return Collections.emptyMap();
            }
            try {
                return method.invoke(null, proto);
            } catch (Exception e) {
                logger.error("执行推送格式化失败: {}", method.getName(), e);
                return Collections.emptyMap();
            }
        };
        if (mapping.values().length > 0) {
            for (int msgId : mapping.values()) {
                PUSH_REGISTRY.put(msgId, new PushEntry(mapping.action(), formatter));
            }
        } else if (mapping.value() != 0) {
            PUSH_REGISTRY.put(mapping.value(), new PushEntry(mapping.action(), formatter));
        }
    }

    /**
     * 获取指定消息 ID 对应的 action 动作名。
     *
     * @param msgId 协议消息 ID
     * @return 对应的 action 名，未注册返回 null
     */
    public static String pushAction(int msgId) {
        PushEntry entry = PUSH_REGISTRY.get(msgId);
        return entry != null ? entry.action : null;
    }

    /**
     * 将网关 Protobuf 消息转为前端 JSON 字典对象。
     *
     * @param msgId 消息 ID
     * @param proto Protobuf 对象
     * @return 转换后的字典对象
     */
    public static Object formatPush(int msgId, Message proto) {
        PushEntry entry = PUSH_REGISTRY.get(msgId);
        if (entry == null || proto == null) {
            return new HashMap<>();
        }
        return entry.formatter.format(proto);
    }

    @PushMapping(value = GMsg.ACK_ENTER_TABLE_MSG, action = "seatUpdate")
    private static Map<String, Object> formatEnterTable(GameProto.AckEnterTable ack) {
        Map<String, Object> m = new HashMap<>();
        m.put("players", formatPlayers(ack.getPlayersList(), 0));
        if (ack.hasTableInfo()) {
            m.put("tableInfo", formatTableInfo(ack.getTableInfo()));
        }
        return m;
    }

    @PushMapping(values = {GMsg.NOT_STATE, GMsg.NOT_TABLE_STATE}, action = "notState")
    private static Map<String, Object> formatNotTableState(GameProto.NotTableState state) {
        Map<String, Object> m = new HashMap<>();
        m.put("state", state.getState());
        m.put("currentRound", state.getCurrentRound());
        m.put("totalRounds", state.getTotalRounds());
        return m;
    }

    /**
     * 格式化玩家列表展示数据。
     */
    public static List<Map<String, Object>> formatPlayers(List<GameProto.Player> players, int currentRoleId) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (GameProto.Player player : players) {
            Map<String, Object> p = new HashMap<>();
            p.put("roleId", player.getRoleId());
            p.put("position", player.getPosition());
            p.put("nickName", player.getNickName().toStringUtf8());
            p.put("cardCount", player.getCardsCount());
            p.put("robot", player.getRoleId() < 0);
            if (currentRoleId != 0 && player.getRoleId() == currentRoleId && player.getCardsCount() > 0) {
                List<Integer> cardValues = new ArrayList<>();
                for (GameProto.Card card : player.getCardsList()) {
                    cardValues.add(card.getValue());
                }
                p.put("cards", cardValues);
            }
            result.add(p);
        }
        return result;
    }

    /**
     * 格式化桌况基础信息。
     */
    public static Map<String, Object> formatTableInfo(GameProto.TableInfo tableInfo) {
        Map<String, Object> result = new HashMap<>();
        result.put("roomId", tableInfo.getRoomId());
        result.put("tableId", tableInfo.getTableId());
        result.put("landlord", tableInfo.getLandlord());
        result.put("currentRound", tableInfo.getCurrentRound());
        result.put("totalRounds", tableInfo.getTotalRounds());
        return result;
    }

    /**
     * 格式化牌桌完整快照（拆分子方法，单方法行数 <= 30）。
     */
    public static Map<String, Object> formatSnapshot(GameProto.AckTableSnapshot n) {
        Map<String, Object> m = new HashMap<>();
        m.put("tableId", n.getTableId());
        m.put("gameType", n.getGameType());
        m.put("version", n.getVersion());
        m.put("round", n.getRound());
        m.put("state", n.getState());
        m.put("stateStart", n.getStateStart());
        m.put("stateDuration", n.getStateDuration());
        m.put("opSeat", n.getOpSeat());
        m.put("choices", formatOpChoices(n.getChoicesList()));
        m.put("players", formatSnapshotPlayers(n.getPlayersList()));
        m.put("discards", formatSnapshotDiscards(n.getDiscardsList()));
        m.put("exposed", formatSnapshotExposed(n.getExposedList()));
        fillSnapshotDetails(m, n);
        return m;
    }

    private static List<Map<String, Object>> formatSnapshotPlayers(List<GameProto.SnapshotPlayer> list) {
        List<Map<String, Object>> players = new ArrayList<>();
        for (GameProto.SnapshotPlayer p : list) {
            Map<String, Object> x = new HashMap<>();
            x.put("roleId", p.getRoleId());
            x.put("position", p.getSeat());
            x.put("nickName", p.getNick().toStringUtf8());
            x.put("online", p.getOnline());
            x.put("totalScore", p.getTotalScore());
            x.put("cardCount", p.getCardCount());
            x.put("cards", new ArrayList<>(p.getCardsList()));
            players.add(x);
        }
        return players;
    }

    private static List<Map<String, Object>> formatSnapshotDiscards(List<GameProto.SnapshotDiscard> list) {
        List<Map<String, Object>> discards = new ArrayList<>();
        for (GameProto.SnapshotDiscard d : list) {
            Map<String, Object> x = new HashMap<>();
            x.put("seat", d.getSeat());
            x.put("tileId", d.getTileId());
            x.put("sequence", d.getSequence());
            discards.add(x);
        }
        return discards;
    }

    private static List<Map<String, Object>> formatSnapshotExposed(List<GameProto.SnapshotExposed> list) {
        List<Map<String, Object>> exposed = new ArrayList<>();
        for (GameProto.SnapshotExposed e : list) {
            Map<String, Object> x = new HashMap<>();
            x.put("seat", e.getSeat());
            x.put("type", e.getType().toStringUtf8());
            x.put("tileIds", new ArrayList<>(e.getTileIdsList()));
            exposed.add(x);
        }
        return exposed;
    }

    private static void fillSnapshotDetails(Map<String, Object> m, GameProto.AckTableSnapshot n) {
        m.put("drawnTile", n.getDrawnTile());
        m.put("pendingDiscardTile", n.getPendingDiscardTile());
        m.put("pendingDiscardSeat", n.getPendingDiscardSeat());
        m.put("wallLeft", n.getWallLeft());
        m.put("laiziTile", n.getLaiziTile());
        m.put("laiziFlipTile", n.getLaiziFlipTile());
        m.put("dealerSeat", n.getDealerSeat());
        m.put("bottomCards", new ArrayList<>(n.getBottomCardsList()));
        List<Integer> lastCards = new ArrayList<>();
        for (GameProto.Card c : n.getLastCards().getCardsList()) {
            lastCards.add(c.getValue());
        }
        m.put("lastCards", lastCards);
        m.put("lastPlaySeat", n.getLastPlaySeat());
        m.put("passSeats", new ArrayList<>(n.getPassSeatsList()));
        m.put("landlordSeat", n.getLandlordSeat());
        m.put("baseScore", n.getBaseScore());
        m.put("robMultiplier", n.getRobMultiplier());
        m.put("bombMultiplier", n.getBombMultiplier());
        m.put("currentMultiplier", n.getCurrentMultiplier());
    }

    @PushMapping(value = GMsg.ACK_OP, action = "ackOp")
    private static Map<String, Object> formatAckOp(GameProto.AckOp ack) {
        Map<String, Object> m = new HashMap<>();
        m.put("opId", ack.getOpId());
        m.put("opFrom", ack.getOpFrom());
        if (ack.hasOp()) {
            m.put("choice", ack.getOp().getChoiceValue());
            List<Integer> cards = new ArrayList<>();
            for (GameProto.CardInfo cardInfo : ack.getOp().getOpCardsList()) {
                for (GameProto.Card card : cardInfo.getCardsList()) {
                    cards.add(card.getValue());
                }
            }
            m.put("cards", cards);
        }
        m.put("baseScore", ack.getBaseScore());
        m.put("robMultiplier", ack.getRobMultiplier());
        m.put("bombMultiplier", ack.getBombMultiplier());
        m.put("currentMultiplier", ack.getCurrentMultiplier());
        return m;
    }

    @PushMapping(value = GMsg.NOT_CARD, action = "notCard")
    private static Map<String, Object> formatNotCard(GameProto.NotCard n) {
        Map<String, Object> m = new HashMap<>();
        List<Map<String, Object>> nCards = new ArrayList<>();
        for (GameProto.NCardsInfo info : n.getNCardsList()) {
            Map<String, Object> c = new HashMap<>();
            c.put("roleId", info.getRoleId());
            List<Map<String, Object>> cards = new ArrayList<>();
            for (GameProto.Card card : info.getCardsList()) {
                Map<String, Object> cv = new HashMap<>();
                cv.put("value", card.getValue());
                cards.add(cv);
            }
            c.put("cards", cards);
            nCards.add(c);
        }
        m.put("nCards", nCards);
        return m;
    }

    @PushMapping(value = GMsg.NOT_OP, action = "notOp")
    private static Map<String, Object> formatNotOp(GameProto.NotOperation n) {
        Map<String, Object> m = new HashMap<>();
        m.put("opSeat", n.getOpSeat());
        m.put("wait", n.getWait());
        m.put("choice", formatOpChoices(n.getChoiceList()));
        return m;
    }

    @PushMapping(value = GMsg.NOT_RESULT, action = "notResult")
    private static Map<String, Object> formatNotResult(GameProto.NotResult n) {
        Map<String, Object> m = new HashMap<>();
        m.put("winner", n.getWinner());
        m.put("landlord_id", n.getLandlordId());
        m.put("win_team", n.getWinTeam());
        m.put("base_score", n.getBaseScore());
        m.put("rob_multiplier", n.getRobMultiplier());
        m.put("spring", n.getSpring());
        m.put("anti_spring", n.getAntiSpring());
        m.put("settle_factor", n.getSettleFactor());
        List<Map<String, Object>> players = new ArrayList<>();
        for (GameProto.RPlayer p : n.getRPlayersList()) {
            Map<String, Object> rp = new HashMap<>();
            rp.put("roleId", p.getRoleId());
            List<Integer> cards = new ArrayList<>();
            for (GameProto.Card c : p.getCardsList()) {
                cards.add(c.getValue());
            }
            rp.put("cards", cards);
            players.add(rp);
        }
        m.put("rPlayers", players);
        return m;
    }

    @PushMapping(value = GMsg.MJ_TILE_NOT, action = "notMjState")
    private static Map<String, Object> formatNotMjState(GameProto.NotMjState n) {
        Map<String, Object> m = new HashMap<>();
        m.put("opSeat", n.getOpSeat());
        m.put("tileId", n.getTileId());
        m.put("action", n.getActionValue());
        m.put("wait", n.getWait());
        m.put("wallLeft", n.getWallLeft());
        m.put("fromSeat", n.getFromSeat());
        m.put("exposedTiles", new ArrayList<>(n.getExposedTilesList()));
        m.put("choice", formatOpChoices(n.getChoiceList()));
        return m;
    }

    private static List<Map<String, Object>> formatOpChoices(List<GameProto.OpInfo> ops) {
        List<Map<String, Object>> choices = new ArrayList<>();
        for (GameProto.OpInfo op : ops) {
            Map<String, Object> c = new HashMap<>();
            c.put("choice", op.getChoiceValue());
            List<Map<String, Object>> cards = new ArrayList<>();
            for (GameProto.CardInfo cardInfo : op.getOpCardsList()) {
                for (GameProto.Card card : cardInfo.getCardsList()) {
                    Map<String, Object> cv = new HashMap<>();
                    cv.put("value", card.getValue());
                    cards.add(cv);
                }
            }
            if (!cards.isEmpty()) {
                c.put("cards", cards);
            }
            choices.add(c);
        }
        return choices;
    }

    @PushMapping(value = GMsg.NOT_ROUND_RESULT, action = "notRoundResult")
    private static Map<String, Object> formatNotRoundResult(GameProto.NotRoundResult n) {
        Map<String, Object> m = new HashMap<>();
        m.put("round", n.getRound());
        m.put("winnerSeat", n.getWinnerSeat());
        m.put("fan", n.getFan());
        m.put("winType", n.getWinType().toStringUtf8());
        m.put("winTile", n.getWinTile());
        m.put("seatScores", formatSeatScores(n.getSeatScoresList()));
        m.put("totalScores", formatSeatScores(n.getTotalScoresList()));
        m.put("hands", formatHands(n.getHandsList()));
        m.put("seatExposed", formatSeatExposed(n.getSeatExposedList()));
        return m;
    }

    private static List<Map<String, Object>> formatSeatScores(List<GameProto.SeatScore> list) {
        List<Map<String, Object>> scores = new ArrayList<>();
        for (GameProto.SeatScore s : list) {
            Map<String, Object> sc = new HashMap<>();
            sc.put("seat", s.getSeat());
            sc.put("score", s.getScore());
            scores.add(sc);
        }
        return scores;
    }

    private static List<Map<String, Object>> formatHands(List<GameProto.HandInfo> list) {
        List<Map<String, Object>> hands = new ArrayList<>();
        for (GameProto.HandInfo h : list) {
            Map<String, Object> hi = new HashMap<>();
            hi.put("seat", h.getSeat());
            hi.put("handTiles", h.getHandTilesList());
            hi.put("exposed", formatExposedList(h.getExposedList()));
            hands.add(hi);
        }
        return hands;
    }

    private static List<Map<String, Object>> formatSeatExposed(List<GameProto.SeatExposed> list) {
        List<Map<String, Object>> seatExposed = new ArrayList<>();
        for (GameProto.SeatExposed se : list) {
            Map<String, Object> row = new HashMap<>();
            row.put("seat", se.getSeat());
            row.put("exposed", formatExposedList(se.getExposedList()));
            seatExposed.add(row);
        }
        return seatExposed;
    }

    private static List<Map<String, Object>> formatExposedList(List<GameProto.ExposedInfo> list) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (GameProto.ExposedInfo e : list) {
            Map<String, Object> one = new HashMap<>();
            one.put("type", e.getType().toStringUtf8());
            one.put("tileIds", e.getTileIdsList());
            out.add(one);
        }
        return out;
    }

    @PushMapping(value = GMsg.NOT_GAME_RESULT, action = "notGameResult")
    private static Map<String, Object> formatNotGameResult(GameProto.NotGameResult n) {
        Map<String, Object> m = new HashMap<>();
        m.put("totalRounds", n.getTotalRounds());
        m.put("completedRounds", n.getCompletedRounds());
        m.put("totalScores", formatSeatScores(n.getTotalScoresList()));
        List<Map<String, Object>> rounds = new ArrayList<>();
        for (GameProto.RoundSummary r : n.getRoundsList()) {
            Map<String, Object> rs = new HashMap<>();
            rs.put("round", r.getRound());
            rs.put("winnerSeat", r.getWinnerSeat());
            rs.put("fan", r.getFan());
            rs.put("winType", r.getWinType().toStringUtf8());
            rounds.add(rs);
        }
        m.put("rounds", rounds);
        return m;
    }
}
