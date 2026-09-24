package com.cloud.hub.web.learning.model;

import java.time.LocalDateTime;

/**
 * 小学应用题生成模板实体。
 *
 * @author cloud
 */
public class WordProblemTemplate {
    /** 模板编号 */
    public String id;
    /** 学段 */
    public String stage;
    /** 运算方式 (+, -, *, /) */
    public String operation;
    /** 题目文本模板插值表达式 */
    public String template;
    /** 最大运算数值上限 */
    public int maxNumber;
    /** 是否启用 */
    public boolean enabled = true;
    /** 创建时间 */
    public LocalDateTime createdAt;
}
