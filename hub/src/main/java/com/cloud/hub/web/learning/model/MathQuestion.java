package com.cloud.hub.web.learning.model;

/**
 * 口算数学题实体。
 *
 * @author cloud
 */
public class MathQuestion {
    /** 题目唯一标识 */
    public String id;
    /** 左运算数 */
    public int left;
    /** 右运算数 */
    public int right;
    /** 运算符 (+, -, *, /) */
    public String operator;
    /** 题目展示文本 */
    public String text;
    /** 正确数值答案 */
    public int answer;

    public MathQuestion(String id, int left, int right, String operator, int answer) {
        this.id = id;
        this.left = left;
        this.right = right;
        this.operator = operator;
        this.text = left + " " + operator + " " + right + " = ?";
        this.answer = answer;
    }
}
