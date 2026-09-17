package com.gamer.data.gdg.util;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;

/**
 * 日期格式单元格按 Excel 序列号输出（如 32663），不按显示值输出（如 6/4/89）。
 */
public class GdDataFormatter extends DataFormatter {

    @Override
    public String formatRawCellContents(double value, int formatIndex, String formatString) {
        return formatForGd(value, formatIndex, formatString, false);
    }

    @Override
    public String formatRawCellContents(double value, int formatIndex, String formatString, boolean use1904Windowing) {
        return formatForGd(value, formatIndex, formatString, use1904Windowing);
    }

    private String formatForGd(double value, int formatIndex, String formatString, boolean use1904Windowing) {
        if (DateUtil.isADateFormat(formatIndex, formatString) && isWholeNumber(value)) {
            return String.valueOf((long)value);
        }
        return super.formatRawCellContents(value, formatIndex, formatString, use1904Windowing);
    }

    private static boolean isWholeNumber(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value) && value == Math.floor(value);
    }
}
