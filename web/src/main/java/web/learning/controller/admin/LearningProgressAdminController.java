package web.learning.controller.admin;

import org.springframework.web.bind.annotation.*;
import web.learning.model.LearningRecord;
import web.learning.model.Mistake;
import web.learning.model.OnlineState;
import web.learning.service.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/learning/admin")
public class LearningProgressAdminController {
    private final LearningAdminAccess access;
    private final RecordService records;
    private final MistakeService mistakes;
    private final StatsService stats;
    private final UsageService usage;
    private final DailyReportService reports;

    public LearningProgressAdminController(LearningAdminAccess access, RecordService records,
                                           MistakeService mistakes, StatsService stats,
                                           UsageService usage, DailyReportService reports) {
        this.access = access;
        this.records = records;
        this.mistakes = mistakes;
        this.stats = stats;
        this.usage = usage;
        this.reports = reports;
    }

    @GetMapping("/records/{userId}")
    public List<LearningRecord> records(@RequestHeader(value = "X-Session-Token", required = false) String token,
                                        @PathVariable String userId) throws Exception {
        access.require(token);
        return records.list(userId);
    }

    @PostMapping("/records/{userId}")
    public LearningRecord addRecord(@RequestHeader(value = "X-Session-Token", required = false) String token,
                                    @PathVariable String userId,
                                    @RequestBody LearningRecord item) throws Exception {
        access.require(token);
        item.studentId = userId;
        return records.add(item);
    }

    @PutMapping("/records/{userId}/{id}")
    public LearningRecord updateRecord(@RequestHeader(value = "X-Session-Token", required = false) String token,
                                       @PathVariable String userId, @PathVariable String id,
                                       @RequestBody LearningRecord item) throws Exception {
        access.require(token);
        return records.update(userId, id, item);
    }

    @DeleteMapping("/records/{userId}/{id}")
    public Map<String, Object> deleteRecord(@RequestHeader(value = "X-Session-Token", required = false) String token,
                                            @PathVariable String userId, @PathVariable String id)
            throws Exception {
        access.require(token);
        records.delete(userId, id);
        return message("记录已删除");
    }

    @GetMapping("/mistakes/{userId}")
    public List<Mistake> mistakes(@RequestHeader(value = "X-Session-Token", required = false) String token,
                                  @PathVariable String userId) throws Exception {
        access.require(token);
        return mistakes.list(userId, null, null);
    }

    @PostMapping("/mistakes/{userId}")
    public Mistake addMistake(@RequestHeader(value = "X-Session-Token", required = false) String token,
                              @PathVariable String userId, @RequestBody Mistake item) throws Exception {
        access.require(token);
        item.studentId = userId;
        return mistakes.add(item);
    }

    @PutMapping("/mistakes/{userId}/{id}")
    public Mistake updateMistake(@RequestHeader(value = "X-Session-Token", required = false) String token,
                                 @PathVariable String userId, @PathVariable String id,
                                 @RequestBody Mistake item) throws Exception {
        access.require(token);
        return mistakes.update(userId, id, item);
    }

    @DeleteMapping("/mistakes/{userId}/{id}")
    public Map<String, Object> deleteMistake(@RequestHeader(value = "X-Session-Token", required = false) String token,
                                             @PathVariable String userId, @PathVariable String id)
            throws Exception {
        access.require(token);
        mistakes.delete(userId, id);
        return message("错题已删除");
    }

    @GetMapping("/stats")
    public Map<String, Object> stats(@RequestHeader(value = "X-Session-Token", required = false) String token) throws Exception {
        access.require(token);
        return stats.admin();
    }

    @GetMapping("/stats/{userId}")
    public Map<String, Object> userStats(@RequestHeader(value = "X-Session-Token", required = false) String token,
                                         @PathVariable String userId) throws Exception {
        access.require(token);
        return stats.personal(userId);
    }

    @GetMapping("/online")
    public List<OnlineState> online(@RequestHeader(value = "X-Session-Token", required = false) String token) throws Exception {
        access.require(token);
        return usage.onlineUsers();
    }

    @GetMapping("/report/preview")
    public Map<String, Object> preview(@RequestHeader(value = "X-Session-Token", required = false) String token) throws Exception {
        access.require(token);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("content", reports.preview());
        return result;
    }

    @PostMapping("/report/send")
    public Map<String, Object> send(@RequestHeader(value = "X-Session-Token", required = false) String token) throws Exception {
        access.require(token);
        return reports.send(true);
    }

    private Map<String, Object> message(String value) {
        return java.util.Collections.<String, Object>singletonMap("message", value);
    }
}
