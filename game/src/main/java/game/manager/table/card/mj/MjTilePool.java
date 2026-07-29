package game.manager.table.card.mj;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import game.manager.table.Table;
import game.manager.table.TableUser;
import msg.registor.message.GMsg;
import proto.GameProto;

/**
 * 麻将牌墙管理
 * 负责初始化牌墙、洗牌、发牌、摸牌
 */
public class MjTilePool {

	private static final Logger logger = LoggerFactory.getLogger(MjTilePool.class);

	private final List<Integer> wallTiles = new ArrayList<>();
	private final Table table;
	/** 兼容旧接口：卡五星等显式花色；null 时按座位规则由 MjWallComposer 编排 */
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
	 * 从牌墙末尾摸一张牌
	 */
	public int drawTile() {
		if (wallTiles.isEmpty()) {
			return -1;
		}
		return wallTiles.remove(wallTiles.size() - 1);
	}

	/**
	 * 牌墙剩余牌数
	 */
	public int remaining() {
		return wallTiles.size();
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
					u.addCards(new game.manager.table.cards.Card(drawTile()));
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
				for (game.manager.table.cards.Card card : otherUser.getCards()) {
					nCards.addCards(GameProto.Card.newBuilder()
							.setValue(owner ? card.getId() : 0).build());
				}
				builder.addNCards(nCards.build());
			}
			sendUser.sendRoleMessage(builder.build(), GMsg.NOT_CARD, table.getTableId());
		}
	}
}
