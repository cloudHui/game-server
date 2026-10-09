package game.manager.table.state;

import java.util.List;

import game.manager.table.Table;
import game.manager.table.ddz.DdzTable;
import game.manager.table.ddz.DdzPlayService;
import game.manager.table.ddz.DdzOperationChoices;
import game.manager.table.pdk.PdkTable;
import game.manager.table.tractor.TractorTable;
import utils.registry.annotation.ProcessEnum;
import utils.registry.enums.TableState;
import msg.registor.message.GMsg;
import proto.ConstProto;
import proto.GameProto;

/**
 * 桌子出牌阶段状态通知处理器（{@link TableState#CARD}）。
 * <p>
 * 核心职责：
 * <ul>
 *   <li><b>操作权限通知：</b>在轮到某玩家行动时，向该座位玩家及全桌广播可操作选项（出牌、不出/过牌）；</li>
 *   <li><b>多玩法分发适配：</b>分别适配斗地主（{@link DdzTable}）、跑得快（{@link PdkTable}）和升级/拖拉机（{@link TractorTable}）；</li>
 *   <li><b>整手代出加速：</b>在斗地主等玩法中，若玩家剩余手牌已可一次性打完且大过上家，直接自动代出快速斩杀。</li>
 * </ul>
 *
 * @author cloud
 */
@ProcessEnum(TableState.CARD)
public class CardNotifyHandle extends AbstractTableHandle {

    /**
     * 出牌状态主调度处理函数。
     * <p>
     * 识别具体棋牌桌子类型并分发给对应的玩法处理逻辑。
     *
     * @param table 当前牌桌对象
     * @return 状态流转标识，固定返回 false
     */
    @Override
    public boolean onTiming(Table table) {
        if (table instanceof PdkTable) {
            return onPdkTiming((PdkTable) table);
        }
        if (table instanceof TractorTable) {
            return onTractorTiming((TractorTable) table);
        }

        // 斗地主玩法处理
        DdzTable ddzTable = (DdzTable) table;
        int seat = table.getOp().getCurrOpSeat();
        // 清理上轮残留选项，重新注册当前座位的合法操作集
        table.getOp().clearChoiceMap();

        List<GameProto.OpInfo> choices = DdzOperationChoices.forTurn(ddzTable.getDdz().getLastHand());
        GameProto.NotOperation.Builder nb = GameProto.NotOperation.newBuilder()
                .setWait(TableState.IDLE_CARD.getOverTime())
                .setOpSeat(seat)
                .addAllChoice(choices);
        for (GameProto.OpInfo choice : choices) {
            table.getOp().addPosOpInfo(seat, choice);
        }

        table.upNextState();
        // 切到 IDLE_CARD 后再由服务端校验整副余牌；若剩余手牌本身构成有效牌型且能压过上家，直接自动打完，提升残局流畅度
        if (DdzPlayService.autoPlayWholeHand(ddzTable, seat)) {
            return false;
        }
        table.sendTableMessage(nb.build(), GMsg.NOT_OP);
        return false;
    }

    /**
     * 跑得快玩法出牌通知处理。
     * <p>
     * 跑得快遵循“有牌必管”强制规则：若管不上只下发“不出/过牌”；若拥有主动权或能管上，则只下发“出牌”选项。
     *
     * @param table 跑得快桌子对象
     * @return 固定返回 false
     */
    private boolean onPdkTiming(PdkTable table) {
        int seat = table.getOp().getCurrOpSeat();
        table.getOp().clearChoiceMap();

        // 依据手牌能否管上上家，计算单一合法选项
        GameProto.OpInfo choice = table.currentOpChoice();
        table.getOp().addPosOpInfo(seat, choice);
        GameProto.NotOperation.Builder nb = GameProto.NotOperation.newBuilder()
                .setWait(TableState.IDLE_CARD.getOverTime())
                .setOpSeat(seat)
                .addChoice(choice);
        table.sendTableMessage(nb.build(), GMsg.NOT_OP);
        table.upNextState();
        return false;
    }

    /**
     * 升级/拖拉机玩法出牌通知处理。
     * <p>
     * 拖拉机每一轮所有座位均必须跟牌，不存在“过牌”选项。
     *
     * @param table 拖拉机桌子对象
     * @return 固定返回 false
     */
    private boolean onTractorTiming(TractorTable table) {
        int seat = table.getOp().getCurrOpSeat();
        table.getOp().clearChoiceMap();
        GameProto.OpInfo play = GameProto.OpInfo.newBuilder().setChoice(ConstProto.Operation.PLAY).build();
        table.getOp().addPosOpInfo(seat, play);
        GameProto.NotOperation.Builder nb = GameProto.NotOperation.newBuilder()
                .setWait(TableState.IDLE_CARD.getOverTime())
                .setOpSeat(seat)
                .addChoice(play);
        // 拖拉机每轮强制跟牌出牌
        table.sendTableMessage(nb.build(), GMsg.NOT_OP);
        table.upNextState();
        return false;
    }
}

