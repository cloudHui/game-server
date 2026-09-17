package com.gamer.data.excel.modelgen.domain;

public class ArrayValue {
    /** 分隔符类型：SPLIT_PIPE 或 SPLIT_COMMA */
    public String divide = null;

    /** 是否为一维数组 */
    public boolean array;

    /**
     * 是否存在二维数组结构
     */
    public boolean arrayTwo;

    /**
     * 一维数组是否为 int 类型（true: int[], false: String[]） null 表示还未确定，需要继续检查
     */
    public Boolean isIntArray = null;

    /**
     * 二维数组是否为 int 类型（true: int[][], false: String[][]） null 表示还未确定，需要继续检查
     */
    public Boolean isIntArrayTwo = null;

    public ArrayValue() {}
}
