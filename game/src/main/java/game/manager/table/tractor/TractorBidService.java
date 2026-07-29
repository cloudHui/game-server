package game.manager.table.tractor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import game.manager.table.TableUser;
import game.manager.table.TractorTable;
import game.manager.table.cards.Card;
import game.manager.table.cards.CardOps;
import game.manager.table.replay.PokerReplayRecorder;
import game.manager.table.state.IdleShowCard;
import msg.registor.enums.TableState;
import msg.registor.message.GMsg;
import proto.ConstProto;
import proto.GameProto;

/**
 * 拖拉机亮主 / 反主 / 自保自反。
 * CALL=亮主或自保，ROB=反主或自反，NOT_CALL=过。
 */
public final class TractorBidService {

	private static final Logger logger = LoggerFactory.getLogger(TractorBidService.class);
	public static final int DECLARE_SECONDS = 15;

	private TractorBidService() {}

	/** 发牌后轮询亮主：仅当前座位。 */
	public static void notifyCurrent(TractorTable table) {
		TractorTableContext ctx = table.getTractor();
		int seat = table.getOp().getCurrOpSeat();
		if (seat < 0) {
			seat = Math.max(0, ctx.getBankerSeat());
			table.getOp().setCurrOpSeat(seat);
		}
		table.getOp().clearChoiceMap();
		TableUser user = table.getSeatUser(seat);
		GameProto.NotOperation.Builder nb = notOp(seat, DECLARE_SECONDS);
		addChoice(table, seat, nb, op(ConstProto.Operation.NOT_CALL));
		appendDeclareChoices(table, seat, user, ctx, nb);
		table.sendTableMessage(nb.build(), GMsg.NOT_OP);
	}

	/** 发牌中抢主：每人各自可选（无需轮到）。 */
	public static void notifyDealBid(TractorTable table) {
		table.getOp().clearChoiceMap();
		for (TableUser u : table.getSeatUsers().values()) {
			sendDealBidTo(table, u);
		}
	}

	/** 仅刷新刚摸到牌的座位，减少发牌期推送。 */
	public static void notifyDealBidSeat(TractorTable table, int seat) {
		TableUser u = table.getSeatUser(seat);
		if (u == null) return;
		java.util.Set<GameProto.OpInfo> existing = table.getOp().getSeatOps(seat);
		if (existing != null) existing.clear();
		sendDealBidTo(table, u);
	}

	private static void sendDealBidTo(TractorTable table, TableUser u) {
		int seat = u.getSeated();
		if (seat < 0) return;
		GameProto.NotOperation.Builder nb = notOp(seat, 0);
		appendDeclareChoices(table, seat, u, table.getTractor(), nb);
		u.sendRoleMessage(nb.build(), GMsg.NOT_OP, table.getTableId());
	}

	public static int applyDuringDeal(TractorTable table, int userId, GameProto.OpInfo opInfo) {
		if (!table.getTractor().isDealing()) return ConstProto.Result.OP_CURR_ERROR_VALUE;
		TableUser user = table.getUsers().get(userId);
		if (user == null || user.getSeated() < 0) return ConstProto.Result.OP_CURR_ERROR_VALUE;
		ConstProto.Operation choice = opInfo.getChoice();
		if (isPass(choice)) return ConstProto.Result.SUCCESS_VALUE;
		if (choice != ConstProto.Operation.CALL && choice != ConstProto.Operation.ROB) {
			return ConstProto.Result.OP_CURR_ERROR_VALUE;
		}
		int result = applyDeclare(table, user, opInfo, choice == ConstProto.Operation.ROB, false);
		if (result == ConstProto.Result.SUCCESS_VALUE) notifyDealBid(table);
		return result;
	}

	public static int apply(TractorTable table, int userId, GameProto.OpInfo opInfo) {
		if (table.getTableState() != TableState.IDLE_ROB) return ConstProto.Result.OP_CURR_ERROR_VALUE;
		TableUser user = table.getUsers().get(userId);
		if (user == null || user.getSeated() != table.getOp().getCurrOpSeat()) {
			return ConstProto.Result.OP_CURR_ERROR_VALUE;
		}
		ConstProto.Operation choice = opInfo.getChoice();
		if (isPass(choice)) return applyPass(table, user);
		if (choice == ConstProto.Operation.CALL || choice == ConstProto.Operation.ROB) {
			return applyDeclare(table, user, opInfo, choice == ConstProto.Operation.ROB, true);
		}
		return ConstProto.Result.OP_CURR_ERROR_VALUE;
	}

	public static void onTimeout(TractorTable table) {
		int seat = table.getOp().getCurrOpSeat();
		TableUser user = table.getSeatUser(seat);
		if (user == null) {
			finishIfNeeded(table, true);
			return;
		}
		logger.info("拖拉机亮主超时视为过, tableId:{} seat:{}", table.getTableId(), seat);
		applyPass(table, user);
	}

	public static void autoBid(TractorTable table, int seat) {
		autoDeclare(table, seat, false);
	}

	public static void autoBidDuringDeal(TractorTable table, int seat) {
		if (!table.getTractor().isDealing()) return;
		autoDeclare(table, seat, true);
	}

	private static void autoDeclare(TractorTable table, int seat, boolean duringDeal) {
		TableUser user = table.getSeatUser(seat);
		if (user == null) return;
		TractorTableContext ctx = table.getTractor();
		TractorRules.BidDeclare best = TractorRules.findBestDeclare(
				user.getCards(), ctx.getLevelRank(), ctx.getBidStrength(), ctx.getBidSuit());
		if (best == null) {
			if (!duringDeal) applyPass(table, user);
			return;
		}
		if (duringDeal) {
			if (ctx.getBidStrength() <= 0 && best.strength == TractorRules.BID_SINGLE
					&& ThreadLocalRandom.current().nextBoolean()) return;
		}
		ConstProto.Operation op = ctx.getBidStrength() <= 0
				? ConstProto.Operation.CALL : ConstProto.Operation.ROB;
		GameProto.OpInfo info = GameProto.OpInfo.newBuilder()
				.setChoice(op).addOpCards(CardOps.toCardInfo(best.cards)).build();
		if (duringDeal) applyDuringDeal(table, user.getUserId(), info);
		else apply(table, user.getUserId(), info);
	}

	private static boolean canOfferDeclare(List<Card> hand, TractorTableContext ctx) {
		return TractorRules.findBestDeclare(hand, ctx.getLevelRank(),
				ctx.getBidStrength(), ctx.getBidSuit()) != null;
	}

	private static int applyPass(TractorTable table, TableUser user) {
		broadcastAck(table, user.getUserId(), op(ConstProto.Operation.NOT_CALL));
		table.getTractor().incBidPasses();
		return finishIfNeeded(table, false);
	}

	private static int applyDeclare(TractorTable table, TableUser user, GameProto.OpInfo opInfo,
			boolean asRob, boolean finishAfter) {
		TractorTableContext ctx = table.getTractor();
		List<Card> selected = CardOps.pullFromHand(user, CardOps.collectIds(opInfo));
		if (selected == null || selected.isEmpty()) return ConstProto.Result.OP_CARD_NOT_MATCH_VALUE;
		TractorRules.BidDeclare dec = TractorRules.analyzeDeclare(selected, ctx.getLevelRank());
		if (dec == null) return ConstProto.Result.OP_CURR_ERROR_VALUE;
		dec = validateDeclare(dec, ctx);
		if (dec == null) return ConstProto.Result.OP_CURR_ERROR_VALUE;

		acceptBid(ctx, user.getSeated(), dec);
		broadcastAck(table, user.getUserId(), GameProto.OpInfo.newBuilder()
				.setChoice(asRob ? ConstProto.Operation.ROB : ConstProto.Operation.CALL)
				.addOpCards(CardOps.toCardInfo(selected)).build());
		logger.info("拖拉机定主 table:{} seat:{} strength:{} suit:{}",
				table.getTableId(), user.getSeated(), dec.strength, dec.suit);
		return finishAfter ? finishIfNeeded(table, false) : ConstProto.Result.SUCCESS_VALUE;
	}

	/** @return 升级后的声明，不合法返回 null */
	private static TractorRules.BidDeclare validateDeclare(
			TractorRules.BidDeclare dec, TractorTableContext ctx) {
		int cur = ctx.getBidStrength();
		if (cur <= 0) return dec;
		return TractorRules.beatsDeclare(dec, cur, ctx.getBidSuit()) ? dec : null;
	}

	private static void acceptBid(TractorTableContext ctx, int seat, TractorRules.BidDeclare dec) {
		ctx.setBidStrength(dec.strength);
		ctx.setBidSuit(dec.suit);
		ctx.setBidSeat(seat);
		ctx.setTrumpSuit(dec.suit);
		ctx.resetBidPasses();
	}

	private static int finishIfNeeded(TractorTable table, boolean force) {
		TractorTableContext ctx = table.getTractor();
		int seats = table.getTableModel().getSeatNum();
		boolean done = force
				|| (ctx.getBidStrength() > 0 && ctx.getBidPasses() >= seats - 1)
				|| (ctx.getBidStrength() <= 0 && ctx.getBidPasses() >= seats);
		if (!done) {
			table.getOp().moveToNextOp();
			table.upNextStateWithTime(TableState.ROB, System.currentTimeMillis());
			return ConstProto.Result.SUCCESS_VALUE;
		}
		finalizeBid(table);
		return ConstProto.Result.SUCCESS_VALUE;
	}

	static void finalizeBid(TractorTable table) {
		TractorTableContext ctx = table.getTractor();
		if (ctx.getBidStrength() > 0 && ctx.getBidSeat() >= 0) {
			ctx.setBankerSeat(ctx.getBidSeat());
			ctx.setTrumpSuit(ctx.getBidSuit());
		} else {
			ctx.setTrumpSuit(0);
			if (ctx.getBankerSeat() < 0) ctx.setBankerSeat(0);
		}
		table.getCardPool().attachBottom(table, ctx.getBankerSeat());
		table.getOp().setCurrOpSeat(ctx.getBankerSeat());
		ctx.setTrickLeader(ctx.getBankerSeat());
		notifyBury(table);
		table.upNextStateWithTime(TableState.IDLE_SHOW_CARD, System.currentTimeMillis());
		logger.info("拖拉机定主结束 banker:{} trump:{} level:{}",
				ctx.getBankerSeat(), ctx.getTrumpSuit(), ctx.getLevelRank());
	}

	public static void notifyBury(TractorTable table) {
		TractorTableContext ctx = table.getTractor();
		int banker = ctx.getBankerSeat();
		table.getOp().clearChoiceMap();
		table.getOp().setCurrOpSeat(banker);

		GameProto.OpInfo bury = op(ConstProto.Operation.DISCARD);
		table.getOp().addPosOpInfo(banker, bury);
		TableUser bankerUser = table.getSeatUser(banker);
		if (bankerUser != null) {
			bankerUser.sendRoleMessage(notOp(banker, IdleShowCard.TRACTOR_BURY_SECONDS)
					.addChoice(bury).build(), GMsg.NOT_OP, table.getTableId());
		}

		for (TableUser u : table.getSeatUsers().values()) {
			int seat = u.getSeated();
			if (seat < 0 || seat == banker) continue;
			GameProto.NotOperation.Builder nb = notOp(seat, IdleShowCard.TRACTOR_BURY_SECONDS);
			addChoice(table, seat, nb, op(ConstProto.Operation.NOT_CALL));
			if (canOfferDeclare(u.getCards(), ctx)) {
				addChoice(table, seat, nb, op(ConstProto.Operation.ROB));
			}
			u.sendRoleMessage(nb.build(), GMsg.NOT_OP, table.getTableId());
		}
	}

	public static int applyReverseDuringBury(TractorTable table, int userId, GameProto.OpInfo opInfo) {
		if (table.getTableState() != TableState.IDLE_SHOW_CARD) return ConstProto.Result.OP_CURR_ERROR_VALUE;
		TableUser user = table.getUsers().get(userId);
		TractorTableContext ctx = table.getTractor();
		if (user == null || user.getSeated() == ctx.getBankerSeat()) {
			return ConstProto.Result.OP_CURR_ERROR_VALUE;
		}
		ConstProto.Operation choice = opInfo.getChoice();
		if (isPass(choice)) {
			broadcastAck(table, userId, op(ConstProto.Operation.NOT_CALL));
			return ConstProto.Result.SUCCESS_VALUE;
		}
		if (choice != ConstProto.Operation.ROB && choice != ConstProto.Operation.CALL) {
			return ConstProto.Result.OP_CURR_ERROR_VALUE;
		}
		List<Card> selected = CardOps.pullFromHand(user, CardOps.collectIds(opInfo));
		if (selected == null || selected.isEmpty()) return ConstProto.Result.OP_CARD_NOT_MATCH_VALUE;
		TractorRules.BidDeclare dec = TractorRules.analyzeDeclare(selected, ctx.getLevelRank());
		if (!TractorRules.beatsDeclare(dec, ctx.getBidStrength(), ctx.getBidSuit())) {
			return ConstProto.Result.OP_CURR_ERROR_VALUE;
		}

		acceptBid(ctx, user.getSeated(), dec);
		ctx.setBankerSeat(user.getSeated());
		broadcastAck(table, userId, GameProto.OpInfo.newBuilder()
				.setChoice(ConstProto.Operation.ROB)
				.addOpCards(CardOps.toCardInfo(selected)).build());

		table.getCardPool().attachBottom(table, user.getSeated());
		table.getOp().setCurrOpSeat(user.getSeated());
		ctx.setTrickLeader(user.getSeated());
		notifyBury(table);
		table.upNextStateWithTime(TableState.IDLE_SHOW_CARD, System.currentTimeMillis());
		logger.info("拖拉机反主换扣底 table:{} newBanker:{} suit:{}",
				table.getTableId(), user.getSeated(), dec.suit);
		return ConstProto.Result.SUCCESS_VALUE;
	}

	private static void appendDeclareChoices(TractorTable table, int seat, TableUser user,
			TractorTableContext ctx, GameProto.NotOperation.Builder nb) {
		if (user == null || !canOfferDeclare(user.getCards(), ctx)) return;
		if (ctx.getBidStrength() <= 0) {
			addChoice(table, seat, nb, op(ConstProto.Operation.CALL));
		} else if (seat == ctx.getBidSeat()) {
			addChoice(table, seat, nb, op(ConstProto.Operation.CALL));
			addChoice(table, seat, nb, op(ConstProto.Operation.ROB));
		} else {
			addChoice(table, seat, nb, op(ConstProto.Operation.ROB));
		}
	}

	private static void addChoice(TractorTable table, int seat,
			GameProto.NotOperation.Builder nb, GameProto.OpInfo choice) {
		table.getOp().addPosOpInfo(seat, choice);
		nb.addChoice(choice);
	}

	private static GameProto.NotOperation.Builder notOp(int seat, int wait) {
		return GameProto.NotOperation.newBuilder().setWait(wait).setOpSeat(seat);
	}

	private static GameProto.OpInfo op(ConstProto.Operation choice) {
		return GameProto.OpInfo.newBuilder().setChoice(choice).build();
	}

	private static boolean isPass(ConstProto.Operation choice) {
		return choice == ConstProto.Operation.NOT_CALL
				|| choice == ConstProto.Operation.PASS
				|| choice == ConstProto.Operation.NOT_ROB;
	}

	private static void broadcastAck(TractorTable table, int actorUserId, GameProto.OpInfo op) {
		TractorTableContext ctx = table.getTractor();
		TableUser actor = table.getUsers().get(actorUserId);
		if (actor != null && table.getReplayRecorder() instanceof PokerReplayRecorder) {
			String action = op.getChoice() == ConstProto.Operation.CALL ? "亮主"
					: op.getChoice() == ConstProto.Operation.ROB ? "反主" : "过";
			((PokerReplayRecorder) table.getReplayRecorder()).recordDeclare(
					actor.getSeated(), action, CardOps.collectIds(op));
		}
		int trumpShow = ctx.getBidStrength() > 0 ? ctx.getBidSuit() : Math.max(0, ctx.getTrumpSuit());
		table.sendTableMessage(GameProto.AckOp.newBuilder()
				.setOp(op).setOpId(actorUserId).setOpFrom(actorUserId)
				.setBaseScore(ctx.getDefenderScore())
				.setRobMultiplier(ctx.getLevelRank())
				.setBombMultiplier(Math.max(0, trumpShow))
				.setCurrentMultiplier(Math.max(0, ctx.getBidStrength()))
				.build(), GMsg.ACK_OP);
	}

}
