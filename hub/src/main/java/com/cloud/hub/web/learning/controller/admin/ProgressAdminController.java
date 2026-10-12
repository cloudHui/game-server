package com.cloud.hub.web.learning.controller.admin;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cloud.hub.web.learning.model.LearningRecord;
import com.cloud.hub.web.learning.model.Mistake;
import com.cloud.hub.web.learning.model.OnlineState;
import com.cloud.hub.web.learning.service.DailyReportService;
import com.cloud.hub.web.learning.service.MistakeService;
import com.cloud.hub.web.learning.service.RecordService;
import com.cloud.hub.web.learning.service.StatsService;
import com.cloud.hub.web.learning.service.UsageService;

/**
 * 学习中心学情进度、错题监控、全站统计与日报管理控制器。
 * <p>
 * 提供学生刷题记录、错题本治理、全站系统使用概况统计、实时在线监控以及每日日报手动触发与预览功能。
 *
 * @author cloud
 */
@RestController
@RequestMapping("/api/learning/admin")
public class ProgressAdminController {

    /** 学习中心鉴权守卫 */
    private final AdminAccess access;
    /** 学生练习记录管理服务 */
    private final RecordService records;
    /** 错题本监控与管理服务 */
    private final MistakeService mistakes;
    /** 全站学情与用户统计画像分析服务 */
    private final StatsService stats;
    /** 实时在线监控服务 */
    private final UsageService usage;
    /** 学情每日日报生成与推送服务 */
    private final DailyReportService reports;

    public ProgressAdminController(AdminAccess access,
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
     *
     * @param token  会话凭证 Header
     * @param userId 学员用户唯一标识
     * @return 练习记录实体列表
     * @throws Exception 鉴权未通过或查询异常
     */
    @GetMapping("/records/{userId}")
    public List<LearningRecord> records(@RequestHeader(value = "X-Session-Token", required = false) String token,
                                        @PathVariable String userId) throws Exception {
        access.require(token);
        return records.list(userId);
    }

    /**
     * 为指定学生补录一条练习记录。
     *
     * @param token  会话凭证 Header
     * @param userId 学员用户唯一标识
     * @param item   待新增的练习记录实体
     * @return 补录保存后的练习记录
     * @throws Exception 鉴权未通过或保存异常
     */
    @PostMapping("/records/{userId}")
    public LearningRecord addRecord(@RequestHeader(value = "X-Session-Token", required = false) String token,
                                    @PathVariable String userId,
                                    @RequestBody LearningRecord item) throws Exception {
        access.require(token);
        // 绑定指定学员上下文
        item.studentId = userId;
        return records.add(item);
    }

    /**
     * 修改指定学生的练习记录。
     *
     * @param token  会话凭证 Header
     * @param userId 学员用户唯一标识
     * @param id     记录流水唯一 ID
     * @param item   更新后的练习记录实体
     * @return 更新完成后的练习记录
     * @throws Exception 鉴权未通过或更新异常
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
     *
     * @param token  会话凭证 Header
     * @param userId 学员用户唯一标识
     * @param id     记录流水唯一 ID
     * @return 操作成功提示
     * @throws Exception 鉴权未通过或删除异常
     */
    @DeleteMapping("/records/{userId}/{id}")
    public Map<String, Object> deleteRecord(@RequestHeader(value = "X-Session-Token", required = false) String token,
                                            @PathVariable String userId,
                                            @PathVariable String id) throws Exception {
        access.require(token);
        records.delete(userId, id);
        return AdminAccess.message("记录已删除");
    }

    /**
     * 查询指定学生的错题本。
     *
     * @param token  会话凭证 Header
     * @param userId 学员用户唯一标识
     * @return 错题条目实体列表
     * @throws Exception 鉴权未通过或查询异常
     */
    @GetMapping("/mistakes/{userId}")
    public List<Mistake> mistakes(@RequestHeader(value = "X-Session-Token", required = false) String token,
                                  @PathVariable String userId) throws Exception {
        access.require(token);
        return mistakes.list(userId, null, null);
    }

    /**
     * 为指定学生手动新增一条错题。
     *
     * @param token  会话凭证 Header
     * @param userId 学员用户唯一标识
     * @param item   错题实体
     * @return 新增完成后的错题实体
     * @throws Exception 鉴权未通过或保存异常
     */
    @PostMapping("/mistakes/{userId}")
    public Mistake addMistake(@RequestHeader(value = "X-Session-Token", required = false) String token,
                              @PathVariable String userId,
                              @RequestBody Mistake item) throws Exception {
        access.require(token);
        // 绑定所属学员上下文
        item.studentId = userId;
        return mistakes.add(item);
    }

    /**
     * 修改指定学生的错题记录。
     *
     * @param token  会话凭证 Header
     * @param userId 学员用户唯一标识
     * @param id     错题唯一 ID
     * @param item   修改后的错题实体
     * @return 修改完成后的错题实体
     * @throws Exception 鉴权未通过或修改异常
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
     *
     * @param token  会话凭证 Header
     * @param userId 学员用户唯一标识
     * @param id     错题唯一 ID
     * @return 操作成功提示
     * @throws Exception 鉴权未通过或删除异常
     */
    @DeleteMapping("/mistakes/{userId}/{id}")
    public Map<String, Object> deleteMistake(@RequestHeader(value = "X-Session-Token", required = false) String token,
                                             @PathVariable String userId,
                                             @PathVariable String id) throws Exception {
        access.require(token);
        mistakes.delete(userId, id);
        return AdminAccess.message("错题已删除");
    }

    /**
     * 查询全站系统使用与学情概况。
     *
     * @param token 会话凭证 Header
     * @return 全站汇总统计画像键值对
     * @throws Exception 鉴权未通过或查询异常
     */
    @GetMapping("/stats")
    public Map<String, Object> stats(@RequestHeader(value = "X-Session-Token", required = false) String token) throws Exception {
        access.require(token);
        return stats.admin();
    }

    /**
     * 查看指定学生的学情统计画像。
     *
     * @param token  会话凭证 Header
     * @param userId 学员用户唯一标识
     * @return 学员个人统计画像键值对
     * @throws Exception 鉴权未通过或查询异常
     */
    @GetMapping("/stats/{userId}")
    public Map<String, Object> userStats(@RequestHeader(value = "X-Session-Token", required = false) String token,
                                         @PathVariable String userId) throws Exception {
        access.require(token);
        return stats.personal(userId);
    }

    /**
     * 监控当前全站实时在线用户列表。
     *
     * @param token 会话凭证 Header
     * @return 在线用户状态列表
     * @throws Exception 鉴权未通过或查询异常
     */
    @GetMapping("/online")
    public List<OnlineState> online(@RequestHeader(value = "X-Session-Token", required = false) String token) throws Exception {
        access.require(token);
        return usage.onlineUsers();
    }

    /**
     * 预览今日日报邮件内容。
     *
     * @param token 会话凭证 Header
     * @return 包含 content 属性的邮件 HTML/Markdown 文本
     * @throws Exception 鉴权未通过或预览生成异常
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
     *
     * @param token 会话凭证 Header
     * @return 发送结果状态键值对
     * @throws Exception 鉴权未通过或邮件发送异常
     */
    @PostMapping("/report/send")
    public Map<String, Object> send(@RequestHeader(value = "X-Session-Token", required = false) String token) throws Exception {
        access.require(token);
        return reports.send(true);
    }
}
