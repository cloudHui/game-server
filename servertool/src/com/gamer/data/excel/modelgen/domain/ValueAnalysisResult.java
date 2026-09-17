package com.gamer.data.excel.modelgen.domain;

/**
 * 单个值分析结果
 */
public class ValueAnalysisResult {
    public final boolean isTwoDimensional;
    public final boolean isNumeric;
    public final String[] subValues;

    public ValueAnalysisResult(boolean isTwoDimensional, boolean isNumeric, String[] subValues) {
        this.isTwoDimensional = isTwoDimensional;
        this.isNumeric = isNumeric;
        this.subValues = subValues;
    }
}