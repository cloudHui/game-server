package game.manager.table.tractor;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import game.manager.table.TableUser;
import game.manager.table.TractorTable;
import game.manager.table.cards.Card;
import game.manager.table.cards.CardOps;
import game.manager.table.tractor.ai.TractorSimpleAi;
import game.manager.table.replay.PokerReplayRecorder;
import msg.registor.enums.TableState;
import msg.registor.message.GMsg;
import proto.ConstProto;
import proto.GameProto;

/** 拖拉机出牌：跟牌、吃墩、抠底 */
public final class TractorPlayService {

	private static final Logger logger = LoggerFactory.getLogger(TractorPlayService.class);

	private TractorPlayService() {}

	public static int apply(TractorTable table, int userId, GameProto.OpInfo opInfo) {
		if (table.getTableState() != TableState.IDLE_CARD) return ConstProto.Result.OP_CURR_ERROR_VALUE;
		TableUser user = table.getUsers().get(userId);
		if (user == null || user.getSeated() != table.getOp().getCurrOpSeat()) {
			return ConstProto.Result.OP_CURR_ERROR_VALUE;
		}
		if (opInfo.getChoice() != ConstProto.Operation.PLAY) return ConstProto.Result.OP_CURR_ERROR_VALUE;
		return applyPlay(table, user, opInfo);
	}

	public static void autoPlay(TractorTable table, int seat) {
		TableUser user = table.getSeatUser(seat);
		if (user == null || user.getCards().isEmpty()) return;
		List<Card> play = TractorSimpleAi.decide(table, seat);
		if (play == null || play.isEmpty()) return;
		GameProto.CardInfo.Builder ci = GameProto.CardInfo.newBuilder();
		for (Card c : play) ci.addCards(GameProto.Card.newBuilder().setValue(c.getId()));
		GameProto.OpInfo op = GameProto.OpInfo.newBuilder()
				.setChoice(ConstProto.Operation.PLAY).addOpCards(ci.build()).build();
		apply(table, user.getUserId(), op);
	}

	private static int applyPlay(TractorTable table, TableUser user, GameProto.OpInfo opInfo) {
		TractorTableContext ctx = table.getTractor();
		List<Integer> ids = CardOps.collectIds(opInfo);
		if (ids.isEmpty()) return ConstProto.Result.OP_CARD_NOT_MATCH_VALUE;
		List<Card> selected = CardOps.pullFromHand(user, ids);
		if (selected == null) return ConstProto.Result.OP_CARD_NOT_MATCH_VALUE;

		int level = ctx.getLevelRank();
		int trump = ctx.getTrumpSuit();
		TractorRules.Combo lead = ctx.getLeadCombo();
		if (lead == null) {
			TractorRules.Combo parsed = TractorRules.analyze(selected, level, trump);
			if (parsed == null) {
				parsed = TractorRules.analyzeThrow(selected, level, trump);
				if (parsed == null) return ConstProto.Result.OP_CARD_NOT_MATCH_VALUE;
				if (!throwCanStand(table, user, selected, level, trump)) {
					Card smallest = smallestCard(selected, level, trump);
					selected = java.util.Collections.singletonList(smallest);
					ids = java.util.Collections.singletonList(smallest.getId());
					parsed = TractorRules.analyze(selected, level, trump);
				}
			}
			if (!user.removeCardsByProtoIds(ids)) return ConstProto.Result.OP_CARD_NOT_MATCH_VALUE;
			ctx.setLeadCombo(parsed);
			ctx.setTrickLeader(user.getSeated());
			ctx.getTrickPlays().add(selected);
			ctx.getTrickSeats().add(user.getSeated());
			afterPlay(table, user, selected);
			return ConstProto.Result.SUCCESS_VALUE;
		}
		if (!TractorRules.isLegalFollow(selected, lead, user.getCards(), level, trump)) {
			return ConstProto.Result.OP_CARD_NOT_MATCH_VALUE;
		}
		if (!user.removeCardsByProtoIds(ids)) return ConstProto.Result.OP_CARD_NOT_MATCH_VALUE;
		ctx.getTrickPlays().add(selected);
		ctx.getTrickSeats().add(user.getSeated());
		afterPlay(table, user, selected);
		return ConstProto.Result.SUCCESS_VALUE;
	}

	private static boolean throwCanStand(TractorTable table, TableUser actor,
			List<Card> selected, int level, int trump) {
		Card smallest = smallestCard(selected, level, trump);
		int group = TractorRules.suitGroup(smallest, level, trump);
		int floor = TractorRules.power(smallest, level, trump);
		for (TableUser opponent : table.getSeatUsers().values()) {
			if (opponent == actor) continue;
			for (Card card : opponent.getCards()) {
				if (TractorRules.suitGroup(card, level, trump) == group
						&& TractorRules.power(card, level, trump) > floor) {
					return false;
				}
			}
		}
		return true;
	}

	private static Card smallestCard(List<Card> cards, int level, int trump) {
		Card smallest = cards.get(0);
		for (int i = 1; i < cards.size(); i++) {
			if (TractorRules.power(cards.get(i), level, trump)
					< TractorRules.power(smallest, level, trump)) {
				smallest = cards.get(i);
			}
		}
		return smallest;
	}

	private static void afterPlay(TractorTable table, TableUser user, List<Card> selected) {
		TractorTableContext ctx = table.getTractor();
		if (table.getReplayRecorder() instanceof PokerReplayRecorder) {
			List<Integer> ids = new ArrayList<>();
			for (Card card : selected) ids.add(card.getId());
			((PokerReplayRecorder) table.getReplayRecorder()).recordPlay(user.getSeated(), ids);
		}
		GameProto.CardInfo.Builder ci = GameProto.CardInfo.newBuilder().setType(proto.ConstProto.CardType.SINGLE);
		for (Card c : selected) ci.addCards(GameProto.Card.newBuilder().setValue(c.getId()));
		ctx.setLastPlayed(ci.build());
		ctx.setLastPlaySeat(user.getSeated());

		int level = ctx.getLevelRank();
		int trump = ctx.getTrumpSuit();
		boolean kill = isKillPlay(ctx, selected, level, trump);
		int seatNum = table.getTableModel().getSeatNum();
		boolean trickDone = ctx.getTrickPlays().size() >= seatNum;
		int mult = kill ? 2 : 1;
		if (trickDone) {
			int winSeat = TractorRules.winnerSeat(ctx.getTrickPlays(), ctx.getTrickSeats(),
					ctx.getLeadCombo(), level, trump);
			boolean winByKill = isWinByKill(ctx, winSeat, level, trump);
			mult = (winByKill ? 200 : 100) + winSeat;
		}

		GameProto.AckOp msg = GameProto.AckOp.newBuilder()
				.setOp(GameProto.OpInfo.newBuilder().setChoice(ConstProto.Operation.PLAY).addOpCards(ci.build()))
				.setOpId(user.getUserId()).setOpFrom(user.getUserId())
				.setBaseScore(ctx.getDefenderScore())
				.setRobMultiplier(ctx.getLevelRank())
				.setBombMultiplier(Math.max(0, ctx.getTrumpSuit()))
				.setCurrentMultiplier(mult).build();
		table.sendTableMessage(msg, GMsg.ACK_OP);

		if (!trickDone) {
			table.getOp().moveToNextOp();
			table.upNextStateWithTime(TableState.CARD, System.currentTimeMillis());
			return;
		}
		finishTrick(table);
	}

	/** 副牌首出后，本手用主牌跟出（同型）视为杀。 */
	private static boolean isKillPlay(TractorTableContext ctx, List<Card> play, int level, int trump) {
		TractorRules.Combo lead = ctx.getLeadCombo();
		if (lead == null || lead.suitId == 0) return false;
		TractorRules.Combo cur = TractorRules.analyze(play, level, trump);
		return cur != null && cur.suitId == 0 && cur.type == lead.type
				&& (lead.type != TractorRules.ComboType.TRACTOR || cur.tractorLen == lead.tractorLen);
	}

	private static boolean isWinByKill(TractorTableContext ctx, int winSeat, int level, int trump) {
		TractorRules.Combo lead = ctx.getLeadCombo();
		if (lead == null || lead.suitId == 0) return false;
		for (int i = 0; i < ctx.getTrickSeats().size(); i++) {
			if (ctx.getTrickSeats().get(i) != winSeat) continue;
			TractorRules.Combo w = TractorRules.analyze(ctx.getTrickPlays().get(i), level, trump);
			return w != null && w.suitId == 0;
		}
		return false;
	}

	private static void finishTrick(TractorTable table) {
		TractorTableContext ctx = table.getTractor();
		int winSeat = TractorRules.winnerSeat(ctx.getTrickPlays(), ctx.getTrickSeats(),
				ctx.getLeadCombo(), ctx.getLevelRank(), ctx.getTrumpSuit());
		int trickScore = 0;
		for (List<Card> play : ctx.getTrickPlays()) {
			for (Card c : play) trickScore += TractorRules.scoreOf(c);
		}
		if (!ctx.isBankerTeam(winSeat)) {
			ctx.addDefenderScore(trickScore);
		}
		ctx.incTricksDone();
		boolean handsEmpty = true;
		for (TableUser u : table.getSeatUsers().values()) {
			if (!u.getCards().isEmpty()) { handsEmpty = false; break; }
		}
		TractorRules.Combo lastLead = ctx.getLeadCombo();
		List<Card> winPlay = null;
		for (int i = 0; i < ctx.getTrickSeats().size(); i++) {
			if (ctx.getTrickSeats().get(i) == winSeat) {
				winPlay = new ArrayList<>(ctx.getTrickPlays().get(i));
				break;
			}
		}
		ctx.resetTrick();
		ctx.setTrickLeader(winSeat);
		table.getOp().setCurrOpSeat(winSeat);

		if (handsEmpty) {
			// 抠底：仅闲家方赢最后一墩时，按赢家该手最大牌型翻倍计入闲分
			if (!ctx.isBankerTeam(winSeat)) {
				int buriedScore = 0;
				for (int id : ctx.getBuriedCards()) {
					buriedScore += TractorRules.scoreOf(new Card(id));
				}
				int mult = winPlay != null
						? TractorRules.digMultiplierForPlay(winPlay, ctx.getLevelRank(), ctx.getTrumpSuit())
						: TractorRules.digMultiplier(lastLead);
				ctx.addDefenderScore(buriedScore * mult);
			}
			TractorSettleService.finishGame(table);
			return;
		}
		table.upNextStateWithTime(TableState.CARD, System.currentTimeMillis());
		logger.debug("拖拉机一轮结束 winSeat:{} defScore:{}", winSeat, ctx.getDefenderScore());
	}

}
