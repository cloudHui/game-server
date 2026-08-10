package lobby.admin;

import model.tablemodel.RobotRoomTemplates;
import model.tablemodel.TableModel;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class AdminRobotMatchRulesTest {
    @Test
    public void appliesSelectedRulesAndForcesRobotLifecycle() {
        Map<String, String> input = new HashMap<>();
        input.put("totalRounds", "5");
        input.put("baseScore", "2");
        input.put("allowChi", "0");

        TableModel model = AdminRobotMatchRules.create(RobotRoomTemplates.mahjong(), input);

        assertEquals(5, model.getTotalRounds());
        assertEquals(2, model.getBaseScore());
        assertEquals(0, model.getAllowChi());
        assertEquals(1, model.getAutoPlay());
        assertEquals(1, model.getAutoNextRound());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsExcessiveRounds() {
        Map<String, String> input = new HashMap<>();
        input.put("totalRounds", "21");
        AdminRobotMatchRules.create(RobotRoomTemplates.douDiZhu(), input);
    }
}
