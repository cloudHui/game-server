package com.gamer.data;

import com.gamer.data.excel.entry.DataBuilderCheck;
import com.gamer.data.message.Util;

/**
 * CodeTool 入口：生成模型代码、Excel/GD 看表。
 *
 * @author liuyunhui
 * @date 2025/12/5
 */
public class CodeTool {
    public static String GD_PATH = "/Document/DevelopmentGD&ConfigurationExcel/GD/data";

    public static String XML_PATH = "/Document/DevelopmentGD&ConfigurationExcel/ConfigurationExcel";

    public static void main(String[] args) {
        new DataBuilderCheck().view(GD_PATH, XML_PATH, Util.inVM());
    }
}
