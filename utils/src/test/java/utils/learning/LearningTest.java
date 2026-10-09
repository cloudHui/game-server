package utils.learning;

import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

/**
 * 艾宾浩斯自适应复习算法单元测试。
 */
public class LearningTest {

    @Test
    public void testSpacedRepetitionProgression() {
        long now = 1000000000L;
        MemoryItem item = new MemoryItem("word-apple", now);

        // 第一次完美答对 (q=5)
        item = SpacedRepetitionScheduler.review(item, 5, now);
        Assert.assertEquals(1, item.getRepetitions());
        Assert.assertEquals(1, item.getIntervalDays());

        // 第二次熟练答对 (q=4)
        now += SpacedRepetitionScheduler.ONE_DAY_MS;
        item = SpacedRepetitionScheduler.review(item, 4, now);
        Assert.assertEquals(2, item.getRepetitions());
        Assert.assertEquals(6, item.getIntervalDays());

        // 第三次答错 (q=1) -> 重置连胜
        now += 6 * SpacedRepetitionScheduler.ONE_DAY_MS;
        item = SpacedRepetitionScheduler.review(item, 1, now);
        Assert.assertEquals(0, item.getRepetitions());
        Assert.assertEquals(1, item.getIntervalDays());
    }

    @Test
    public void testPickDueItems() {
        long now = 1000000000L;
        List<MemoryItem> list = new ArrayList<>();
        MemoryItem m1 = new MemoryItem("m1", now - 2 * SpacedRepetitionScheduler.ONE_DAY_MS);
        m1.setNextReviewTime(now - SpacedRepetitionScheduler.ONE_DAY_MS); // 已超期1天

        MemoryItem m2 = new MemoryItem("m2", now);
        m2.setNextReviewTime(now + SpacedRepetitionScheduler.ONE_DAY_MS); // 1天后才到期

        list.add(m1);
        list.add(m2);

        List<MemoryItem> due = SpacedRepetitionScheduler.pickDueItems(list, 10, now);
        Assert.assertEquals(1, due.size());
        Assert.assertEquals("m1", due.get(0).getItemId());
    }
}
