package com.cloud.hub.game.domain.pdk;

import com.cloud.hub.game.domain.ddz.DdzHand;
import com.cloud.hub.game.domain.ddz.ai.AiVision;
import proto.GameProto;

import java.util.HashSet;
import java.util.Set;

/**
 * 跑得快一桌运行时状态。
 */
public class PdkTableContext {

	private GameProto.CardInfo lastPlayed = GameProto.CardInfo.getDefaultInstance();
	private DdzHand lastHand;
	private int consecutivePasses;
	private int lastPlaySeat = -1;
	/** 本轮已过牌座位（断线重连展示「不要」） */
	private final Set<Integer> passSeats = new HashSet<>();
	/** 首出座位（持方块3 / 上局头游） */
	private int firstSeat = -1;
	/** 各座位是否出过牌（用于关牌判定） */
	private final Set<Integer> playedSeats = new HashSet<>();
	private int finishOrder = 0;
	private final int[] finishRanks = new int[] { -1, -1, -1 };
	/** AI 智能等级，默认大师档（参照斗地主最高级） */
	private int aiLevel = AiVision.AI_MASTER;

	public GameProto.CardInfo getLastPlayed() { return lastPlayed; }
	public void setLastPlayed(GameProto.CardInfo lastPlayed) {
		this.lastPlayed = lastPlayed != null ? lastPlayed : GameProto.CardInfo.getDefaultInstance();
	}

	public DdzHand getLastHand() { return lastHand; }
	public void setLastHand(DdzHand lastHand) { this.lastHand = lastHand; }

	public int getConsecutivePasses() { return consecutivePasses; }
	public void addPass() { consecutivePasses++; }
	public void setConsecutivePasses(int n) { consecutivePasses = n; }

	public void addPassSeat(int seat) { passSeats.add(seat); }
	public Set<Integer> getPassSeats() { return passSeats; }

	public int getLastPlaySeat() { return lastPlaySeat; }
	public void setLastPlaySeat(int lastPlaySeat) { this.lastPlaySeat = lastPlaySeat; }

	public int getFirstSeat() { return firstSeat; }
	public void setFirstSeat(int firstSeat) { this.firstSeat = firstSeat; }

	public void markPlayed(int seat) { playedSeats.add(seat); }
	public boolean hasPlayed(int seat) { return playedSeats.contains(seat); }

	public void recordFinish(int seat) {
		if (finishRanks[seat] >= 0) return;
		finishRanks[seat] = finishOrder++;
	}

	public int getFinishRank(int seat) { return finishRanks[seat]; }

	public void resetCurrentTrick() {
		lastPlayed = GameProto.CardInfo.getDefaultInstance();
		lastHand = null;
		consecutivePasses = 0;
		passSeats.clear();
	}

	public void resetRound() {
		lastPlaySeat = -1;
		playedSeats.clear();
		finishOrder = 0;
		for (int i = 0; i < finishRanks.length; i++) finishRanks[i] = -1;
		resetCurrentTrick();
	}

	public int getAiLevel() {
		return aiLevel;
	}

	public void setAiLevel(int aiLevel) {
		this.aiLevel = Math.max(AiVision.AI_DUMB, Math.min(AiVision.AI_MASTER, aiLevel));
	}
}
