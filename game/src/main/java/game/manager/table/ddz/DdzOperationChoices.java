package game.manager.table.ddz;

import game.manager.table.cards.Card;
import proto.ConstProto;
import proto.GameProto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 斗地主当前回合操作选项的单一构建入口。 */
public final class DdzOperationChoices {
    private DdzOperationChoices() {}

    public static List<GameProto.OpInfo> forTurn(DdzHand lastHand) {
        GameProto.OpInfo.Builder play = GameProto.OpInfo.newBuilder().setChoice(ConstProto.Operation.PLAY);
        if (lastHand != null) {
            GameProto.CardInfo.Builder lastCards = GameProto.CardInfo.newBuilder().setType(lastHand.getType());
            for (Card card : lastHand.getCards()) {
                lastCards.addCards(GameProto.Card.newBuilder().setValue(card.getId()));
            }
            play.addOpCards(lastCards);
        }
        if (lastHand == null) return Collections.singletonList(play.build());
        List<GameProto.OpInfo> choices = new ArrayList<>(2);
        choices.add(play.build());
        choices.add(GameProto.OpInfo.newBuilder().setChoice(ConstProto.Operation.PASS).build());
        return choices;
    }
}
