package com.cloud.hub.web.learning.controller;

import com.cloud.hub.web.learning.model.ContentItem;
import com.cloud.hub.web.learning.model.LearningRecord;
import com.cloud.hub.web.learning.model.MathQuestion;
import com.cloud.hub.web.learning.model.Mistake;
import com.cloud.hub.web.learning.model.PrintableQuestion;
import com.cloud.hub.web.learning.model.Student;
import com.cloud.hub.web.learning.model.WordItem;
import com.cloud.hub.web.learning.service.AuthService;
import com.cloud.hub.web.learning.service.ContentService;
import com.cloud.hub.web.learning.service.MathService;
import com.cloud.hub.web.learning.service.MistakeService;
import com.cloud.hub.web.learning.service.RecordService;
import com.cloud.hub.web.learning.service.StatsService;
import com.cloud.hub.web.learning.service.WordService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 学习中心基础业务 API 控制器。
 * <p>
 * 提供生字词查询、各学科内容拉取、口算数学题动态生成与试卷排版、学习记录提交及错题本重练接口。
 *
 * @author cloud
 */
@RestController
@RequestMapping("/api/learning")
public class ApiController {

    private final AuthService auth;
    private final WordService words;
    private final MathService math;
    private final RecordService records;
    private final MistakeService mistakes;
    private final StatsService stats;
    private final ContentService content;

    public ApiController(AuthService auth,
                         WordService words,
                         MathService math,
                         RecordService records,
                         MistakeService mistakes,
                         StatsService stats,
                         ContentService content) {
        this.auth = auth;
        this.words = words;
        this.math = math;
        this.records = records;
        this.mistakes = mistakes;
        this.stats = stats;
        this.content = content;
    }

    /**
     * 学习中心健康状态探活。
     */
    @GetMapping("/health")
    public Map<String, Object> health(@RequestHeader(value = "X-Session-Token", required = false) String token) throws Exception {
        auth.require(token);
        return Collections.singletonMap("status", "ok");
    }

    /**
     * 按学段获取语文生字库列表。
     */
    @GetMapping("/words")
    public List<WordItem> words(@RequestHeader(value = "X-Session-Token", required = false) String token,
                                @RequestParam(required = false) String stage) throws Exception {
        auth.requirePermission(token, "CHINESE");
        return words.list(stage);
    }

    /**
     * 获取指定学科的教学内容资料。
     */
    @GetMapping("/content")
    public List<ContentItem> content(@RequestHeader(value = "X-Session-Token", required = false) String token,
                                     @RequestParam String subject) throws Exception {
        auth.requirePermission(token, permissionFor(subject));
        return content.content(subject);
    }

    /**
     * 在线口算练习出题接口。
     */
    @GetMapping("/math/questions")
    public List<MathQuestion> questions(@RequestHeader(value = "X-Session-Token", required = false) String token,
                                        @RequestParam(defaultValue = "10") int max,
                                        @RequestParam(defaultValue = "10") int count,
                                        @RequestParam(defaultValue = "mixed") String operation) throws Exception {
        auth.requirePermission(token, "MATH");
        return math.generate(max, count, operation);
    }

    /**
     * 课后练习卷纸可打印习题生成接口。
     */
    @GetMapping("/math/printable")
    public List<PrintableQuestion> printable(@RequestHeader(value = "X-Session-Token", required = false) String token,
                                             @RequestParam(defaultValue = "10") int max,
                                             @RequestParam(defaultValue = "20") int count,
                                             @RequestParam(defaultValue = "mixed") String operation,
                                             @RequestParam(defaultValue = "5") int wordProblems,
                                             @RequestParam(defaultValue = "幼小衔接") String stage) throws Exception {
        auth.requirePermission(token, "PRINT");
        return math.printable(max, count, operation, wordProblems, stage);
    }

    /**
     * 上报单次练习做题记录。
     */
    @PostMapping("/records")
    public LearningRecord addRecord(@RequestHeader(value = "X-Session-Token", required = false) String token,
                                    @RequestBody LearningRecord record) throws Exception {
        Student current = auth.requirePermission(token, "RECORDS");
        record.studentId = current.id;
        return records.add(record);
    }

    /**
     * 获取当前学生的练习历史记录列表。
     */
    @GetMapping("/records")
    public List<LearningRecord> records(@RequestHeader(value = "X-Session-Token", required = false) String token) throws Exception {
        Student current = auth.requirePermission(token, "RECORDS");
        return records.list(current.id);
    }

    /**
     * 归集录入一条错题。
     */
    @PostMapping("/mistakes")
    public Mistake addMistake(@RequestHeader(value = "X-Session-Token", required = false) String token,
                              @RequestBody Mistake mistake) throws Exception {
        Student current = auth.requirePermission(token, "MISTAKES");
        mistake.studentId = current.id;
        return mistakes.add(mistake);
    }

    /**
     * 查询个人错题本列表。
     */
    @GetMapping("/mistakes")
    public List<Mistake> mistakes(@RequestHeader(value = "X-Session-Token", required = false) String token,
                                  @RequestParam(required = false) String subject,
                                  @RequestParam(required = false) String status) throws Exception {
        Student current = auth.requirePermission(token, "MISTAKES");
        return mistakes.list(current.id, subject, status);
    }

    /**
     * 提交单条错题的复习重练结果。
     */
    @PostMapping("/mistakes/{mistakeId}/review")
    public Mistake review(@RequestHeader(value = "X-Session-Token", required = false) String token,
                          @PathVariable String mistakeId,
                          @RequestBody ReviewRequest request) throws Exception {
        Student current = auth.requirePermission(token, "MISTAKES");
        return mistakes.review(current.id, mistakeId, request.correct);
    }

    /**
     * 查询个人学情仪表盘统计。
     */
    @GetMapping("/stats")
    public Map<String, Object> stats(@RequestHeader(value = "X-Session-Token", required = false) String token) throws Exception {
        Student current = auth.requirePermission(token, "STATS");
        return stats.personal(current.id);
    }

    private String permissionFor(String subject) {
        if ("英语".equals(subject)) {
            return "ENGLISH";
        }
        if ("历史".equals(subject)) {
            return "HISTORY";
        }
        if ("化学".equals(subject)) {
            return "CHEMISTRY";
        }
        if ("数学".equals(subject)) {
            return "MATH";
        }
        return "CHINESE";
    }

    /**
     * 复习请求体。
     */
    public static class ReviewRequest {
        public boolean correct;
    }
}
