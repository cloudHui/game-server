package com.cloud.hub.web.learning.service;

import com.cloud.hub.web.learning.model.LearningRecord;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 学生学习历程记录管理服务。
 * <p>
 * 提供学习做题记录提交、按学生拉取历史流水、修改、删除及个人练习仪表盘数据汇总。
 *
 * @author cloud
 */
@Service
public class RecordService {

    private final JsonFileStore store;

    public RecordService(JsonFileStore store) {
        this.store = store;
    }

    /**
     * 新增一条学生的学习做题记录。
     *
     * @param record 学习记录对象
     * @return 填充了全局唯一 ID 与时间戳的记录
     * @throws Exception 存储异常
     */
    public synchronized LearningRecord add(LearningRecord record) throws Exception {
        validateId(record.studentId);
        validate(record);
        List<LearningRecord> records = list(record.studentId);
        record.id = UUID.randomUUID().toString();
        record.createdAt = LocalDateTime.now();
        if (record.stage == null) {
            record.stage = "幼小衔接";
        }
        records.add(0, record);
        store.write(store.path("records", record.studentId), records);
        return record;
    }

    /**
     * 查询指定学生的全部历史学习记录列表。
     *
     * @param studentId 学生档案 ID
     * @return 记录列表
     * @throws Exception 存储异常
     */
    public List<LearningRecord> list(String studentId) throws Exception {
        if (!StudentService.isValidArchiveId(studentId)) {
            return new ArrayList<>();
        }
        return store.readList(store.path("records", studentId), new TypeReference<List<LearningRecord>>() {
        });
    }

    /**
     * 更新指定学生的某条历史记录。
     *
     * @param studentId 学生档案 ID
     * @param recordId  记录唯一标识
     * @param changes   变更属性
     * @return 更新后的记录
     * @throws Exception 存储异常
     */
    public synchronized LearningRecord update(String studentId, String recordId, LearningRecord changes) throws Exception {
        validate(changes);
        List<LearningRecord> records = list(studentId);
        for (int i = 0; i < records.size(); i++) {
            LearningRecord current = records.get(i);
            if (recordId.equals(current.id)) {
                changes.id = current.id;
                changes.studentId = studentId;
                if (changes.createdAt == null) {
                    changes.createdAt = current.createdAt;
                }
                records.set(i, changes);
                store.write(store.path("records", studentId), records);
                return changes;
            }
        }
        throw new IllegalArgumentException("找不到学习记录");
    }

    /**
     * 删除指定学生的某条历史记录。
     *
     * @param studentId 学生档案 ID
     * @param recordId  记录唯一标识
     * @throws Exception 存储异常
     */
    public synchronized void delete(String studentId, String recordId) throws Exception {
        List<LearningRecord> records = list(studentId);
        if (!records.removeIf(item -> recordId.equals(item.id))) {
            throw new IllegalArgumentException("找不到学习记录");
        }
        store.write(store.path("records", studentId), records);
    }

    /**
     * 聚合计算指定学生的个人学习仪表盘数据看板。
     *
     * @param studentId 学生档案 ID
     * @return 仪表盘统计模型
     * @throws Exception 存储异常
     */
    public Dashboard dashboard(String studentId) throws Exception {
        List<LearningRecord> records = list(studentId);
        LocalDate today = LocalDate.now();
        Dashboard dashboard = new Dashboard();
        dashboard.totalSessions = records.size();

        for (LearningRecord record : records) {
            dashboard.totalQuestions += record.total;
            dashboard.totalCorrect += record.correct;
            if (record.createdAt != null && record.createdAt.toLocalDate().equals(today)) {
                dashboard.todaySessions++;
                dashboard.todayQuestions += record.total;
                dashboard.todaySeconds += record.durationSeconds;
            }
        }
        dashboard.accuracy = dashboard.totalQuestions == 0 ? 0 :
                Math.round(dashboard.totalCorrect * 1000.0 / dashboard.totalQuestions) / 10.0;
        return dashboard;
    }

    private void validateId(String id) {
        if (!StudentService.isValidArchiveId(id)) {
            throw new IllegalArgumentException("无效的学习档案");
        }
    }

    private void validate(LearningRecord record) {
        if (record.subject == null || record.subject.trim().isEmpty()) {
            throw new IllegalArgumentException("学习科目不能为空");
        }
        if (record.module == null || record.module.trim().isEmpty()) {
            throw new IllegalArgumentException("学习模块不能为空");
        }
        if (record.total < 0 || record.correct < 0 || record.correct > record.total) {
            throw new IllegalArgumentException("完成数和正确数不正确");
        }
        if (record.durationSeconds < 0 || record.durationSeconds > 86400) {
            throw new IllegalArgumentException("学习时长不正确");
        }
    }

    /**
     * 仪表盘统计视图模型。
     */
    public static class Dashboard {
        public int totalSessions;
        public int todaySessions;
        public int totalQuestions;
        public int totalCorrect;
        public int todayQuestions;
        public long todaySeconds;
        public double accuracy;
        public int pendingMistakes;
    }
}
