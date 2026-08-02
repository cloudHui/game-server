package game.manager.table.tractor;

import game.manager.table.TableUser;
import game.manager.table.state.Waiting;
import msg.registor.enums.TableState;
import msg.registor.message.GMsg;
import proto.GameProto;

/**
 * 拖拉机发牌：服务端一次发完，客户端本地按间隔播动画；动画期内可抢主。
 */
public final class TractorDealService {

    /**
     * 给客户端播发牌动画 + 抢主的窗口（毫秒）
     */
    private static final long DEAL_ANI_MS = 2500L;

    private TractorDealService() {
    }

    /**
     * START_ANI：首 tick 一次发完并推送；到期后进入亮主回合。
     */
    public static boolean onTiming(TractorTable table) {
        TractorTableContext ctx = table.getTractor();
        TractorCardPool pool = table.getCardPool();
        long now = System.currentTimeMillis();

        if (ctx.getLastDealTime() == 0) {
            pool.dealAllNow();
            Waiting.recordInitHands(table);
            pool.sendInitCardNotice(table.getSeatUsers());
            TractorBidService.notifyDealBid(table);
            ctx.setLastDealTime(now);
            table.sendTableMessage(GameProto.NotTableState.newBuilder()
                    .setState(TableState.START_ANI.getId())
                    .setStateStart(table.getStateStartTime())
                    .setStateDuration((int) Math.ceil(DEAL_ANI_MS / 1000.0))
                    .build(), GMsg.NOT_STATE);
            return false;
        }

        maybeRobotBid(table, now);
        if (now - ctx.getLastDealTime() >= DEAL_ANI_MS) {
            finishDealPhase(table, ctx);
        }
        return false;
    }

    private static void finishDealPhase(TractorTable table, TractorTableContext ctx) {
        ctx.setDealing(false);
        int seats = table.getTableModel().getSeatNum();
        int next = ctx.getBidStrength() > 0 && ctx.getBidSeat() >= 0
                ? (ctx.getBidSeat() + 1) % seats
                : Math.max(0, ctx.getBankerSeat());
        table.getOp().setCurrOpSeat(next);
        table.upNextState(TableState.ROB);
    }

    private static void maybeRobotBid(TractorTable table, long now) {
        if ((now / 400) % 2 != 0) return;
        for (TableUser u : table.getSeatUsers().values()) {
            if (u != null && u.isRobot() && u.getSeated() >= 0) {
                TractorBidService.autoBidDuringDeal(table, u.getSeated());
                return;
            }
        }
    }
}
