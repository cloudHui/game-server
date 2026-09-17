package com.gamer.data.excel.core;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import com.gamer.data.log.Log;
import com.gamer.data.excel.shared.FileWithSheets;
import com.gamer.data.gdg.excel.ExcelOperate;
import com.gamer.data.gdg.excel.XlsxSheetStreamReader;

/**
 * 配置目录预扫描：仅读取 xlsx 的 Sheet 名，不读列与数据行。
 */
public final class ExcelCatalogLoader {

    /**
     * 禁止实例化。
     */
    private ExcelCatalogLoader() {}

    /**
     * 扫描单个 xlsx，提取合法英文 Sheet 名。
     *
     * @param file
     *            xlsx 文件
     * @param log
     *            日志输出；不可读时写普通日志
     * @return catalog 条目；不可读或无合法 Sheet 时返回 null
     * @throws Exception
     *             读取 Sheet 名失败
     */
    public static ExcelCatalogEntry scanEntry(File file, Log log) throws Exception {
        // 探测文件是否可被 SAX 流式读取
        ExcelOperate op = new ExcelOperate(file);
        if (!op.probeReadable(log)) {
            return null;
        }
        // 只读 workbook 元数据中的 Sheet 名列表
        List<String> allNames = XlsxSheetStreamReader.readSheetNames(file);
        List<String> validNames = new ArrayList<>();
        for (String sheetName : allNames) {
            if (sheetName == null) {
                continue;
            }
            String trimmed = sheetName.trim();
            // 与 GD 流水线一致的英文 Sheet 名规则
            if (ExcelSheetRules.isValidConfigSheetName(trimmed)) {
                validNames.add(trimmed);
            }
        }
        if (validNames.isEmpty()) {
            return null;
        }
        return new ExcelCatalogEntry(file.getName(), file, validNames);
    }

    /**
     * 由 catalog 条目构造模型生成用 stub（空列列表，展开 Sheet 时再懒加载列）。
     *
     * @param entry
     *            目录扫描条目
     * @return FileWithSheets stub
     */
    public static FileWithSheets toFileWithSheetsStub(ExcelCatalogEntry entry) {
        FileWithSheets stub = new FileWithSheets(entry.fileName);
        stub.sourceFile = entry.sourceFile;
        for (int i = 0; i < entry.sheetNames.size(); i++) {
            String sheetName = entry.sheetNames.get(i);
            // 预置空列列表，仅登记 Sheet 名
            stub.getSheet(sheetName);
        }
        return stub;
    }
}
