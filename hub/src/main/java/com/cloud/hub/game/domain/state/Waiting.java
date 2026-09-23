package com.cloud.hub.game.domain.state;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cloud.hub.game.Game;
import com.cloud.hub.game.domain.cards.Card;
import com.cloud.hub.game.domain.replay.ReplayRecorder;
import com.cloud.hub.game.domain.table.Table;
import com.cloud.hub.game.domain.table.TableUser;

import utils.registry.annotation.ProcessEnum;
import utils.registry.enums.TableState;

/**
 * 桌子等待准备阶段状态处理器。
 * <p>
 * <b>职责与使用场景：</b>
 * <ul>
 *   <li>监听 {@link TableState#WAITING} 状态，在桌子串行 tick 中检测玩家就坐与准备状态；</li>
 *   <li>检测坐满且存在真人（或机器人陪练房间）时调用 {@link #startGame(Table)} 触发开局；</li>
 *   <li>全面依托 {@link Table#dealCards()}、{@link Table#onGameStarted()}、
 *       {@link Table#getInitialStartState()} 等钩子实现纯多态开局，彻底消灭历史代码中的长 switch 分支。</li>
 * </ul>
 */
@ProcessEnum(TableState.WAITING)
public class Waiting extends AbstractTableHandle {
    private static final Logger logger = LoggerFactory.getLogger(Waiting.class);

    @Override
    public boolean onTiming(Table table) {
        if (table.sitFull()) {
            if (table.isAllRobot() && !table.isRobotRoom()) {
                logger.info("普通桌全机器人不开局，解散桌子, tableId: {}", table.getTableId());
                table.upNextState(TableState.TABLE_DIS);
                return false;
            }
            startGame(table);
            return false;
        }
        if (table.isEmpty() || (!table.hasHumanPlayer() && !table.isRobotRoom())) {
            logger.info("等待阶段无真人，解散桌子, tableId: {}, empty: {}, allRobot: {}",
                    table.getTableId(), table.isEmpty(), table.isAllRobot());
            Game.getInstance().getTableManager().removeTableAsync(table.getTableId());
            return true;
        }
        // 未坐满则继续等待；不再使用桌模 waitTimeout 配置。
        return false;
    }

    /**
     * 启动新一局游戏。
     * <p>
     * 依次执行：初始化游戏配置、洗牌发牌、启动录像、根据游戏类型跳转初始状态（如斗地主叫分、拖拉机动画等）。
     *
     * @param table 游戏桌实例
     */
    private void startGame(Table table) {
        table.initGameConfig();
        table.dealCards();
        table.onGameStarted();

        initReplay(table);
        if (table.shouldRecordInitHands()) {
            recordInitHands(table);
        }

        table.upNextState(table.getInitialStartState());
    }

    /**
     * 初始化录像
     * 
     * @param table
     */
    private void initReplay(Table table) {
        ReplayRecorder replay = table.createReplayRecorder();
        if (replay == null)
            return;

        table.setReplayRecorder(replay);

        Map<Integer, Integer> userIds = new HashMap<>();
        Map<Integer, String> nicknames = new HashMap<>();
        for (Map.Entry<Integer, TableUser> entry : table.getSeatUsers().entrySet()) {
            userIds.put(entry.getKey(), entry.getValue().getUserId());
            nicknames.put(entry.getKey(), entry.getValue().getNick());
        }

        replay.writeHeader(table.getGameDisplayName(), table.getTableModel().getTotalRounds(),
                table.getTableModel().getSeatNum(), userIds, nicknames);
        replay.writeConfig("底分=" + table.getTableModel().getBaseScore()
                + ", 最大番=" + table.getTableModel().getMaxFan()
                + ", autoPlay=" + table.getTableModel().getAutoPlay());

        table.onInitReplayHeader(replay);
    }

    /**
     * 记录手牌
     */
    public static void recordInitHands(Table table) {
        ReplayRecorder replay = table.getReplayRecorder();
        if (replay == null)
            return;

        Map<Integer, List<Integer>> hands = new HashMap<>();
        for (Map.Entry<Integer, TableUser> entry : table.getSeatUsers().entrySet()) {
            List<Integer> tileIds = new ArrayList<>();
            for (Card c : entry.getValue().getCards()) {
                tileIds.add(c.getId());
            }
            hands.put(entry.getKey(), tileIds);
        }
        replay.writeInitHands(hands);
        replay.checkpoint();
    }
}
