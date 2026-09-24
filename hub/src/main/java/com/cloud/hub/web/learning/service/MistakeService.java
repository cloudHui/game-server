package com.cloud.hub.web.learning.service;

import com.cloud.hub.web.learning.model.Mistake;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 错题本归集与重练复习服务。
 * <p>
 * 自动识别去重相同错题、累加错误计数、根据复习表现（连续答对次数）自适应流转掌握状态。
 *
 * @author cloud
 */
@Service
public class MistakeService {

    private final JsonFileStore store;

    public MistakeService(JsonFileStore store) {
        this.store = store;
    }

    /**
     * 录入一条新的错题（如已存在同题则累加错题次数并重置复习状态）。
     *
     * @param incoming 错题实体
     * @return 最终持久化的错题实体
     * @throws Exception 存储异常
     */
    public synchronized Mistake add(Mistake incoming) throws Exception {
        validateId(incoming.studentId);
        validate(incoming);
        List<Mistake> all = all(incoming.studentId);
        Mistake existing = all.stream().filter(item -> sameQuestion(item, incoming)).findFirst().orElse(null);
        if (existing != null) {
            existing.errorCount++;
            existing.userAnswer = incoming.userAnswer;
            existing.lastWrongAt = LocalDateTime.now();
            existing.consecutiveCorrect = 0;
            existing.status = "待复习";
            store.write(store.path("mistakes", incoming.studentId), all);
            return existing;
        }

        incoming.id = UUID.randomUUID().toString();
        incoming.errorCount = 1;
        incoming.reviewCount = 0;
        incoming.consecutiveCorrect = 0;
        incoming.status = "待复习";
        incoming.firstWrongAt = LocalDateTime.now();
        incoming.lastWrongAt = incoming.firstWrongAt;
        all.add(0, incoming);
        store.write(store.path("mistakes", incoming.studentId), all);
        return incoming;
    }

    /**
     * 按科目和状态条件过滤查询错题列表。
     *
     * @param studentId 学生档案 ID
     * @param subject   学科过滤
     * @param status    掌握状态过滤
     * @return 错题列表
     * @throws Exception 存储异常
     */
    public List<Mistake> list(String studentId, String subject, String status) throws Exception {
        return all(studentId).stream()
                .filter(item -> subject == null || subject.isEmpty() || subject.equals(item.subject))
                .filter(item -> status == null || status.isEmpty() || status.equals(item.status))
                .collect(Collectors.toList());
    }

    /**
     * 提交针对某道错题的复习答题结果。
     *
     * @param studentId 学生档案 ID
     * @param mistakeId 错题 ID
     * @param correct   本次复习是否答对
     * @return 状态流转后的错题实体
     * @throws Exception 存储异常
     */
    public synchronized Mistake review(String studentId, String mistakeId, boolean correct) throws Exception {
        List<Mistake> all = all(studentId);
        Mistake target = all.stream().filter(item -> mistakeId.equals(item.id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("找不到这条错题"));
        target.reviewCount++;
        target.lastReviewedAt = LocalDateTime.now();

        if (correct) {
            target.consecutiveCorrect++;
            target.status = target.consecutiveCorrect >= 3 ? "已掌握" : target.consecutiveCorrect >= 2 ? "基本掌握" : "复习中";
        } else {
            target.errorCount++;
            target.consecutiveCorrect = 0;
            target.status = "待复习";
            target.lastWrongAt = LocalDateTime.now();
        }
        store.write(store.path("mistakes", studentId), all);
        return target;
    }

    /**
     * 查询学生当前尚未完全掌握的错题总数。
     *
     * @param studentId 学生档案 ID
     * @return 待复习错题数
     * @throws Exception 存储异常
     */
    public int pendingCount(String studentId) throws Exception {
        return (int) all(studentId).stream().filter(item -> !"已掌握".equals(item.status)).count();
    }

    /**
     * 更新错题属性信息。
     *
     * @param studentId 学生档案 ID
     * @param mistakeId 错题 ID
     * @param changes   变更对象
     * @return 更新后的错题
     * @throws Exception 存储异常
     */
    public synchronized Mistake update(String studentId, String mistakeId, Mistake changes) throws Exception {
        validate(changes);
        List<Mistake> all = all(studentId);
        for (int i = 0; i < all.size(); i++) {
            if (mistakeId.equals(all.get(i).id)) {
                Mistake current = all.get(i);
                mergeMistakeChanges(current, changes, studentId);
                all.set(i, current);
                store.write(store.path("mistakes", studentId), all);
                return current;
            }
        }
        throw new IllegalArgumentException("找不到错题");
    }

    private void mergeMistakeChanges(Mistake current, Mistake changes, String studentId) {
        changes.id = current.id;
        changes.studentId = studentId;
        if (changes.firstWrongAt == null) {
            changes.firstWrongAt = current.firstWrongAt;
        }
        if (changes.lastWrongAt == null) {
            changes.lastWrongAt = current.lastWrongAt;
        }
        if (changes.lastReviewedAt == null) {
            changes.lastReviewedAt = current.lastReviewedAt;
        }
        if (changes.errorCount <= 0) {
            changes.errorCount = current.errorCount;
        }
        if (changes.reviewCount <= 0) {
            changes.reviewCount = current.reviewCount;
        }
        if (changes.consecutiveCorrect <= 0) {
            changes.consecutiveCorrect = current.consecutiveCorrect;
        }
        if (changes.status == null || changes.status.trim().isEmpty()) {
            changes.status = current.status;
        }
    }

    /**
     * 删除指定错题。
     *
     * @param studentId 学生档案 ID
     * @param mistakeId 错题 ID
     * @throws Exception 存储异常
     */
    public synchronized void delete(String studentId, String mistakeId) throws Exception {
        List<Mistake> all = all(studentId);
        if (!all.removeIf(item -> mistakeId.equals(item.id))) {
            throw new IllegalArgumentException("找不到错题");
        }
        store.write(store.path("mistakes", studentId), all);
    }

    private List<Mistake> all(String studentId) throws Exception {
        if (!StudentService.isValidArchiveId(studentId)) {
            return new ArrayList<>();
        }
        return store.readList(store.path("mistakes", studentId), new TypeReference<List<Mistake>>() {
        });
    }

    private boolean sameQuestion(Mistake a, Mistake b) {
        return safe(a.subject).equals(safe(b.subject)) && safe(a.question).equals(safe(b.question));
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private void validateId(String id) {
        if (!StudentService.isValidArchiveId(id)) {
            throw new IllegalArgumentException("无效的学习档案");
        }
    }

    private void validate(Mistake mistake) {
        if (mistake.subject == null || mistake.subject.trim().isEmpty()) {
            throw new IllegalArgumentException("错题科目不能为空");
        }
        if (mistake.question == null || mistake.question.trim().isEmpty()) {
            throw new IllegalArgumentException("错题内容不能为空");
        }
        if (mistake.correctAnswer == null) {
            mistake.correctAnswer = "";
        }
    }
}
