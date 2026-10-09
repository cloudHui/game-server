package utils.match;

/**
 * 双方成功匹配结果数据载体。
 *
 * @param <T> 业务附加载荷类型
 * @author cloud
 */
public class MatchResult<T> {

    /** 匹配成功的发起方玩家票据 */
    private final MatchTicket<T> playerA;

    /** 匹配成功的应答方玩家票据 */
    private final MatchTicket<T> playerB;

    /** 撮合成功时双方的分差绝对值 */
    private final int ratingDiff;

    public MatchResult(MatchTicket<T> playerA, MatchTicket<T> playerB) {
        this.playerA = playerA;
        this.playerB = playerB;
        this.ratingDiff = Math.abs(playerA.getRating() - playerB.getRating());
    }

    public MatchTicket<T> getPlayerA() {
        return playerA;
    }

    public MatchTicket<T> getPlayerB() {
        return playerB;
    }

    public int getRatingDiff() {
        return ratingDiff;
    }
}
