package game.manager.table.mj.ai;

import java.util.Arrays;

import game.manager.table.TableUser;
import game.manager.table.cards.Card;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;

public class MjSimpleAiTest {
	@Test
	public void chiChoosesCombinationThatCompletesOpenMeld() {
		TableUser user = new TableUser(1, "", "robot", 0);
		for (int tile : new int[]{101, 102, 201, 202, 203, 301, 302, 303,
				401, 401, 401, 501, 501}) {
			user.addCards(new Card(tile));
		}
		int[] expected = {101, 102};
		assertArrayEquals(expected, MjSimpleAi.pickChi(user, Arrays.asList(expected)));
	}
}
