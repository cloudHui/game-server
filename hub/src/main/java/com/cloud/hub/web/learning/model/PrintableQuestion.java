package com.cloud.hub.web.learning.model;

/**
 * 课后打印习题纸条目实体。
 *
 * @author cloud
 */
public class PrintableQuestion {
    /** 题目标识 */
    public String id;
    /** 题目类型 (math/word/poem 等) */
    public String type;
    /** 题干文本 */
    public String text;
    /** 标准参考答案 */
    public String answer;

    public PrintableQuestion(String id, String type, String text, String answer) {
        this.id = id;
        this.type = type;
        this.text = text;
        this.answer = answer;
    }
}
