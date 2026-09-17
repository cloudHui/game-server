package com.gamer.data.gdg.excel;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.ss.usermodel.Workbook;

import com.gamer.data.log.Log;
import com.gamer.data.excel.shared.ExcelExtensions;
import com.gamer.data.excel.ui.ViewUtils;

/**
 * Excel 解析与 GD 生成入口（仅 xlsx；View 展示在选中文件时再加载 Workbook）。
 */
public class ExcelOperate {

    static {
        applyLargeFileZipSettings();
    }

    /** 是否已初始化 Workbook */
    public boolean init = false;
    /** Excel 文件 */
    public File file;
    /** POI Workbook（xlsx 展示用，按需打开） */
    public Workbook workbook = null;
    /** 轻量探测得到的 Sheet 名（扫描目录时填充，避免预加载整本 Workbook） */
    private List<String> probedSheetNames = Collections.emptyList();
    /** 文件 Path */
    public final Path path;
    /** 文件名 */
    public final String fileName;
    /** 绝对路径 */
    public final String filePath;

    /**
     * 构造。
     *
     * @param file
     *            Excel 文件
     */
    public ExcelOperate(File file) {
        this.file = file;
        path = file.toPath();
        fileName = file.getName();
        filePath = file.getAbsolutePath();
    }

    /**
     * 是否为 xlsx 文件。
     *
     * @param name
     *            文件名
     * @return 是否 xlsx
     */
    public static boolean isNotXlsxFile(String name) {
        return name == null || !name.toLowerCase().trim().endsWith(ExcelExtensions.FILE_EXT_XLSX);
    }

    /**
     * Sheet 名是否合法。
     *
     * @param name
     *            Sheet 名
     * @return 是否合法
     */
    public static boolean isValidSheetName(String name) {
        return Pattern.matches("[a-zA-Z][a-zA-Z_]+", name);
    }

    /** 单条 zip 项解压上限：配置表 sheet.xml 可略超 200MB（如 Pay.xlsx 的 sheet14 ≈ 200.003MB） */
    private static final long MAX_ZIP_ENTRY_BYTES = 512L * 1024L * 1024L;

    /** 共享字符串等文本块上限 */
    private static final long MAX_ZIP_TEXT_BYTES = 256L * 1024L * 1024L;

    /**
     * 放宽 POI 对 xlsx 压缩包的限制，避免配置表被误判为 Zip Bomb。
     * <p>
     * 须在任意 OPCPackage / XSSFWorkbook 打开前调用（类加载时已执行一次）。
     * </p>
     */
    public static void applyLargeFileZipSettings() {
        ZipSecureFile.setMinInflateRatio(0.001);
        ZipSecureFile.setMaxEntrySize(MAX_ZIP_ENTRY_BYTES);
        ZipSecureFile.setMaxTextSize(MAX_ZIP_TEXT_BYTES);
    }

    /**
     * 把 {@link Log} 转成样式失败回调；log 为 null 时返回 null（由 {@link XlsxStyles} 写 stderr）。
     *
     * @param log
     *            日志，可为 null
     * @return 回调或 null
     */
    public static XlsxStyles.Warn warnFromLog(final Log log) {
        if (log == null) {
            return null;
        }
        return new XlsxStyles.Warn() {
            @Override
            public void warn(String message) {
                log.logMessage(message, true);
            }
        };
    }

    /**
     * 目录扫描用轻量校验：仅检查存在、扩展名与非空（不打开 OPC，避免大文件卡住扫描）。
     *
     * @param log
     *            日志
     * @return 是否可作为配置表候选
     */
    public boolean probeReadable(Log log) {
        if (file == null) {
            file = new File(filePath);
        }
        if (!file.exists()) {
            log(log, "ExcelOperate.probe 文件不存在: " + fileName);
            return false;
        }
        if (isNotXlsxFile(fileName)) {
            log(log, "ExcelOperate.probe 仅支持 xlsx: " + fileName);
            return false;
        }
        if (file.length() == 0L) {
            log(log, "ExcelOperate.probe 文件为空: " + fileName);
            return false;
        }
        return true;
    }

    /**
     * SAX 读取 Sheet 名列表（不打开 Workbook，供 GD 对比窗口使用）。
     *
     * @param log
     *            日志
     * @return 是否成功读到 Sheet 列表（与是否存在合法英文名无关）
     */
    public boolean probeNotSheetNames(Log log) {
        if (file == null) {
            file = new File(filePath);
        }
        if (!probeReadable(log)) {
            return true;
        }
        if (probedSheetNames != null && !probedSheetNames.isEmpty()) {
            return false;
        }
        try {
            XlsxOpcSession session = XlsxOpcSession.open(file);
            try {
                probedSheetNames = new ArrayList<>(session.readSheetNames());
            } finally {
                session.close();
            }
            return false;
        } catch (Exception e) {
            probedSheetNames = Collections.emptyList();
            log(log, "ExcelOperate.probeSheetNames 失败: " + fileName + " (" + formatFileSize(file.length()) + ") 原因: "
                + formatException(e));
            return true;
        }
    }

    /**
     * 绑定预扫描得到的 Sheet 名（不打开 Workbook）。
     */
    public void bindProbedSheetNames(List<String> sheetNames) {
        if (sheetNames == null || sheetNames.isEmpty()) {
            probedSheetNames = Collections.emptyList();
        } else {
            probedSheetNames = new ArrayList<>(sheetNames);
        }
    }

    /**
     * 返回 SAX 探测到的全部 Sheet 名（未过滤）。
     */
    public List<String> getProbedSheetNames() {
        if (probedSheetNames == null) {
            return Collections.emptyList();
        }
        return new ArrayList<>(probedSheetNames);
    }

    /**
     * 返回符合配置表命名规则的 Sheet 名（优先用已打开 Workbook，否则用 SAX 探测结果）。
     */
    public List<String> getConfigSheetNames() {
        List<String> result = new ArrayList<>();
        if (init && workbook != null) {
            int count = workbook.getNumberOfSheets();
            for (int i = 0; i < count; i++) {
                String name = workbook.getSheetAt(i).getSheetName();
                if (isValidSheetName(name)) {
                    result.add(name);
                }
            }
            return result;
        }
        if (probedSheetNames != null) {
            for (String name : probedSheetNames) {
                if (isValidSheetName(name)) {
                    result.add(name);
                }
            }
        }
        return result;
    }


    /**
     * 关闭 Workbook，保留文件引用供再次打开。
     */
    public void releaseWorkbook() {
        try {
            if (this.workbook != null) {
                this.workbook.close();
            }
        } catch (Exception ignored) {
            // 关闭失败时忽略
        }
        this.workbook = null;
        this.init = false;
    }

    private static String formatException(Exception e) {
        String reason = e.getClass().getSimpleName();
        if (e.getMessage() != null && !e.getMessage().isEmpty()) {
            reason = reason + ": " + e.getMessage();
        }
        return reason;
    }

    private static String formatFileSize(long bytes) {
        return ViewUtils.formatFileSizeForLog(bytes);
    }

    /**
     * 写日志。
     */
    private void log(Log log, String message) {
        if (log != null) {
            log.logMessage(message, true);
        } else {
            System.out.println(message);
        }
    }
}
