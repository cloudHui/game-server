package game.domain.poker.niuniu;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 牛牛单局运行时上下文。
 * <p>
 * 维护当前局子阶段（抢庄/下注/亮牌/结算）、定庄结果、各座抢庄与下注倍数、摊牌集合及机器人延迟时间戳。
 * </p>
 */
public class NiuniuContext {

    /** 阶段：0-等待开局 */
    public static final int STAGE_WAITING = 0;
    /** 阶段：1-明牌抢庄 */
    public static final int STAGE_ROB = 1;
    /** 阶段：2-闲家加倍下注 */
    public static final int STAGE_BET = 2;
    /** 阶段：3-拼牛亮牌 */
    public static final int STAGE_SHOW = 3;
    /** 阶段：4-单局结算完成 */
    public static final int STAGE_SETTLE = 4;

    /** 当前对局子阶段枚举值（0~4） */
    private volatile int stage = STAGE_WAITING;
    /** 本局庄家座位号（未定庄前为 -1） */
    private volatile int bankerSeat = -1;
    /** 本局庄家抢庄倍数（1~4） */
    private volatile int bankerMultiplier = 1;
    /** 当前阶段机器人允许行动的目标时间戳（毫秒），避免 0ms 瞬秒连跳 */
    private volatile long robotActAt = 0L;

    /** 各座位抢庄倍数记录 &lt;座位号, 0~4倍&gt; */
    private final Map<Integer, Integer> robMultipliers = new ConcurrentHashMap<>();
    /** 各闲家座位下注倍数记录 &lt;座位号, 1~5倍&gt; */
    private final Map<Integer, Integer> betMultipliers = new ConcurrentHashMap<>();
    /** 各座位算牛结果缓存 &lt;座位号, 手牌评估模型&gt; */
    private final Map<Integer, NiuniuHand> playerHands = new ConcurrentHashMap<>();
    /** 已完成亮牌的座位集合 */
    private final Set<Integer> showedSeats = ConcurrentHashMap.newKeySet();

    /**
     * 重置单局状态供下一局复用。
     */
    public void reset() {
        this.stage = STAGE_WAITING;
        this.bankerSeat = -1;
        this.bankerMultiplier = 1;
        this.robotActAt = 0L;
        this.robMultipliers.clear();
        this.betMultipliers.clear();
        this.playerHands.clear();
        this.showedSeats.clear();
    }

    /**
     * 获取当前阶段编号。
     *
     * @return 阶段编号（0~4）
     */
    public int getStage() {
        return stage;
    }

    /**
     * 设置当前阶段编号。
     *
     * @param stage 阶段编号（0~4）
     */
    public void setStage(int stage) {
        this.stage = stage;
    }

    /**
     * 获取本局庄家座位号。
     *
     * @return 庄家座位号，未定庄返回 -1
     */
    public int getBankerSeat() {
        return bankerSeat;
    }

    /**
     * 设置本局庄家座位号。
     *
     * @param bankerSeat 庄家座位号
     */
    public void setBankerSeat(int bankerSeat) {
        this.bankerSeat = bankerSeat;
    }

    /**
     * 获取庄家抢庄倍数。
     *
     * @return 庄家倍数（1~4）
     */
    public int getBankerMultiplier() {
        return bankerMultiplier;
    }

    /**
     * 设置庄家抢庄倍数。
     *
     * @param bankerMultiplier 庄家倍数（1~4）
     */
    public void setBankerMultiplier(int bankerMultiplier) {
        this.bankerMultiplier = bankerMultiplier;
    }

    /**
     * 获取机器人允许行动时间戳（毫秒）。
     *
     * @return 毫秒时间戳
     */
    public long getRobotActAt() {
        return robotActAt;
    }

    /**
     * 设置机器人允许行动时间戳（毫秒）。
     *
     * @param robotActAt 毫秒时间戳
     */
    public void setRobotActAt(long robotActAt) {
        this.robotActAt = robotActAt;
    }

    /**
     * 获取各座位抢庄倍数映射。
     *
     * @return 座位到抢庄倍数映射
     */
    public Map<Integer, Integer> getRobMultipliers() {
        return robMultipliers;
    }

    /**
     * 获取各闲家座位下注倍数映射。
     *
     * @return 座位到下注倍数映射
     */
    public Map<Integer, Integer> getBetMultipliers() {
        return betMultipliers;
    }

    /**
     * 获取各座位算牛评估结果映射。
     *
     * @return 座位到手牌评估结果映射
     */
    public Map<Integer, NiuniuHand> getPlayerHands() {
        return playerHands;
    }

    /**
     * 获取已亮牌座位集合。
     *
     * @return 已亮牌座位集合
     */
    public Set<Integer> getShowedSeats() {
        return showedSeats;
    }
}
