package game.manager.table.cards;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import game.manager.table.TableUser;
import proto.GameProto;

/** 扑克类出牌：从 OpInfo 取牌、校验手牌、组装 CardInfo。 */
public final class CardOps {

	private CardOps() {}

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

	/** 按 id 多重集从手牌取出对应 Card（不移除）；不足返回 null。 */
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
