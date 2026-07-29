package game.manager.table.replay;

import java.util.List;

/** 跑得快、拖拉机共用的扑克回放记录器。 */
public class PokerReplayRecorder extends BaseReplayRecorder {

	public PokerReplayRecorder(long tableId, int round) {
		super(tableId, round);
	}

	public void recordPlay(int seat, List<Integer> cardIds) {
		appendAction("座" + seat + " 出牌 " + formatList(cardIds));
	}

	public void recordPass(int seat) {
		appendAction("座" + seat + " 过");
	}

	public void recordDeclare(int seat, String action, List<Integer> cardIds) {
		appendAction("座" + seat + " " + action + " " + formatList(cardIds));
	}

	public void recordBury(int seat, List<Integer> cardIds) {
		appendAction("座" + seat + " 扣底 " + formatList(cardIds));
	}

	@Override
	public void writeSettlement(int winnerSeat, int fan, String winType, int[] scores) {
		sb.append("\n=== 结算 ===\n");
		sb.append("赢家: 座").append(winnerSeat).append("\n");
		sb.append("结算值: ").append(fan).append("\n");
		sb.append("类型: ").append(winType).append("\n");
		for (int i = 0; i < scores.length; i++) {
			sb.append("座").append(i).append(": ")
					.append(scores[i] >= 0 ? "+" : "").append(scores[i]).append("\n");
		}
	}
}
