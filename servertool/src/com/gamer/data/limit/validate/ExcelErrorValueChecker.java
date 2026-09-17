package com.gamer.data.limit.validate;

/**
 * Excel 标准错误值与未求值公式检测。
 */
public class ExcelErrorValueChecker {

    private static final String[] EXCEL_ERROR_LITERALS =
        {"#N/A", "#REF!", "#VALUE!", "#DIV/0!", "#NAME?", "#NULL!", "#NUM!"};

    public static final String REASON_EXCEL_ERROR = "Excel错误值";

    private ExcelErrorValueChecker() {}

    public static boolean isExcelErrorLiteral(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        if (value.startsWith("ERROR:")) {
            return true;
        }
        for (String excelErrorLiteral : EXCEL_ERROR_LITERALS) {
            if (excelErrorLiteral.equals(value)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isUnexpandedFormula(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        return value.startsWith("=");
    }

    public static boolean isExcelErrorValue(String value) {
        return isExcelErrorLiteral(value) || isUnexpandedFormula(value);
    }
}
