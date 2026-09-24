package com.cloud.hub.web.learning.model;

/**
 * 生字生词教学条目实体。
 *
 * @author cloud
 */
public class WordItem {
    /** 生字标识 */
    public String id;
    /** 学段 */
    public String stage;
    /** 汉字 */
    public String character;
    /** 汉语拼音 */
    public String pinyin;
    /** 组词列表 */
    public String words;
    /** 经典造句示范 */
    public String sentence;
    /** 字义阐释 */
    public String meaning;
}
