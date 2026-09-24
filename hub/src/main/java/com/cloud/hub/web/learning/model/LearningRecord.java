package com.cloud.hub.web.learning.model;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 学生单次学习测试练习记录。
 *
 * @author cloud
 */
public class LearningRecord {
    /** 记录唯一标识 */
    public String id;
    /** 所属学生 ID */
    public String studentId;
    /** 学科 */
    public String subject;
    /** 功能模块 (如口算、古诗、单词等) */
    public String module;
    /** 所属学段 */
    public String stage;
    /** 题目总数 */
    public int total;
    /** 正确题数 */
    public int correct;
    /** 作答耗时（秒） */
    public long durationSeconds;
    /** 扩展答题明细 */
    public Map<String, Object> details;
    /** 完成提交时间 */
    public LocalDateTime createdAt;
}
