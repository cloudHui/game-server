package com.cloud.hub.web.learning.controller.admin;

import com.cloud.hub.web.learning.model.LearningRecord;
import com.cloud.hub.web.learning.model.Mistake;
import com.cloud.hub.web.learning.model.OnlineState;
import com.cloud.hub.web.learning.service.DailyReportService;
import com.cloud.hub.web.learning.service.MistakeService;
import com.cloud.hub.web.learning.service.RecordService;
import com.cloud.hub.web.learning.service.StatsService;
import com.cloud.hub.web.learning.service.UsageService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 学习中心学情进度、错题监控、全站统计与日报管理控制器。
 *
 * @author cloud
 */
@RestController
@RequestMapping("/api/learning/admin")
public class LearningProgressAdminController {

    private final LearningAdminAccess access;
    private final RecordService records;
    private final MistakeService mistakes;
    private final StatsService stats;
    private final UsageService usage;
    private final DailyReportService reports;

    public LearningProgressAdminController(LearningAdminAccess access,
                                           RecordService records,
                                           MistakeService mistakes,
                                           StatsService stats,
                                           UsageService usage,
                                           DailyReportService reports) {
        this.access = access;
        this.records = records;
        this.mistakes = mistakes;
        this.stats = stats;
        this.usage = usage;
        this.reports = reports;
    }

    /**
     * 查询指定学生的学习记录流水。
     */
    @GetMapping("/records/{userId}")
    public List<LearningRecord> records(@RequestHeader(value = "X-Session-Token", required = false) String token,
                                        @PathVariable String userId) throws Exception {
        access.require(token);
        return records.list(userId);
    }

    /**
     * 为指定学生补录一条练习记录。
     */
    @PostMapping("/records/{userId}")
    public LearningRecord addRecord(@RequestHeader(value = "X-Session-Token", required = false) String token,
                                    @PathVariable String userId,
                                    @RequestBody LearningRecord item) throws Exception {
        access.require(token);
        item.studentId = userId;
        return records.add(item);
    }

    /**
     * 修改指定学生的练习记录。
     */
    @PutMapping("/records/{userId}/{id}")
    public LearningRecord updateRecord(@RequestHeader(value = "X-Session-Token", required = false) String token,
                                       @PathVariable String userId,
                                       @PathVariable String id,
                                       @RequestBody LearningRecord item) throws Exception {
        access.require(token);
        return records.update(userId, id, item);
    }

    /**
     * 删除指定学生的练习记录。
     */
    @DeleteMapping("/records/{userId}/{id}")
    public Map<String, Object> deleteRecord(@RequestHeader(value = "X-Session-Token", required = false) String token,
                                            @PathVariable String userId,
                                            @PathVariable String id) throws Exception {
        access.require(token);
        records.delete(userId, id);
        return message("记录已删除");
    }

    /**
     * 查询指定学生的错题本。
     */
    @GetMapping("/mistakes/{userId}")
    public List<Mistake> mistakes(@RequestHeader(value = "X-Session-Token", required = false) String token,
                                  @PathVariable String userId) throws Exception {
        access.require(token);
        return mistakes.list(userId, null, null);
    }

    /**
     * 为指定学生手动新增一条错题。
     */
    @PostMapping("/mistakes/{userId}")
    public Mistake addMistake(@RequestHeader(value = "X-Session-Token", required = false) String token,
                              @PathVariable String userId,
                              @RequestBody Mistake item) throws Exception {
        access.require(token);
        item.studentId = userId;
        return mistakes.add(item);
    }

    /**
     * 修改指定学生的错题记录。
     */
    @PutMapping("/mistakes/{userId}/{id}")
    public Mistake updateMistake(@RequestHeader(value = "X-Session-Token", required = false) String token,
                                 @PathVariable String userId,
                                 @PathVariable String id,
                                 @RequestBody Mistake item) throws Exception {
        access.require(token);
        return mistakes.update(userId, id, item);
    }

    /**
     * 删除指定学生的错题。
     */
    @DeleteMapping("/mistakes/{userId}/{id}")
    public Map<String, Object> deleteMistake(@RequestHeader(value = "X-Session-Token", required = false) String token,
                                             @PathVariable String userId,
                                             @PathVariable String id) throws Exception {
        access.require(token);
        mistakes.delete(userId, id);
        return message("错题已删除");
    }

    /**
     * 查询全站系统使用与学情概况。
     */
    @GetMapping("/stats")
    public Map<String, Object> stats(@RequestHeader(value = "X-Session-Token", required = false) String token) throws Exception {
        access.require(token);
        return stats.admin();
    }

    /**
     * 查看指定学生的学情统计画像。
     */
    @GetMapping("/stats/{userId}")
    public Map<String, Object> userStats(@RequestHeader(value = "X-Session-Token", required = false) String token,
                                         @PathVariable String userId) throws Exception {
        access.require(token);
        return stats.personal(userId);
    }

    /**
     * 监控当前全站实时在线用户列表。
     */
    @GetMapping("/online")
    public List<OnlineState> online(@RequestHeader(value = "X-Session-Token", required = false) String token) throws Exception {
        access.require(token);
        return usage.onlineUsers();
    }

    /**
     * 预览今日日报邮件内容。
     */
    @GetMapping("/report/preview")
    public Map<String, Object> preview(@RequestHeader(value = "X-Session-Token", required = false) String token) throws Exception {
        access.require(token);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("content", reports.preview());
        return result;
    }

    /**
     * 手动触发推送今日日报邮件。
     */
    @PostMapping("/report/send")
    public Map<String, Object> send(@RequestHeader(value = "X-Session-Token", required = false) String token) throws Exception {
        access.require(token);
        return reports.send(true);
    }

    private Map<String, Object> message(String value) {
        return Collections.singletonMap("message", value);
    }
}
