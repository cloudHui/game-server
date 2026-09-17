package com.gamer.data.gdg.excel;

import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.model.StylesTable;

/**
 * xlsx 样式表读取：只依赖 POI，供 GUI 与 MCP Excel 共用。
 * <p>
 * fat-jar 下 XMLBeans 解析 dxfs（条件格式）时，工作线程上下文 ClassLoader 可能找不到已打进包的
 * .xsb；先钉到本类 ClassLoader，仍失败则退回空样式，避免整表读列失败。
 * </p>
 */
public final class XlsxStyles {

    /**
     * 样式读取失败时的提示回调。
     */
    public interface Warn {

        /**
         * 输出已格式化的失败说明。
         *
         * @param message
         *            中文说明，含异常摘要
         */
        void warn(String message);
    }

    /**
     * 禁止实例化。
     */
    private XlsxStyles() {}

    /**
     * 读取样式表；失败时写 stderr 并返回空样式。
     *
     * @param reader
     *            XSSFReader，可为 null
     * @return 样式表，不会为 null
     */
    public static StylesTable read(XSSFReader reader) {
        return read(reader, null);
    }

    /**
     * 读取样式表。fat-jar 下先钉 TCCL；失败则空样式，日期/数字可能按原始值显示。
     *
     * @param reader
     *            XSSFReader，可为 null
     * @param warn
     *            失败回调，为 null 时写 stderr
     * @return 样式表，不会为 null
     */
    public static StylesTable read(XSSFReader reader, Warn warn) {
        ClassLoader saved = Thread.currentThread().getContextClassLoader();
        ClassLoader toolCl = XlsxStyles.class.getClassLoader();
        if (toolCl != null) {
            Thread.currentThread().setContextClassLoader(toolCl);
        }
        try {
            if (reader == null) {
                return new StylesTable();
            }
            StylesTable styles = reader.getStylesTable();
            if (styles == null) {
                return new StylesTable();
            }
            return styles;
        } catch (Exception e) {
            String msg = "读取 Excel 样式失败，使用空样式: " + e.getMessage();
            if (warn != null) {
                warn.warn(msg);
            } else {
                System.err.println(msg);
            }
            return new StylesTable();
        } finally {
            Thread.currentThread().setContextClassLoader(saved);
        }
    }
}
