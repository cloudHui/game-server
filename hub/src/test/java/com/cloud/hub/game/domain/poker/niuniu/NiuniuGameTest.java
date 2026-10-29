package com.cloud.hub.game.domain.poker.niuniu;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.junit.Before;
import org.junit.Test;

import com.cloud.hub.game.db.ScoreRepository;
import com.cloud.hub.game.domain.cards.Card;
import com.cloud.hub.game.domain.replay.ReplayDirectories;
import com.cloud.hub.game.domain.state.TableStateHandleManager;
import com.cloud.hub.game.domain.table.GameResult;
import com.cloud.hub.game.domain.table.TableUser;
import com.cloud.hub.lobby.manager.table.LobbyTableManager;

import model.tablemodel.RobotRoomTemplates;
import model.tablemodel.TableModel;
import proto.ConstProto;
import proto.GameProto;
import proto.ModelProto;
import tool.config.TableConfigManager;
import utils.registry.enums.TableState;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * 牛牛（明牌抢庄斗牛）模板注册、牌型判定与多局机器人状态机端到端测试。
 *
 * <p><b>覆盖场景：</b>
 * <ul>
 *   <li>官方模板注册：经典多人房（#5）与快速机器人房（#9020）在大厅白名单及配表管理器中的注册能力；</li>
 *   <li>牌型求解器：五小牛/炸弹牛/五花牛/四花牛/牛牛/有牛/无牛及同牌型花色比牌，并校验全路径完整保留 5 张手牌；</li>
 *   <li>快速机器人房（#9020）：1 名真人入座自动补满 3 名机器人，驱动 4 局完整「发4张 -> 抢庄 -> 定庄 -> 闲家下注 -> 补发第5张 -> 亮牌 -> 结算 -> 自动续局 -> 总结算」闭环；</li>
 *   <li>经典多人房（#5）：2 人（1 真人 + 1 手动机器人）点击准备开局不跳局号，且局间续局准备不要求坐满 4 人。</li>
 * </ul>
 */
public class NiuniuGameTest {

    /** 测试临时回放目录，隔离磁盘产物避免污染工程根目录 */
    private Path replayTempDir;

    /**
     * 初始化独立回放输出目录。
     *
     * @throws Exception 创建临时目录异常
     */
    @Before
    public void setUp() throws Exception {
        replayTempDir = Files.createTempDirectory("niuniu-replay-test");
        ReplayDirectories.configure(replayTempDir);
        Path scoreDb = replayTempDir.resolve("test-lobby.db");
        ScoreRepository.initialize(scoreDb.toString(), Runnable::run);
    }

    /**
     * 验证经典房 #5（来自 TableModel.xlsx 配表）与快速机器人房 #9020（来自内置模板）均已正常加载。
     */
    @Test
    public void officialTemplatesAndLobbyRegistry() {
        assertEquals(9020, RobotRoomTemplates.NIUNIU_ROOM_ID);
        assertFalse(RobotRoomTemplates.isRobotRoom(5));
        assertTrue(RobotRoomTemplates.isRobotRoom(9020));

        assertTrue(LobbyTableManager.isOfficialRoom(5));
        assertTrue(LobbyTableManager.isOfficialRoom(9020));

        TableConfigManager configManager = new TableConfigManager();
        assertFalse("应成功从 config/tablemodel_models.dat 加载 Excel 配表", configManager.loadFail());
        TableModel classic = configManager.getTableModel(5);
        assertNotNull("Excel 配表中应包含牛牛经典房 #5", classic);

        RobotRoomTemplates.register(configManager::putRuntimeModel);
        TableModel quick = configManager.getTableModel(9020);
        assertNotNull(quick);
        assertEquals(NiuniuTable.GAME_TYPE_NIUNIU, classic.getType());
        assertEquals(NiuniuTable.GAME_TYPE_NIUNIU, quick.getType());
        assertEquals(4, classic.getSeatNum());
        assertEquals(4, quick.getSeatNum());
        assertEquals(5, classic.getCardNum());
    }

    /**
     * 验证牛牛全部特殊牌型、普通牛型、无牛及同点黑桃压红桃比牌，并确保评估结果始终保留全部 5 张牌。
     */
    @Test
    public void evaluateSpecialAndNormalHandsPreservesAllFiveCards() {
        // 五小牛：A(1) + A(1) + 2(2) + 2(2) + 3(3) = 9 <= 10
        NiuniuHand fiveSmall = NiuniuRules.evaluate(cards(114, 214, 115, 215, 103));
        assertEquals(NiuniuCardType.FIVE_SMALL, fiveSmall.getCardType());
        assertEquals(8, fiveSmall.getCardType().getDefaultMultiplier());
        assertEquals(5, fiveSmall.getAllCards().size());

        // 炸弹牛：4 张 8 + 单张 K
        NiuniuHand bomb = NiuniuRules.evaluate(cards(108, 208, 308, 408, 413));
        assertEquals(NiuniuCardType.BOMB, bomb.getCardType());
        assertEquals(7, bomb.getCardType().getDefaultMultiplier());
        assertEquals(5, bomb.getAllCards().size());

        // 五花牛：J J Q K K
        NiuniuHand fiveFlower = NiuniuRules.evaluate(cards(111, 211, 312, 313, 413));
        assertEquals(NiuniuCardType.FIVE_FLOWER, fiveFlower.getCardType());
        assertEquals(6, fiveFlower.getCardType().getDefaultMultiplier());
        assertEquals(5, fiveFlower.getAllCards().size());

        // 四花牛：10 + J Q K K
        NiuniuHand fourFlower = NiuniuRules.evaluate(cards(110, 211, 312, 213, 413));
        assertEquals(NiuniuCardType.FOUR_FLOWER, fourFlower.getCardType());
        assertEquals(5, fourFlower.getCardType().getDefaultMultiplier());
        assertEquals(5, fourFlower.getAllCards().size());

        // 牛牛：10 + K + Q (30) + 3 + 7 (10)
        NiuniuHand niuNiuSpadeK = NiuniuRules.evaluate(cards(110, 413, 212, 103, 207));
        NiuniuHand niuNiuHeartK = NiuniuRules.evaluate(cards(210, 313, 112, 203, 307));
        assertEquals(NiuniuCardType.NIU_NIU, niuNiuSpadeK.getCardType());
        assertEquals(4, niuNiuSpadeK.getCardType().getDefaultMultiplier());
        assertEquals(5, niuNiuSpadeK.getAllCards().size());
        // 同为牛牛且最大牌均为 K 时，黑桃 K (413) 大于红桃 K (313)
        assertTrue(NiuniuRules.INSTANCE.compare(niuNiuSpadeK, niuNiuHeartK) > 0);

        // 牛九：10 + 4 + 6 (20) + A(1) + 8(8)
        NiuniuHand niu9 = NiuniuRules.evaluate(cards(110, 204, 306, 414, 108));
        assertEquals(NiuniuCardType.NIU_9, niu9.getCardType());
        assertEquals(3, niu9.getCardType().getDefaultMultiplier());
        assertEquals(5, niu9.getAllCards().size());

        // 无牛：3 + 3 + 3 + 5 + 7，任意三张之和为 9/11/13/15，均非 10 的倍数
        NiuniuHand noNiu = NiuniuRules.evaluate(cards(103, 203, 303, 105, 207));
        assertEquals(NiuniuCardType.NO_NIU, noNiu.getCardType());
        assertEquals(1, noNiu.getCardType().getDefaultMultiplier());
        assertEquals(5, noNiu.getAllCards().size());
    }

    /**
     * 验证快速机器人房（#9020）1 名真人入座自动补满 3 名机器人，完整走通全部 4 局及末局总结算。
     *
     * @throws Exception 读取回放文件异常
     */
    @Test
    public void quickRobotRoom9020PlaysAllFourRoundsEndToEnd() throws Exception {
        TableModel model = RobotRoomTemplates.niuniu();
        ModelProto.RoomRole creator = ModelProto.RoomRole.newBuilder().setRoleId(1001).build();
        NiuniuTable table = new NiuniuTable(902001L, model, creator);

        TableUser human = new TableUser(1001, "", "Tester", 0);
        assertEquals(ConstProto.Result.SUCCESS_VALUE, table.addUser(human));
        // 快速机器人房首位真人入桌后自动补齐 3 个机器人席位
        assertEquals(4, table.getSeatUsers().size());
        assertTrue(table.canStartGame());

        int totalRounds = model.getTotalRounds();
        assertEquals(4, totalRounds);

        for (int round = 1; round <= totalRounds; round++) {
            assertEquals(round, table.getCurrentRound());
            assertEquals(TableState.WAITING, table.getTableState());

            // 1. WAITING -> ROB：洗牌并先发前 4 张明牌
            TableStateHandleManager.handle(table);
            assertEquals(TableState.ROB, table.getTableState());
            for (TableUser seatUser : table.getSeatUsers().values()) {
                assertEquals("抢庄阶段每人应持有4张牌", 4, seatUser.getCards().size());
            }

            // 2. ROB -> IDLE_ROB：仅广播一次抢庄通知并立即切入等待抢庄态（绝不死循环停留在 ROB）
            TableStateHandleManager.handle(table);
            assertEquals(TableState.IDLE_ROB, table.getTableState());
            assertEquals(NiuniuContext.STAGE_ROB, table.getContext().getStage());

            // 消除拟人延迟让 3 名机器人抢庄，真人同步提交 2 倍抢庄
            table.getContext().setRobotActAt(0L);
            TableStateHandleManager.handle(table);
            GameProto.OpInfo robOp = GameProto.OpInfo.newBuilder()
                    .setChoice(ConstProto.Operation.NIU_ROB_2)
                    .build();
            assertEquals(ConstProto.Result.SUCCESS_VALUE,
                    table.processOp(human.getUserId(), robOp, null, table.getTableId(), round * 10 + 1));

            // 3. 定庄完成自动切入 IDLE_SHOW_CARD（闲家下注阶段）
            assertEquals(TableState.IDLE_SHOW_CARD, table.getTableState());
            assertEquals(NiuniuContext.STAGE_BET, table.getContext().getStage());
            int bankerSeat = table.getContext().getBankerSeat();
            assertTrue(bankerSeat >= 0 && bankerSeat < 4);

            // 机器人闲家自动下注；若真人是闲家则提交 3 倍下注（若真人是庄家则机器人下完即自动收束）
            table.getContext().setRobotActAt(0L);
            TableStateHandleManager.handle(table);
            if (human.getSeated() != bankerSeat) {
                GameProto.OpInfo betOp = GameProto.OpInfo.newBuilder()
                        .setChoice(ConstProto.Operation.NIU_BET_3)
                        .build();
                assertEquals(ConstProto.Result.SUCCESS_VALUE,
                        table.processOp(human.getUserId(), betOp, null, table.getTableId(), round * 10 + 2));
            }

            // 4. 下注完成必须统一补发第 5 张牌并切入 CARD（亮牌拼牛阶段）
            assertEquals(TableState.CARD, table.getTableState());
            assertEquals(NiuniuContext.STAGE_SHOW, table.getContext().getStage());
            for (TableUser seatUser : table.getSeatUsers().values()) {
                assertEquals("下注完成后每人必须补齐5张牌", 5, seatUser.getCards().size());
            }

            // 机器人亮牌 + 真人亮牌
            table.getContext().setRobotActAt(0L);
            TableStateHandleManager.handle(table);
            GameProto.OpInfo showOp = GameProto.OpInfo.newBuilder()
                    .setChoice(ConstProto.Operation.NIU_SHOW)
                    .build();
            assertEquals(ConstProto.Result.SUCCESS_VALUE,
                    table.processOp(human.getUserId(), showOp, null, table.getTableId(), round * 10 + 3));

            // 5. 全员摊牌后切入 TABLE_OVER 并写入零和单局战绩
            assertEquals(TableState.TABLE_OVER, table.getTableState());
            assertEquals(round, table.getGameResult().getCompletedRounds());
            GameResult.RoundEntry roundEntry = table.getGameResult().getRoundEntries().get(round - 1);
            int scoreSum = 0;
            for (int s : roundEntry.getScores()) {
                scoreSum += s;
            }
            assertEquals("闲庄零和博弈当局总分代数和必须为0", 0, scoreSum);

            // 6. 推进局间 TABLE_OVER：前 3 局超时后自动开启下一局，第 4 局结束后散桌结算
            table.upNextStateWithTime(TableState.TABLE_OVER, 0L);
            TableStateHandleManager.handle(table);
            if (round < totalRounds) {
                assertEquals(TableState.WAITING, table.getTableState());
                assertEquals(round + 1, table.getCurrentRound());
            }
        }

        // 校验回放文件落盘且包含第 5 张牌补发与比牌结算审计信息
        String expectedFile = table.getTableId() + "_1.txt";
        Optional<Path> firstReplay = Files.walk(replayTempDir)
                .filter(p -> p.getFileName().toString().equals(expectedFile))
                .findFirst();
        assertTrue("首局牛牛回放文件应已落盘", firstReplay.isPresent());
        String replayContent = new String(Files.readAllBytes(firstReplay.get()), StandardCharsets.UTF_8);
        assertTrue(replayContent.contains("补发第5张"));
        assertTrue(replayContent.contains("定庄"));
        assertTrue(replayContent.contains("亮牌"));
        assertTrue(replayContent.contains("=== 结算 ==="));
    }

    /**
     * 验证经典房（#5）支持 2 人（1 真人 + 1 手动添加机器人）准备开局，首局不跳局号且局间续局无需坐满 4 人。
     */
    @Test
    public void classicRoom5SupportsTwoPlayersPrepareWithoutSkippingRoundOne() {
        TableConfigManager configManager = new TableConfigManager();
        assertFalse(configManager.loadFail());
        TableModel model = configManager.getTableModel(5);
        assertNotNull(model);
        ModelProto.RoomRole creator = ModelProto.RoomRole.newBuilder().setRoleId(2001).build();
        NiuniuTable table = new NiuniuTable(500001L, model, creator);

        TableUser human = new TableUser(2001, "", "Host", 0);
        assertEquals(ConstProto.Result.SUCCESS_VALUE, table.addUser(human));
        // 经典房不自动塞满机器人，单人不可开局
        assertEquals(1, table.getSeatUsers().size());
        assertFalse(table.canStartGame());

        // 房主手动添加 1 名陪练机器人，凑齐最小开局人数（2人），但真人未准备前仍不自动抢跑开局
        assertTrue(table.addOneRobot());
        assertEquals(2, table.getSeatUsers().size());
        assertFalse(table.canStartGame());

        // 真人点击准备后满足开局条件，且当前局号仍严格保持为第 1 局
        table.addReady(human.getUserId());
        assertTrue(table.canStartGame());
        assertTrue(table.allReady());
        assertEquals(1, table.getCurrentRound());

        // 驱动第 1 局通过超时自动兜底快速跑完（验证超时链路在 2 人残局下同样完备）
        TableStateHandleManager.handle(table);
        assertEquals(TableState.ROB, table.getTableState());
        assertEquals(1, table.getCurrentRound());

        TableStateHandleManager.handle(table);
        assertEquals(TableState.IDLE_ROB, table.getTableState());
        NiuniuFlow.onRobTimeout(table);
        assertEquals(TableState.IDLE_SHOW_CARD, table.getTableState());

        NiuniuFlow.onBetTimeout(table);
        assertEquals(TableState.CARD, table.getTableState());
        for (TableUser seatUser : table.getSeatUsers().values()) {
            assertEquals(5, seatUser.getCards().size());
        }

        NiuniuFlow.onShowTimeout(table);
        assertEquals(TableState.TABLE_OVER, table.getTableState());
        assertEquals(1, table.getGameResult().getCompletedRounds());

        // 首轮 TABLE_OVER tick：机器人自动准备，但真人未准备前停留在 TABLE_OVER 展示结算面板
        TableStateHandleManager.handle(table);
        assertEquals(TableState.TABLE_OVER, table.getTableState());
        assertFalse("真人尚未点击下一局前不应提前跳局", table.allReady());

        // 真人点击下一局准备后，2 人桌无需坐满 4 人即可推进至第 2 局 WAITING
        table.addReady(human.getUserId());
        assertTrue(table.allReady());
        TableStateHandleManager.handle(table);
        assertEquals(TableState.WAITING, table.getTableState());
        assertEquals(2, table.getCurrentRound());
        assertTrue("第 2 局起自动满足开局条件直接发牌", table.canStartGame());
    }

    /**
     * 将整数牌面编码数组转换为 {@link Card} 列表。
     *
     * @param ids 牌编码可变参数
     * @return 扑克牌对象列表
     */
    private static List<Card> cards(int... ids) {
        Card[] arr = new Card[ids.length];
        for (int i = 0; i < ids.length; i++) {
            arr[i] = new Card(ids[i]);
        }
        return Arrays.asList(arr);
    }
}
