package com.cloud.hub.web.learning.service;

import com.cloud.hub.web.learning.model.MathQuestion;
import com.cloud.hub.web.learning.model.PrintableQuestion;
import com.cloud.hub.web.learning.model.WordProblemTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * 口算练习与可打印数学题纸生成服务。
 * <p>
 * 支持根据最大数值、题目数量、加减法模式动态生成口算算式，并支持混合应用题排版生成习题纸。
 *
 * @author cloud
 */
@Service
public class MathService {

    private final Random random = new Random();
    private final ContentService content;

    public MathService(ContentService content) {
        this.content = content;
    }

    /**
     * 随机生成口算数学题目列表。
     *
     * @param max       计算最大数值 (5 ~ 1000)
     * @param count     生成题目数量 (1 ~ 100)
     * @param operation 运算模式 ("add", "sub", 或混选)
     * @return 口算题实体列表
     */
    public List<MathQuestion> generate(int max, int count, String operation) {
        int safeMax = Math.max(5, Math.min(max, 1000));
        int safeCount = Math.max(1, Math.min(count, 100));
        List<MathQuestion> questions = new ArrayList<>(safeCount);

        for (int i = 0; i < safeCount; i++) {
            boolean add = "add".equals(operation) || (!"sub".equals(operation) && random.nextBoolean());
            questions.add(generateOne(safeMax, add));
        }
        return questions;
    }

    private MathQuestion generateOne(int max, boolean add) {
        int left = random.nextInt(max + 1);
        int right = random.nextInt(max + 1);
        if (add) {
            while (left + right > max) {
                left = random.nextInt(max + 1);
                right = random.nextInt(max + 1);
            }
            return new MathQuestion(UUID.randomUUID().toString(), left, right, "+", left + right);
        } else {
            if (right > left) {
                int swap = left;
                left = right;
                right = swap;
            }
            return new MathQuestion(UUID.randomUUID().toString(), left, right, "−", left - right);
        }
    }

    /**
     * 生成适合课后打印的练习试卷题目（算术题与文字应用题混合）。
     *
     * @param max              算术最大数值
     * @param count            纯算术题数量
     * @param operation        运算模式
     * @param wordProblemCount 文字应用题数量
     * @param stage            学段
     * @return 试卷排版题目列表
     * @throws Exception 模板读取异常
     */
    public List<PrintableQuestion> printable(int max, int count, String operation,
                                            int wordProblemCount, String stage) throws Exception {
        List<PrintableQuestion> result = new ArrayList<>();
        if (count > 0) {
            for (MathQuestion q : generate(max, count, operation)) {
                result.add(new PrintableQuestion(q.id, "算术", q.text.replace("?", "______"), String.valueOf(q.answer)));
            }
        }
        if (wordProblemCount > 0) {
            appendWordProblems(result, max, wordProblemCount, stage);
        }
        return result;
    }

    private void appendWordProblems(List<PrintableQuestion> result, int max, int count, String stage) throws Exception {
        List<WordProblemTemplate> available = new ArrayList<>();
        for (WordProblemTemplate item : content.templates()) {
            if (item.enabled && (stage == null || stage.isEmpty() || stage.equals(item.stage)) && item.maxNumber <= max) {
                available.add(item);
            }
        }
        if (available.isEmpty()) {
            available = content.templates();
        }
        int safeCount = Math.max(0, Math.min(50, count));
        for (int i = 0; i < safeCount && !available.isEmpty(); i++) {
            WordProblemTemplate template = available.get(random.nextInt(available.size()));
            result.add(generateWordProblem(template, max));
        }
    }

    private PrintableQuestion generateWordProblem(WordProblemTemplate template, int max) {
        boolean add = "add".equals(template.operation);
        int a = random.nextInt(max + 1);
        int b = random.nextInt(max + 1);
        if (add) {
            while (a + b > max) {
                a = random.nextInt(max + 1);
                b = random.nextInt(max + 1);
            }
        } else if (b > a) {
            int swap = a;
            a = b;
            b = swap;
        }
        String text = template.template.replace("{a}", String.valueOf(a)).replace("{b}", String.valueOf(b));
        return new PrintableQuestion(UUID.randomUUID().toString(), "文字题", text, String.valueOf(add ? a + b : a - b));
    }
}
