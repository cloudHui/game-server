package com.gamer.data.excel.modelgen.domain;

/**
 * 数组类型分析结果
 */
public class ArrayTypeAnalysisResult {
    public final boolean hasTwoDimensionalStructure;
    public final boolean allInt;

    public ArrayTypeAnalysisResult(boolean hasTwoDimensionalStructure, boolean allInt) {
        this.hasTwoDimensionalStructure = hasTwoDimensionalStructure;
        this.allInt = allInt;
    }
}