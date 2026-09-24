package com.cloud.hub.web.learning.model;

import java.time.LocalDateTime;

/**
 * 学习资源与习题内容实体。
 *
 * @author cloud
 */
public class ContentItem {
    /** 唯一编号 */
    public String id;
    /** 学科分类 (chinese/math/english) */
    public String subject;
    /** 年级学段 */
    public String stage;
    /** 内容类型 (poetry/word/formula 等) */
    public String type;
    /** 标题 */
    public String title;
    /** 正文或题目描述 */
    public String body;
    /** 参考答案 */
    public String answer;
    /** 是否启用 */
    public boolean enabled = true;
    /** 创建时间 */
    public LocalDateTime createdAt;
    /** 最近更新时间 */
    public LocalDateTime updatedAt;
}
