package utils.match;

import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import utils.replay.ReplayFrame;
import utils.replay.ReplayTape;

/**
 * 匹配、ELO 算法与回放系统单元测试。
 */
public class MatchTest {

    @Test
    public void testEloCalculations() {
        // 积分相同期望胜率应为 0.5
        double exp = EloRatingCalculator.calculateExpectedScore(1200, 1200);
        Assert.assertEquals(0.5, exp, 0.001);

        // 高分打低分，胜者加分较少
        int newA = EloRatingCalculator.calculateNewRating(1600, 1200, 1.0, 30);
        Assert.assertTrue(newA > 1600 && newA < 1620);

        // 低分爆冷战胜高分，加分显著
        int newB = EloRatingCalculator.calculateNewRating(1200, 1600, 1.0, 30);
        Assert.assertTrue(newB > 1220);
    }

    @Test
    public void testMatchmakingQueue() {
        MatchmakingQueue<String> queue = new MatchmakingQueue<>(50, 10, 300);
        long now = System.currentTimeMillis();

        // 初始分差过大(150分)，初始容差50分不应匹配
        queue.enqueue(1, 1200, now, "player1");
        queue.enqueue(2, 1350, now, "player2");
        List<MatchResult<String>> matches = queue.pollMatches(now);
        Assert.assertTrue(matches.isEmpty());

        // 经过 15 秒后，容差扩大至 50 + 15 * 10 = 200分，应成功撮合
        List<MatchResult<String>> laterMatches = queue.pollMatches(now + 15000);
        Assert.assertEquals(1, laterMatches.size());
        Assert.assertEquals(150, laterMatches.get(0).getRatingDiff());
        Assert.assertEquals(0, queue.size());
    }

    @Test
    public void testReplayTape() {
        ReplayTape<String> tape = new ReplayTape<>("match-01", 1, System.currentTimeMillis());
        tape.setInitialSnapshot("snap-init");
        ReplayFrame<String> f1 = tape.record(1, 100, "move-A");
        ReplayFrame<String> f2 = tape.record(2, 101, "move-B");
        tape.finish();

        Assert.assertEquals(2, tape.frameCount());
        Assert.assertEquals(1, f1.getFrameSeq());
        Assert.assertEquals(2, f2.getFrameSeq());
        Assert.assertEquals("move-A", f1.getPayload());
    }
}
