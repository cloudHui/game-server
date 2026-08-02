package game.manager.table.state;

import game.manager.table.Table;
import game.manager.table.ddz.DdzTable;
import msg.annotation.ProcessEnum;
import msg.registor.enums.TableState;
import msg.registor.message.GMsg;
import proto.ConstProto;
import proto.GameProto;

/**
 * 出牌阶段：广播当前座位可操作项（出牌 / 过）。
 *
 * @author cloud
 * @version 1.0
 * @date 2026-05-03
 * @since 1.0
 */
@ProcessEnum(TableState.CARD)
public class CardNotifyHandle extends AbstractTableHandle {

    @Override
    public boolean onTiming(Table table) {
        if (table instanceof game.manager.table.pdk.PdkTable) {
            return onPdkTiming((game.manager.table.pdk.PdkTable) table);
        }
        if (table instanceof game.manager.table.tractor.TractorTable) {
            return onTractorTiming((game.manager.table.tractor.TractorTable) table);
        }
        DdzTable ddzTable = (DdzTable) table;
        int seat = table.getOp().getCurrOpSeat();
        table.getOp().clearChoiceMap();

        GameProto.OpInfo play = GameProto.OpInfo.newBuilder().setChoice(ConstProto.Operation.PLAY).build();
        table.getOp().addPosOpInfo(seat, play);

        GameProto.NotOperation.Builder nb = GameProto.NotOperation.newBuilder()
                .setWait(TableState.IDLE_CARD.getOverTime())
                .setOpSeat(seat)
                .addChoice(play);

        if (ddzTable.getDdz().getLastHand() != null) {
            GameProto.OpInfo pass = GameProto.OpInfo.newBuilder().setChoice(ConstProto.Operation.PASS).build();
            table.getOp().addPosOpInfo(seat, pass);
            nb.addChoice(pass);
        }

        table.sendTableMessage(nb.build(), GMsg.NOT_OP);
        table.upNextState();
        return false;
    }

    private boolean onPdkTiming(game.manager.table.pdk.PdkTable table) {
        int seat = table.getOp().getCurrOpSeat();
        table.getOp().clearChoiceMap();
        // 跑得快：管不上只下发不出；首出/能管只下发出牌
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

    private boolean onTractorTiming(game.manager.table.tractor.TractorTable table) {
        int seat = table.getOp().getCurrOpSeat();
        table.getOp().clearChoiceMap();
        GameProto.OpInfo play = GameProto.OpInfo.newBuilder().setChoice(ConstProto.Operation.PLAY).build();
        table.getOp().addPosOpInfo(seat, play);
        GameProto.NotOperation.Builder nb = GameProto.NotOperation.newBuilder()
                .setWait(TableState.IDLE_CARD.getOverTime())
                .setOpSeat(seat)
                .addChoice(play);
        // 拖拉机每轮都要跟牌，无“过牌”
        table.sendTableMessage(nb.build(), GMsg.NOT_OP);
        table.upNextState();
        return false;
    }
}
