package com.cloud.hub.web.learning.model;

import java.time.LocalDateTime;

/**
 * 错题本记录实体。
 *
 * @author cloud
 */
public class Mistake {
    /** 错题唯一标识 */
    public String id;
    /** 所属学生 ID */
    public String studentId;
    /** 学科 */
    public String subject;
    /** 功能模块 */
    public String module;
    /** 学段 */
    public String stage;
    /** 题目题干 */
    public String question;
    /** 学生的错误作答 */
    public String userAnswer;
    /** 标准参考答案 */
    public String correctAnswer;
    /** 错误归因分类 */
    public String errorType;
    /** 累计做错次数 */
    public int errorCount;
    /** 累计重练复习次数 */
    public int reviewCount;
    /** 连续答对次数（用于掌握判定） */
    public int consecutiveCorrect;
    /** 当前状态 (active/resolved) */
    public String status;
    /** 首次做错时间 */
    public LocalDateTime firstWrongAt;
    /** 最近一次做错时间 */
    public LocalDateTime lastWrongAt;
    /** 最近一次复习时间 */
    public LocalDateTime lastReviewedAt;
}
