package com.cloud.hub.game.domain.cards;

import com.cloud.hub.game.domain.table.TableUser;
import proto.GameProto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 扑克类出牌操作通用辅助工具。
 * <p>
 * 负责从 Protobuf 的 OpInfo 中提取扑克牌 ID 列表、
 * 将牌列表序列化为 CardInfo，以及校验并从玩家手牌中提取出牌子集。
 *
 * @author cloud
 */
public final class CardOps {

    private CardOps() {
    }

    /**
     * 从操作信息 OpInfo 中提取所有包含的卡牌 ID 列表。
     *
     * @param opInfo 操作信息协议对象
     * @return 提取出的牌 ID 集合
     */
    public static List<Integer> collectIds(GameProto.OpInfo opInfo) {
        List<Integer> ids = new ArrayList<>();
        if (opInfo == null) return ids;
        for (GameProto.CardInfo ci : opInfo.getOpCardsList()) {
            for (GameProto.Card c : ci.getCardsList()) ids.add(c.getValue());
        }
        return ids;
    }

    public static GameProto.CardInfo toCardInfo(List<Card> cards) {
        GameProto.CardInfo.Builder ci = GameProto.CardInfo.newBuilder();
        if (cards != null) {
            for (Card c : cards) ci.addCards(GameProto.Card.newBuilder().setValue(c.getId()));
        }
        return ci.build();
    }

    /**
     * 按 id 多重集从手牌取出对应 Card（不移除）；不足返回 null。
     */
    public static List<Card> pullFromHand(TableUser user, List<Integer> ids) {
        if (user == null || ids == null || ids.isEmpty()) return null;
        Map<Integer, Integer> need = new HashMap<>();
        for (int id : ids) need.merge(id, 1, Integer::sum);
        Map<Integer, Integer> have = new HashMap<>();
        for (Card c : user.getCards()) have.merge(c.getId(), 1, Integer::sum);
        for (Map.Entry<Integer, Integer> e : need.entrySet()) {
            if (have.getOrDefault(e.getKey(), 0) < e.getValue()) return null;
        }
        List<Card> out = new ArrayList<>();
        Map<Integer, Integer> left = new HashMap<>(need);
        for (Card c : user.getCards()) {
            Integer n = left.get(c.getId());
            if (n != null && n > 0) {
                out.add(c);
                left.put(c.getId(), n - 1);
            }
        }
        return out.size() == ids.size() ? out : null;
    }
}
