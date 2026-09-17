package com.gamer.data;

import com.gamer.data.excel.diff.DiffCheck;
import com.gamer.data.gdg.excel.ExcelOperate;
import com.gamer.data.message.Util;

/**
 * StrategyTool 入口：Excel / GD 对比、生成、复制。
 *
 * @author liuyunhui
 * @date 2025/12/26
 */
public class StrategyTool {
    public static String GD_PATH = "/GD/data";

    public static String XML_PATH = "/ConfigurationExcel";

    public static void main(String[] args) {
        // 这个单独的工具就在 ConfigurationExcel/xls2gd_zh 目录下
        ExcelOperate.applyLargeFileZipSettings();

        if (!Util.inVM()) {
            GD_PATH = CodeTool.GD_PATH;
            XML_PATH = CodeTool.XML_PATH;
        }
        new DiffCheck().view(GD_PATH, XML_PATH, false);
    }
}
