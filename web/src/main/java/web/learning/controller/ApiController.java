package web.learning.controller;

import web.learning.model.ContentItem;
import web.learning.model.LearningRecord;
import web.learning.model.MathQuestion;
import web.learning.model.Mistake;
import web.learning.model.PrintableQuestion;
import web.learning.model.Student;
import web.learning.model.WordItem;
import web.learning.service.AuthService;
import web.learning.service.ContentService;
import web.learning.service.MathService;
import web.learning.service.MistakeService;
import web.learning.service.RecordService;
import web.learning.service.StatsService;
import web.learning.service.WordService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

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

    public ApiController(AuthService auth, WordService words, MathService math, RecordService records,
                         MistakeService mistakes, StatsService stats, ContentService content) {
        this.auth = auth;
        this.words = words;
        this.math = math;
        this.records = records;
        this.mistakes = mistakes;
        this.stats = stats;
        this.content = content;
    }

    @GetMapping("/health")
    public Map<String, Object> health(@RequestHeader(value = "X-Session-Token", required = false) String token) throws Exception {
        auth.require(token);
        return java.util.Collections.<String, Object>singletonMap("status", "ok");
    }

    @GetMapping("/words")
    public List<WordItem> words(@RequestHeader(value = "X-Session-Token", required = false) String token,
                                @RequestParam(required = false) String stage) throws Exception {
        auth.requirePermission(token, "CHINESE");
        return words.list(stage);
    }

    @GetMapping("/content")
    public List<ContentItem> content(@RequestHeader(value = "X-Session-Token", required = false) String token,
                                     @RequestParam String subject) throws Exception {
        auth.requirePermission(token, permissionFor(subject));
        return content.content(subject);
    }

    @GetMapping("/math/questions")
    public List<MathQuestion> questions(@RequestHeader(value = "X-Session-Token", required = false) String token,
                                        @RequestParam(defaultValue = "10") int max, @RequestParam(defaultValue = "10") int count,
                                        @RequestParam(defaultValue = "mixed") String operation) throws Exception {
        auth.requirePermission(token, "MATH");
        return math.generate(max, count, operation);
    }

    @GetMapping("/math/printable")
    public List<PrintableQuestion> printable(@RequestHeader(value = "X-Session-Token", required = false) String token,
                                             @RequestParam(defaultValue = "10") int max, @RequestParam(defaultValue = "20") int count,
                                             @RequestParam(defaultValue = "mixed") String operation, @RequestParam(defaultValue = "5") int wordProblems,
                                             @RequestParam(defaultValue = "幼小衔接") String stage) throws Exception {
        auth.requirePermission(token, "PRINT");
        return math.printable(max, count, operation, wordProblems, stage);
    }

    @PostMapping("/records")
    public LearningRecord addRecord(@RequestHeader(value = "X-Session-Token", required = false) String token,
                                    @RequestBody LearningRecord record) throws Exception {
        Student current = auth.requirePermission(token, "RECORDS");
        record.studentId = current.id;
        return records.add(record);
    }

    @GetMapping("/records")
    public List<LearningRecord> records(@RequestHeader(value = "X-Session-Token", required = false) String token) throws Exception {
        Student current = auth.requirePermission(token, "RECORDS");
        return records.list(current.id);
    }

    @PostMapping("/mistakes")
    public Mistake addMistake(@RequestHeader(value = "X-Session-Token", required = false) String token,
                              @RequestBody Mistake mistake) throws Exception {
        Student current = auth.requirePermission(token, "MISTAKES");
        mistake.studentId = current.id;
        return mistakes.add(mistake);
    }

    @GetMapping("/mistakes")
    public List<Mistake> mistakes(@RequestHeader(value = "X-Session-Token", required = false) String token,
                                  @RequestParam(required = false) String subject, @RequestParam(required = false) String status) throws Exception {
        Student current = auth.requirePermission(token, "MISTAKES");
        return mistakes.list(current.id, subject, status);
    }

    @PostMapping("/mistakes/{mistakeId}/review")
    public Mistake review(@RequestHeader(value = "X-Session-Token", required = false) String token, @PathVariable String mistakeId,
                          @RequestBody ReviewRequest request) throws Exception {
        Student current = auth.requirePermission(token, "MISTAKES");
        return mistakes.review(current.id, mistakeId, request.correct);
    }

    @GetMapping("/stats")
    public Map<String, Object> stats(@RequestHeader(value = "X-Session-Token", required = false) String token) throws Exception {
        Student current = auth.requirePermission(token, "STATS");
        return stats.personal(current.id);
    }

    private String permissionFor(String subject) {
        if ("英语".equals(subject)) return "ENGLISH";
        if ("历史".equals(subject)) return "HISTORY";
        if ("化学".equals(subject)) return "CHEMISTRY";
        if ("数学".equals(subject)) return "MATH";
        return "CHINESE";
    }

    public static class ReviewRequest {
        public boolean correct;
    }
}
