package com.gamer.data.excel.modelgen.generator;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.poi.hssf.usermodel.HSSFCell;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import com.gamer.data.excel.shared.FileWithSheets;
import com.gamer.data.excel.modelgen.ModelGenContext;
import com.gamer.data.excel.modelgen.view.ModelGenUiDialogs;
import com.gamer.data.excel.modelgen.domain.ArrayTypeAnalysisResult;
import com.gamer.data.excel.modelgen.domain.ArrayValue;
import com.gamer.data.excel.modelgen.domain.ConstType;
import com.gamer.data.excel.modelgen.domain.ManagerGenInfo;
import com.gamer.data.excel.modelgen.domain.ManagerSelectionItem;
import com.gamer.data.excel.modelgen.domain.ServerConfigSelection;
import com.gamer.data.excel.modelgen.domain.ValueAnalysisResult;
import com.gamer.data.excel.shared.Title;

/**
 * 生成数据模板和GameConfig枚举
 *
 * @author liuyunhui
 */
public class Generator {

    // ================ 常量定义 ================

    /** 文件类型常量 */
    private static final String XLSX_EXTENSION = "xlsx";
    private static final String XLS_EXTENSION = "xls";

    /** 特殊处理的工作簿名称 */
    private static final String GAME_CONFIG = "GameConfig";
    private static final String MESSAGE = "Message";

    /** 数据模型包名前缀 */
    private static final String DATA_PACKAGE_PREFIX = "com.gow.common.config.";

    private static ModelGenContext ctx = ModelGenContext.NOOP;

    // ================ 核心生成逻辑 ================

    /** @param withManagerConfig true=弹框选 Manager/Config；false=只写 Config，不弹框 */
    public static void genCode(List<FileWithSheets> allFiles, ModelGenContext modelGenContext,
        boolean withManagerConfig) {
        try {
            ctx = modelGenContext;
            List<ManagerGenInfo> allPendingManagers = new ArrayList<>();
            for (FileWithSheets file : allFiles) {
                System.out.println("开始处理文件: " + file);
                ctx.logMessage("开始处理文件: " + file);
                readExcelCreateJavaHead(file, allPendingManagers);
            }
            // Config 与 limit 一并落盘（在 Manager 弹框之前）
            ctx.afterConfigFilesWritten(allFiles);
            if (withManagerConfig) {
                flushPendingManagers(ctx.getServerPath(), allPendingManagers);
            }
            System.out.println("全部生成完成");
            ctx.logMessage("全部生成完成");
        } catch (Exception e) {
            ctx.logMessage("生成模型或枚举代码失败: " + e);
            System.out.println("生成模型或枚举代码失败: " + e);
        }
    }

    /**
     * 根据展开时缓存的列集合生成 Java 配置模型（不读 Excel 列定义）。
     *
     * @param file
     *            已勾选 Sheet 与列的 Excel 子集
     * @param allPendingManagers
     *            所有待处理的 Manager 列表
     */
    public static void readExcelCreateJavaHead(FileWithSheets file, List<ManagerGenInfo> allPendingManagers) {
        String fileExtension = getFileExtension(file.excelName);
        String javaName = JavaNames.upperFirst(file.excelName.split("\\.")[0]);

        if (!file.excelName.endsWith(XLSX_EXTENSION) && !file.excelName.endsWith(XLS_EXTENSION)) {
            ctx.logMessage("不支持的文件类型: " + fileExtension);
            System.out.println("不支持的文件类型: " + fileExtension);
            return;
        }
        if (MESSAGE.equals(javaName)) {
            return;
        }
        processGenJavaCodeFromCache(ctx.getServerPath(), javaName, file, allPendingManagers);
        if (GAME_CONFIG.equals(javaName)) {
            processGameConfigEnumsFromFile(file, javaName);
        }
    }

    /**
     * 使用内存中的 Title 列表生成配置模型类（不打开 Workbook）。
     *
     * @param serverPath
     *            Server 路径
     * @param javaName
     *            Excel 对应 Java 包名前缀
     * @param sheets
     *            已勾选 Sheet 与列
     * @param allPendingManagers
     *            待生成 Manager 列表
     */
    private static void processGenJavaCodeFromCache(String serverPath, String javaName, FileWithSheets sheets,
        List<ManagerGenInfo> allPendingManagers) {
        if (sheets == null || sheets.sheets == null || sheets.sheets.isEmpty()) {
            return;
        }
        for (Map.Entry<String, List<Title>> entry : sheets.sheets.entrySet()) {
            String sheetName = entry.getKey();
            List<Title> sheetLines = entry.getValue();
            if (sheetLines == null || sheetLines.isEmpty()) {
                continue;
            }
            processSheetForJavaHead(sheetName, serverPath, javaName, sheetLines, allPendingManagers);
        }
    }

    /**
     * GameConfig 枚举生成：生成时再读 Excel 数据行（列定义仍用缓存）。
     *
     * @param file
     *            已勾选 Sheet 子集
     * @param javaName
     *            Java 包名前缀
     */
    private static void processGameConfigEnumsFromFile(FileWithSheets file, String javaName) {
        try (Workbook workbook = openWorkbookFromXmlPath(file.excelName)) {
            for (Map.Entry<String, List<Title>> entry : file.sheets.entrySet()) {
                String sheetName = entry.getKey();
                List<Title> sheetLines = entry.getValue();
                if (sheetLines == null || sheetLines.isEmpty()) {
                    continue;
                }
                Sheet sheet = workbook.getSheet(sheetName);
                if (sheet != null) {
                    processSheetForConst(sheet, ctx.getServerPath(), javaName);
                }
            }
        } catch (Exception e) {
            ctx.logMessage("GameConfig 枚举生成失败: " + e);
            System.out.println("GameConfig 枚举生成失败: " + e);
        }
    }

    /**
     * 从配置目录打开 Excel（仅 GameConfig 枚举生成使用）。
     *
     * @param excelName
     *            Excel 文件名
     * @return 工作簿
     * @throws IOException
     *             打开失败
     */
    private static Workbook openWorkbookFromXmlPath(String excelName) throws IOException {
        InputStream inputStream = Files.newInputStream(Paths.get(ctx.getXmlPath() + "\\" + excelName));
        if (excelName.endsWith(XLSX_EXTENSION)) {
            return new XSSFWorkbook(inputStream);
        }
        return new HSSFWorkbook(inputStream);
    }

    /**
     * 处理 Sheet 生成配置模型类。
     *
     * @param sheetName
     *            Sheet 名
     * @param serverPath
     *            Server 路径
     * @param javaName
     *            Java 包名前缀
     * @param titleList
     *            展开时缓存的列集合
     * @param pendingManagers
     *            待生成 Manager 列表
     */
    private static void processSheetForJavaHead(String sheetName, String serverPath, String javaName,
        List<Title> titleList, List<ManagerGenInfo> pendingManagers) {
        String finalJavaName = getJavaName(sheetName, javaName);
        try {
            String genJavaModel = genJavaModel(finalJavaName, titleList, javaName.toLowerCase());
            String replace = DATA_PACKAGE_PREFIX.replace(".", "/");
            String path = serverPath + "/common/src/" + replace + javaName.toLowerCase();
            doWrite(finalJavaName, path, genJavaModel);

            if (pendingManagers != null) {
                boolean exists = false;
                for (ManagerGenInfo info : pendingManagers) {
                    if (info != null && finalJavaName.equals(info.getConfigClassName())) {
                        exists = true;
                        break;
                    }
                }
                if (!exists) {
                    pendingManagers.add(new ManagerGenInfo(finalJavaName, javaName.toLowerCase(), sheetName, titleList));
                }
            }
        } catch (Exception e) {
            ctx.logMessage("处理Sheet生成配置模型类失败: " + javaName + " error: " + e);
            System.out.println("处理Sheet生成配置模型类失败: " + javaName + " error: " + e);
        }
    }

    /** 数据行 string 类型扫描上限（行号从第 5 行起计） */
    public static final int MAX_ARRAY_DATA_SCAN_ROWS = 200;

    /**
     * 从 xls 工作表解析列定义（仅表头，不做 string 推断）。
     *
     * @param sheets
     *            Excel 结构
     * @param sheetName
     *            Sheet 名
     * @param sheet
     *            工作表
     */
    public static void setJavaTitle(FileWithSheets sheets, String sheetName, Sheet sheet) {
        Row propertyName = sheet.getRow(0);

        if (propertyName == null) {
            System.out.println("Sheet " + sheet.getSheetName() + " 没有属性行，跳过");
            ctx.logMessage("Sheet " + sheet.getSheetName() + " 没有属性行，跳过");
            return;
        }

        getJavaTitle(propertyName, sheet, sheets.getSheet(sheetName));
    }

    /**
     * 从 SAX 读到的表头行（0~4 行）构建 Title 列表，不扫描数据行。
     *
     * @param sheets
     *            Excel 结构
     * @param sheetName
     *            Sheet 名
     * @param headerRowsByRowNum
     *            行号 -> 列号 -> 单元格文本
     */
    public static void buildTitlesFromHeaderRows(FileWithSheets sheets, String sheetName,
        Map<Integer, TreeMap<Integer, String>> headerRowsByRowNum) {
        List<Title> titleList = sheets.getSheet(sheetName);
        titleList.clear();
        TreeMap<Integer, String> propertyName = headerRowsByRowNum.get(0);
        if (propertyName == null || propertyName.isEmpty()) {
            return;
        }
        TreeMap<Integer, String> desc = headerRowsByRowNum.get(1);
        TreeMap<Integer, String> propertyType = headerRowsByRowNum.get(2);
        TreeMap<Integer, String> descAp = headerRowsByRowNum.get(3);
        // 空行 SAX 会得到非 null 的空 TreeMap，lastKey() 会抛 NoSuchElementException
        int maxCol = propertyName.lastKey();
        maxCol = maxHeaderColumnIndex(desc, maxCol);
        maxCol = maxHeaderColumnIndex(propertyType, maxCol);
        maxCol = maxHeaderColumnIndex(descAp, maxCol);
        for (int cellIndex = 0; cellIndex <= maxCol; cellIndex++) {
            Title title = processCellForTitleFromStrings(getHeaderCell(propertyName, cellIndex),
                getHeaderCell(propertyType, cellIndex), getHeaderCell(desc, cellIndex),
                getHeaderCell(descAp, cellIndex));
            if (title != null) {
                titleList.add(title);
            }
        }
    }

    /**
     * 展开时对 string 列做数据行扫描（SAX/内存行），推断 int[] / int[][] 等数组类型。
     *
     * @param sheetName
     *            Sheet 名
     * @param titles
     *            已解析的列集合
     * @param headerRowsByRowNum
     *            表头行（0~4）
     * @param dataRows
     *            数据行（从第 5 行起）
     */
    public static void refineStringTypesFromDataRows(String sheetName, List<Title> titles,
        Map<Integer, TreeMap<Integer, String>> headerRowsByRowNum, List<TreeMap<Integer, String>> dataRows) {
        if (titles == null || titles.isEmpty() || headerRowsByRowNum == null) {
            return;
        }
        Map<String, Integer> oldNameToCol = buildOldNameToColIndex(headerRowsByRowNum);
        for (Title title : titles) {
            if (title == null || !"string".equals(title.getType()) || GAME_CONFIG.equals(sheetName)) {
                continue;
            }
            Integer colIndex = oldNameToCol.get(title.getOldName());
            if (colIndex == null) {
                continue;
            }
            ArrayValue dataValue = analyzeArrayFromDataRows(dataRows, colIndex);
            ArrayValue commentValue = analyzeFromComment(title.getDes());
            ArrayValue arrayValue = mergeArrayValue(dataValue, commentValue);
            applyArrayTypeToTitle(title, arrayValue);
        }
    }

    /**
     * xls 展开：从工作表收集表头与数据行后做 string 数组推断。
     *
     * @param sheet
     *            工作表
     * @param titles
     *            列集合
     */
    public static void refineStringTypesFromSheet(Sheet sheet, List<Title> titles) {
        if (sheet == null || titles == null || titles.isEmpty()) {
            return;
        }
        Map<Integer, TreeMap<Integer, String>> headerRows = collectHeaderRowsFromSheet(sheet);
        List<TreeMap<Integer, String>> dataRows = collectDataRowsFromSheet(sheet);
        refineStringTypesFromDataRows(sheet.getSheetName(), titles, headerRows, dataRows);
    }

    private static String getHeaderCell(TreeMap<Integer, String> row, int col) {
        if (row == null) {
            return null;
        }
        return row.get(col);
    }

    /**
     * 取表头行的最大列号（空行不参与）。
     *
     * @param row
     *            表头行
     * @param currentMax
     *            当前最大列号
     * @return 合并后的最大列号
     */
    private static int maxHeaderColumnIndex(TreeMap<Integer, String> row, int currentMax) {
        if (row == null || row.isEmpty()) {
            return currentMax;
        }
        int last = row.lastKey();
        return Math.max(last, currentMax);
    }

    /**
     * 从Excel 表格中解析并生成Java 属性名称和类型的集合
     * <p>
     * 该方法遍历Excel表格的属性名行（第0行），逐列提取字段信息， 并为每个有效字段创建对应的Title对象添加到集合中。
     * <p>
     * Excel表格结构约定： - 第0行：属性名称（字段名） - 第1行：字段描述 - 第2行：属性类型（如 int, string, int[], int[][] 等） - 第3行：附加描述（可选）
     * <p>
     * 处理流程： 1. 获取描述行（第1行）、类型行（第2行）和附加描述行（第3行） 2. 遍历属性名行的每个单元格 3. 调用 processCellForTitle 方法处理每个单元格，生成Title对象 4.
     * 将有效的Title对象添加到titleList集合中 5. 对于string类型的字段，调用 checkStringData 检测实际数据结构， 自动识别并转换为合适的数组类型（如 int[], int[][] 等）
     *
     * @param propertyName
     *            属性名行（Excel第0行），包含所有字段名称
     * @param sheet
     *            Excel工作表对象，用于获取其他行的数据
     * @param titleList
     *            输出参数，用于存储解析后的Java属性名称和类型集合
     */
    private static void getJavaTitle(Row propertyName, Sheet sheet, List<Title> titleList) {
        Row desc = sheet.getRow(1);
        Row propertyType = sheet.getRow(2);
        Row descAp = sheet.getRow(3);

        for (int cellIndex = 0; cellIndex < propertyName.getPhysicalNumberOfCells(); cellIndex++) {
            Title title = processCellForTitle(propertyName, desc, propertyType, descAp, cellIndex);
            if (title != null) {
                titleList.add(title);
            }
        }
    }

    /**
     * 处理单个单元格，生成Title对象
     * <p>
     * 该方法从Excel表格的指定列中提取属性信息，并构建对应的Title对象。
     * <p>
     * 处理流程： 1. 从属性名行（第0行）获取字段名称 2. 从类型行（第2行）获取字段类型 3. 从描述行（第1行）和附加描述行（第3行）获取字段描述，并合并 4. 验证字段名和类型是否有效（非空且类型不以'_'开头） 5.
     * 规范化类型名称（如将 String 转为 string，Integer 转为 int 等） 6. 将字段名转换为驼峰命名格式
     * 
     * @param propertyName
     *            属性名行（Excel第0行）
     * @param desc
     *            描述行（Excel第1行）
     * @param propertyType
     *            类型行（Excel第2行）
     * @param descAp
     *            附加描述行（Excel第3行）
     * @param cellIndex
     *            当前处理的列索引
     * @return 生成的Title对象，如果字段无效则返回null
     */
    private static Title processCellForTitle(Row propertyName, Row desc, Row propertyType, Row descAp, int cellIndex) {
        String name = getCellValue(propertyName.getCell(cellIndex));
        String type = getCellValue(propertyType != null ? propertyType.getCell(cellIndex) : null);
        String description = getCellValue(desc != null ? desc.getCell(cellIndex) : null);
        String descApText = descAp != null ? getCellValue(descAp.getCell(cellIndex)) : null;
        return processCellForTitleFromStrings(name, type, description, descApText);
    }

    /**
     * 从表头字符串构建 Title（SAX 表头路径与 POI 路径共用）。
     */
    private static Title processCellForTitleFromStrings(String name, String type, String description,
        String descApText) {
        if (description == null) {
            description = "";
        }
        if (descApText != null && !descApText.isEmpty()) {
            description = description + " " + descApText.replace("\n", " ");
        }

        try {
            // 列名规范化：去除首尾空格及常见不可见字符，避免生成带空格的字段名
            if (name != null) {
                name = normalizeColumnName(name);
            }
            // 验证基本字段
            if (name == null || name.isEmpty() || type == null || type.isEmpty() || type.charAt(0) == '_') {
                return null;
            }

            // 保存原始类型
            String originalType = type.trim();

            // 规范化类型
            type = TypeAnalyzer.normalizeType(type);

            // 转换为驼峰命名
            String camelName = JavaNames.camelCase(name, false).replace("_", "");
            return new Title(camelName, type, description.trim(), name, originalType);
        } catch (Exception e) {
            System.out.println("处理单个单元格，生成Title对象失败: " + name + " " + type + " " + description + " error: " + e);
            ctx.logMessage("处理单个单元格，生成Title对象失败: " + name + " " + type + " " + description + " error: " + e);
            return null;
        }
    }

    /**
     * 规范化 Excel 列名：去除首尾空格及常见不可见字符（零宽字符等），避免生成带空格或不可见字符的 Java 字段名。
     *
     * @param columnName
     *            原始列名
     * @return 规范化后的列名
     */
    private static String normalizeColumnName(String columnName) {
        if (columnName == null) {
            return null;
        }
        String s = columnName.trim();
        // 去除常见不可见字符：零宽空格、零宽非连接符、零宽连接符、BOM
        s = s.replace("\u200B", "").replace("\u200C", "").replace("\u200D", "").replace("\uFEFF", "");
        return s.trim();
    }

    /**
     * 从表头第 0 行构建 oldName -> 列号映射。
     *
     * @param headerRowsByRowNum
     *            表头行
     * @return 属性名到列索引
     */
    private static Map<String, Integer> buildOldNameToColIndex(Map<Integer, TreeMap<Integer, String>> headerRowsByRowNum) {
        Map<String, Integer> oldNameToCol = new LinkedHashMap<>();
        TreeMap<Integer, String> propertyName = headerRowsByRowNum.get(0);
        if (propertyName == null) {
            return oldNameToCol;
        }
        for (Map.Entry<Integer, String> entry : propertyName.entrySet()) {
            String name = entry.getValue();
            if (name != null && !name.trim().isEmpty()) {
                oldNameToCol.put(name.trim(), entry.getKey());
            }
        }
        return oldNameToCol;
    }

    /**
     * 从 POI Sheet 收集表头行（0~4）。
     *
     * @param sheet
     *            工作表
     * @return 行号 -> 列值
     */
    private static Map<Integer, TreeMap<Integer, String>> collectHeaderRowsFromSheet(Sheet sheet) {
        Map<Integer, TreeMap<Integer, String>> headerRows = new TreeMap<>();
        for (int rowNum = 0; rowNum < 5; rowNum++) {
            Row row = sheet.getRow(rowNum);
            if (row == null) {
                continue;
            }
            TreeMap<Integer, String> cols = new TreeMap<>();
            for (int col = 0; col < row.getPhysicalNumberOfCells(); col++) {
                String value = getCellValue(row.getCell(col));
                if (value != null) {
                    cols.put(col, value);
                }
            }
            headerRows.put(rowNum, cols);
        }
        return headerRows;
    }

    /**
     * 从 POI Sheet 收集数据行（第 5 行起，最多 300 物理行）。
     *
     * @param sheet
     *            工作表
     * @return 数据行列表
     */
    private static List<TreeMap<Integer, String>> collectDataRowsFromSheet(Sheet sheet) {
        List<TreeMap<Integer, String>> dataRows = new ArrayList<>();
        int maxRow = sheet.getPhysicalNumberOfRows();
        int limit = 5 + 300;
        if (maxRow > limit) {
            maxRow = limit;
        }
        for (int rowNum = 5; rowNum < maxRow; rowNum++) {
            Row row = sheet.getRow(rowNum);
            if (row == null) {
                continue;
            }
            TreeMap<Integer, String> cols = new TreeMap<>();
            for (int col = 0; col < row.getPhysicalNumberOfCells(); col++) {
                String value = getCellValue(row.getCell(col));
                if (value != null) {
                    cols.put(col, value);
                }
            }
            dataRows.add(cols);
        }
        return dataRows;
    }

    /**
     * 根据数组分析结果设置Title 的类型和注解
     * <p>
     * 该方法根据数组分析结果设置Title 的类型和注解。
     * <p>
     * 处理流程： 1. 根据数组分析结果设置Title 的类型和注解 2. 如果数组分析结果为二维数组，则设置二维数组类型 3. 如果数组分析结果为一维数组，则设置一维数组类型 4. 如果不是数组，保持 string 类型不变
     */
    private static void applyArrayTypeToTitle(Title title, ArrayValue arrayValue) {
        String annotationCode = "value = \"" + title.getOldName() + "\", isArray = true";

        if (arrayValue.arrayTwo) {
            // 二维数组
            setTwoDimensionalArrayType(title, arrayValue, annotationCode);
        } else if (arrayValue.array) {
            // 一维数组
            setOneDimensionalArrayType(title, arrayValue, annotationCode);
        }
        // 如果不是数组，保持 string 类型不变
    }

    /**
     * 设置二维数组类型
     * <p>
     * 该方法设置二维数组类型。
     * <p>
     * 处理流程： 1. 如果数组分析结果为纯整数，则设置 int[][] 类型 2. 如果数组分析结果为混合类型，则设置 string[][] 类型
     * 
     * @param title
     *            字段标题对象，用于设置转换后的类型和注解
     * @param arrayValue
     *            数组分析结果
     * @param annotationCode
     *            注解代码
     */
    private static void setTwoDimensionalArrayType(Title title, ArrayValue arrayValue, String annotationCode) {
        // 仅纯 int 二维数组可自动抬维；非 int 保持原 string，不再生成 string[][]
        if (Boolean.TRUE.equals(arrayValue.isIntArrayTwo)) {
            title.setNewCode(annotationCode);
            title.setType("int[][]");
        }
    }

    /**
     * 设置一维数组类型
     * <p>
     * 该方法设置一维数组类型。
     * <p>
     * 处理流程： 1. 如果数组分析结果为纯整数，则设置 int[] 类型 2. 如果数组分析结果为混合类型，则设置 string[] 类型
     * 
     * @param title
     *            字段标题对象，用于设置转换后的类型和注解
     * @param arrayValue
     *            数组分析结果
     * @param annotationCode
     *            注解代码
     */
    private static void setOneDimensionalArrayType(Title title, ArrayValue arrayValue, String annotationCode) {
        // 仅纯 int 一维数组可自动抬维；含非数字（如备注列逗号）保持 string
        if (Boolean.TRUE.equals(arrayValue.isIntArray)) {
            title.setNewCode(annotationCode);
            title.setType("int[]");
        }
    }

    /**
     * 从内存数据行分析数组结构（SAX / xls 共用，不含注释推断）。
     *
     * @param dataRows
     *            数据行（第 5 行起）
     * @param colIndex
     *            列索引
     * @return 数组分析结果
     */
    private static ArrayValue analyzeArrayFromDataRows(List<TreeMap<Integer, String>> dataRows, int colIndex) {
        ArrayValue arrayValue = new ArrayValue();
        if (dataRows == null || dataRows.isEmpty()) {
            return arrayValue;
        }

        int scannedDataRows = 0;
        for (TreeMap<Integer, String> row : dataRows) {
            if (row == null) {
                continue;
            }

            String cellValue = row.get(colIndex);
            if (cellValue == null || cellValue.trim().isEmpty() || "-1".equals(cellValue)) {
                continue;
            }

            scannedDataRows++;
            if (scannedDataRows > MAX_ARRAY_DATA_SCAN_ROWS) {
                break;
            }

            String[] values = extractValuesFromCell(cellValue, arrayValue);
            if (values == null || values.length <= 1) {
                continue;
            }

            analyzeArrayType(arrayValue, values);

            if (arrayValue.arrayTwo) {
                return arrayValue;
            }
        }
        return arrayValue;
    }

    /**
     * 从字段描述（第 2、4 行合并文本）推断数组维数。
     * <p>
     * 识别配置表固定话术：外层 {@code 用|分隔}、内层 {@code 用,分隔} 或 {@code 配置格式：} 字段列表；同时存在则判为二维。
     * 保底识别 {@code 字段,字段|字段,字段} 模板（如 {@code 属性id,值|属性id,值}），数据行仅有逗号时仍可抬升为二维。
     *
     * @param des
     *            合并后的字段描述
     * @return 注释侧数组分析结果
     */
    private static ArrayValue analyzeFromComment(String des) {
        ArrayValue result = new ArrayValue();
        if (des == null || des.trim().isEmpty()) {
            return result;
        }

        String normalized = normalizeCommentDes(des);
        boolean outerPipe = hasOuterPipeDelimiter(normalized);
        boolean innerComma = hasInnerCommaDelimiter(normalized);
        // 保底：识别「字段,字段|字段,字段」类模板（如 属性id,值|属性id,值），数据行无 | 时仍可抬升为二维
        boolean commaGroupPipe = hasCommaGroupPipePattern(normalized);

        if (outerPipe && innerComma) {
            result.arrayTwo = true;
            result.array = false;
            result.divide = "SPLIT_PIPE";
            result.isIntArrayTwo = true;
        } else if (commaGroupPipe) {
            result.arrayTwo = true;
            result.array = false;
            result.divide = "SPLIT_PIPE";
            result.isIntArrayTwo = true;
        } else if (outerPipe) {
            result.array = true;
            result.divide = "SPLIT_PIPE";
            result.isIntArray = true;
        } else if (innerComma) {
            result.array = true;
            result.divide = "SPLIT_COMMA";
            result.isIntArray = true;
        }
        return result;
    }

    /**
     * 合并数据推断与注释推断，取最大维数；注释可在无数据或数据维数较低时抬维。
     *
     * @param data
     *            数据行分析结果
     * @param comment
     *            注释分析结果
     * @return 合并后的数组分析结果
     */
    private static ArrayValue mergeArrayValue(ArrayValue data, ArrayValue comment) {
        if (data == null) {
            return comment != null ? comment : new ArrayValue();
        }
        if (comment == null) {
            return data;
        }

        ArrayValue merged = new ArrayValue();
        merged.arrayTwo = data.arrayTwo || comment.arrayTwo;
        merged.array = merged.arrayTwo || data.array || comment.array;

        if (merged.arrayTwo) {
            merged.divide = "SPLIT_PIPE";
            if (Boolean.TRUE.equals(data.isIntArrayTwo) || Boolean.TRUE.equals(comment.isIntArrayTwo)) {
                merged.isIntArrayTwo = true;
            } else if (comment.arrayTwo) {
                merged.isIntArrayTwo = true;
            }
        } else if (merged.array) {
            if ("SPLIT_PIPE".equals(comment.divide)) {
                merged.divide = "SPLIT_PIPE";
            } else if ("SPLIT_PIPE".equals(data.divide)) {
                merged.divide = "SPLIT_PIPE";
            } else if (comment.divide != null) {
                merged.divide = comment.divide;
            } else {
                merged.divide = data.divide;
            }
            if (Boolean.TRUE.equals(data.isIntArray) || Boolean.TRUE.equals(comment.isIntArray)) {
                merged.isIntArray = true;
            } else if (comment.array) {
                merged.isIntArray = true;
            }
        }
        return merged;
    }

    /**
     * 规范化注释文本：中文逗号转英文逗号，便于分隔符话术匹配。
     *
     * @param des
     *            原始描述
     * @return 规范化后的描述
     */
    private static String normalizeCommentDes(String des) {
        if (des == null) {
            return "";
        }
        return des.replace('，', ',');
    }

    /**
     * 注释是否声明外层使用 {@code |} 分隔。
     *
     * @param normalizedDes
     *            已规范化的描述
     * @return 是否外层 {@code |} 分隔
     */
    private static boolean hasOuterPipeDelimiter(String normalizedDes) {
        return normalizedDes.contains("用|分隔") || normalizedDes.contains("用|分割");
    }

    /**
     * 注释是否声明内层使用 {@code ,} 分隔。
     *
     * @param normalizedDes
     *            已规范化的描述
     * @return 是否内层 {@code ,} 分隔
     */
    private static boolean hasInnerCommaDelimiter(String normalizedDes) {
        if (normalizedDes.contains("用,分隔") || normalizedDes.contains("用,分割")) {
            return true;
        }

        int formatIdx = normalizedDes.indexOf("配置格式");
        if (formatIdx < 0) {
            return false;
        }

        String formatPart = normalizedDes.substring(formatIdx);
        int multiIdx = formatPart.indexOf("多个");
        if (multiIdx > 0) {
            formatPart = formatPart.substring(0, multiIdx);
        }
        int commaCount = 0;
        for (int i = 0; i < formatPart.length(); i++) {
            if (formatPart.charAt(i) == ',') {
                commaCount++;
            }
        }
        return commaCount >= 2;
    }

    /**
     * 注释是否含「组内逗号、组间竖线」的字段模板。
     * <p>
     * 识别配置表常见写法：{@code 属性id,值|属性id,值}、{@code 投放类型,道具id,数量|投放类型,道具id,数量}。
     * 要求按 {@code |} 拆分后至少两段且每段均含 {@code ,}，以排除 {@code 1|1|1|1} 这类一维竖线数组说明。
     *
     * @param normalizedDes
     *            已规范化的描述
     * @return 是否为组内逗号、组间竖线的二维模板
     */
    private static boolean hasCommaGroupPipePattern(String normalizedDes) {
        if (normalizedDes == null || normalizedDes.indexOf('|') < 0) {
            return false;
        }
        String[] segments = normalizedDes.split("\\|", -1);
        if (segments.length < 2) {
            return false;
        }
        int commaSegmentCount = 0;
        for (String s : segments) {
            String segment = s.trim();
            if (segment.isEmpty()) {
                continue;
            }
            if (segment.indexOf(',') >= 0) {
                commaSegmentCount++;
            }
        }
        return commaSegmentCount >= 2;
    }

    /**
     * 从单元格值中提取分割后的字符串数组 优先使用 | 拆分，然后使用 , 拆分
     * <p>
     * 该方法从单元格值中提取分割后的字符串数组。
     * <p>
     * 处理流程： 1. 如果单元格值为空，返回 null 2. 如果单元格值包含 |，则使用 | 拆分 3. 如果单元格值包含 ,，则使用 , 拆分 4. 返回拆分后的字符串数组
     * 
     * @param cellValue
     *            单元格值
     * @param arrayValue
     *            数组值
     * @return 拆分后的字符串数组
     */
    private static String[] extractValuesFromCell(String cellValue, ArrayValue arrayValue) {
        if (cellValue == null || cellValue.trim().isEmpty()) {
            return null;
        }

        // 优先使用 | 拆分
        if (cellValue.contains("|")) {
            arrayValue.divide = "SPLIT_PIPE";
            return cellValue.split("\\|");
        }
        // 再看能不能用 , 拆分
        else if (cellValue.contains(",")) {
            arrayValue.divide = "SPLIT_COMMA";
            return cellValue.split(",");
        }

        return null;
    }

    /**
     * 分析数组类型并更新ArrayValue对象 检查是否是 int 只要有一行数据支持最大维数数组拆分就按最大拆分
     * <p>
     * 该方法分析数组类型并更新ArrayValue对象。
     * <p>
     * 处理流程： 1. 根据分析结果设置ArrayValue（取最大维数） 2. 如果分析结果为二维数组，则设置二维数组类型 3. 如果分析结果为一维数组，则设置一维数组类型
     *
     * @param arrayValue 数组值
     * @param values     字符串数组
     */
    private static void analyzeArrayType(ArrayValue arrayValue, String[] values) {
        ArrayTypeAnalysisResult result = analyzeArrayValues(arrayValue, values);

        // 根据分析结果设置ArrayValue（取最大维数）
        if (result.hasTwoDimensionalStructure) {
            // 二维数组
            arrayValue.arrayTwo = true;
            arrayValue.array = false;
            // 类型判断在 analyzeTwoDimensionalType 中已经设置
        } else if (values.length > 1) {
            // 一维数组
            arrayValue.array = true;
            setOneDimensionalArrayTypeFlags(arrayValue, result.allInt);
        }
    }

    /**
     * 分析数组值，返回分析结果
     * <p>
     * 该方法分析数组值，返回分析结果。
     * <p>
     * 处理流程： 1. 检查是否是二维数组 2. 检查是否是一维数组 3. 返回分析结果
     *
     * @param arrayValue 数组值
     * @param values     字符串数组
     * @return 分析结果
     */
    private static ArrayTypeAnalysisResult analyzeArrayValues(ArrayValue arrayValue, String[] values) {
        boolean hasTwoDimensionalStructure = false;
        boolean allInt = true;

        for (String value : values) {
            value = value.trim();
            if (value.isEmpty()) {
                continue;
            }

            ValueAnalysisResult valueResult = analyzeSingleValue(value, arrayValue.divide);
            if (valueResult.isTwoDimensional) {
                hasTwoDimensionalStructure = true;
                analyzeTwoDimensionalType(arrayValue, valueResult.subValues);
                allInt = false;
            } else if (valueResult.isNumeric) {
                // 一维数组的值，检查是否是 int
                if (TypeAnalyzer.isNotInteger(value)) {
                    allInt = false;
                }
            } else {
                allInt = false;
            }
        }

        return new ArrayTypeAnalysisResult(hasTwoDimensionalStructure, allInt);
    }

    /**
     * 分析单个值
     * <p>
     * 该方法分析单个值。
     * <p>
     * 处理流程： 1. 检查是否是描述行 2. 检查是否是一维数组 3. 返回分析结果
     *
     * @param value         值
     * @param currentDivide 当前拆分符
     * @return 分析结果
     */
    private static ValueAnalysisResult analyzeSingleValue(String value, String currentDivide) {
        return TypeAnalyzer.analyzeValue(value, currentDivide);
    }

    /**
     * 设置一维数组类型标志
     * <p>
     * 该方法设置一维数组类型标志。
     * <p>
     * 处理流程： 1. 如果当前行全部是整数，设置为 int[] 2. 如果当前行不是全部数字，不改变之前的状态
     * 
     * @param arrayValue
     *            数组值
     * @param allInt
     *            是否是全部整数
     */
    private static void setOneDimensionalArrayTypeFlags(ArrayValue arrayValue, boolean allInt) {
        if (allInt) {
            // 如果当前行全部是整数，设置为 int[]
            if (arrayValue.isIntArray == null) {
                arrayValue.isIntArray = true;
            }
        }
        // 如果当前行不是全部数字，不改变之前的状态
    }

    /**
     * 分析二维数组的子值类型，检查是否是 int
     * <p>
     * 该方法分析二维数组的子值类型，检查是否是 int。
     * <p>
     * 处理流程： 1. 检查是否是 int 2. 设置二维数组类型
     *
     * @param arrayValue 数组值
     * @param subValues  子值数组
     */
    private static void analyzeTwoDimensionalType(ArrayValue arrayValue, String[] subValues) {
        boolean allInt = true;

        for (String subValue : subValues) {
            subValue = subValue.trim();
            if (subValue.isEmpty()) {
                continue;
            }

            // 检查是否是 int
            if (TypeAnalyzer.isNotInteger(subValue)) {
                allInt = false;
                break;
            }
        }

        // 设置二维数组类型
        if (allInt) {
            // 如果当前行全部是整数，设置为 int[][]
            if (arrayValue.isIntArrayTwo == null) {
                arrayValue.isIntArrayTwo = true;
            }
        }
    }

    /**
     * 获取常量值
     * <p>
     * 该方法获取常量值。
     * <p>
     * 处理流程： 1. 获取常量值 2. 返回常量值
     * 
     * @param sheet
     *            Excel工作簿
     * @return 常量值
     */
    private static List<ConstType> getConstValue(Sheet sheet) {
        List<ConstType> titleList = new ArrayList<>();

        for (int rowIndex = 5; rowIndex < sheet.getPhysicalNumberOfRows(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row != null) {
                String name = getCellValue(row.getCell(1));
                if (name == null || name.trim().isEmpty()) {
                    continue;
                }

                String value = getCellValue(row.getCell(0));
                String des = getCellValue(row.getCell(2));

                int id;
                try {
                    id = Integer.parseInt(value);
                } catch (Exception e) {
                    try {
                        id = (int)Float.parseFloat(value);
                    } catch (Exception f) {
                        System.out.println("无法解析ID: " + value);
                        ctx.logMessage("无法解析ID: " + value);
                        continue;
                    }
                }

                titleList.add(new ConstType(id, des, name.toUpperCase()));
            }
        }
        return titleList;
    }

    /**
     * 处理Sheet生成枚举类
     * <p>
     * 该方法处理Sheet生成枚举类。
     * <p>
     * 处理流程： 1. 获取常量值 2. 获取Java名称 3. 获取包路径 4. 生成枚举类代码 5. 写入文件
     */
    public static void processSheetForConst(Sheet sheet, String serverPath, String javaName) {
        List<ConstType> titleList = getConstValue(sheet);

        String finalJavaName = getJavaNameConst(sheet.getSheetName(), javaName);
        String packages;
        String basePath = serverPath + "/common/src/";

        // if (javaName.equals(MESSAGE)) {
        // finalJavaName = "ActionResult";
        // packages = "com/gow/common/net/action";
        // } else {
        packages = "com/gow/common/config/constant";
        // }

        try {
            doWrite(finalJavaName, basePath + packages, generateEnumCode(finalJavaName, titleList, packages));
            System.out.println(finalJavaName + ".java 文件已生成。");
            ctx.logMessage(finalJavaName + ".java 文件已生成。");
        } catch (Exception e) {
            System.out.println("处理Sheet生成配置模型类失败: " + javaName + " error: " + e);
            ctx.logMessage("处理Sheet生成配置模型类失败: " + javaName + " error: " + e);
        }
    }

    // ================ 文件操作 ================
    /**
     * 写入Java文件
     * <p>
     * 该方法写入Java文件。
     * <p>
     * 处理流程： 1. 写入Java文件 2. 返回写入后的文件路径
     * 
     * @param javaName
     *            Java名称
     * @param path
     *            文件路径
     * @param content
     *            文件内容
     */
    private static void doWrite(String javaName, String path, String content) throws IOException {
        String fileName = javaName + ".java";
        Path directoryPath = Paths.get(path);

        // 创建目录（如果不存在）
        if (!Files.exists(directoryPath)) {
            Files.createDirectories(directoryPath);
            System.out.println("目录已创建: " + path);
            ctx.logMessage("目录已创建: " + path);
        }

        // 构建完整文件路径
        Path filePath = directoryPath.resolve(fileName);

        // 如果文件已存在，先删除
        if (Files.exists(filePath)) {
            Files.delete(filePath);
            System.out.println("已删除原有文件: " + filePath);
            ctx.logMessage("已删除原有文件: " + filePath);
        }

        // 写入文件
        Files.write(filePath, content.getBytes(StandardCharsets.UTF_8));
        System.out.println("文件写入成功: " + filePath);
        ctx.logMessage("文件写入成功: " + filePath);
    }

    /**
     * 写入数据模型类
     * <p>
     * 该方法写入数据模型类。
     * <p>
     * 处理流程： 1. 写入数据模型类 2. 返回写入后的字符串
     * 
     * @param javaName
     *            Java名称
     * @param titleList
     *            标题列表
     * @param filePath
     *            文件路径
     * @return 写入后的字符串
     */
    public static String genJavaModel(String javaName, List<Title> titleList, String filePath) {
        return CodeRenderer.renderModel(javaName, titleList, filePath, getAuthor());
    }

    // ================ 代码生成辅助方法 ================
    /**
     * 生成枚举类代码
     * <p>
     * 该方法生成枚举类代码。
     * <p>
     * 处理流程： 1. 生成枚举类代码 2. 返回生成后的字符串
     * 
     * @param className
     *            类名称
     * @param enumItems
     *            枚举项列表
     * @param packages
     *            包路径
     * @return 生成后的字符串
     */
    public static String generateEnumCode(String className, List<ConstType> enumItems, String packages) {
        return CodeRenderer.renderEnum(className, enumItems, packages, getAuthor());
    }

    /**
     * 获取文件扩展名
     * <p>
     * 该方法获取文件扩展名。
     * <p>
     * 处理流程： 1. 获取文件扩展名 2. 返回获取后的字符串
     * 
     * @param fileName
     *            文件名
     * @return 文件扩展名
     */
    private static String getFileExtension(String fileName) {
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex == -1) {
            return "";
        }
        return fileName.substring(lastDotIndex + 1).toLowerCase();
    }

    /**
     * 获取单元格的值
     * <p>
     * 该方法获取单元格的值。
     * <p>
     * 处理流程： 1. 获取单元格的值 2. 返回获取后的字符串
     * 
     * @param cell
     *            单元格
     * @return 单元格的值
     */
    private static String getCellValue(Object cell) {
        if (cell == null) {
            return "";
        }

        CellType cellType;

        if (cell instanceof XSSFCell) {
            XSSFCell xssfCell = (XSSFCell)cell;
            cellType = xssfCell.getCellType();
        } else if (cell instanceof HSSFCell) {
            HSSFCell hssfCell = (HSSFCell)cell;
            cellType = hssfCell.getCellType();
        } else {
            return "";
        }

        return convertCellValue(cellType, cell);
    }

    /**
     * 根据单元格类型转换值
     * <p>
     * 该方法根据单元格类型转换值。
     * <p>
     * 处理流程： 1. 根据单元格类型转换值 2. 返回转换后的字符串
     * 
     * @param cellType
     *            单元格类型
     * @param cell
     *            单元格
     * @return 转换后的字符串
     */
    private static String convertCellValue(CellType cellType, Object cell) {
        switch (cellType) {
            case NUMERIC:
                return String.valueOf(getNumericValue(cell));
            case BOOLEAN:
                return String.valueOf(getBooleanValue(cell));
            default:
                return getStringValue(cell);
        }
    }

    /**
     * 获取数值类型的值
     * <p>
     * 该方法获取数值类型的值。
     * <p>
     * 处理流程： 1. 获取数值类型的值 2. 返回获取后的值
     * 
     * @param cell
     *            单元格
     * @return 数值类型的值
     */
    private static double getNumericValue(Object cell) {
        if (cell instanceof XSSFCell)
            return ((XSSFCell)cell).getNumericCellValue();
        if (cell instanceof HSSFCell)
            return ((HSSFCell)cell).getNumericCellValue();
        return 0;
    }

    /**
     * 获取布尔类型的值
     * <p>
     * 该方法获取布尔类型的值。
     * <p>
     * 处理流程： 1. 获取布尔类型的值 2. 返回获取后的值
     * 
     * @param cell
     *            单元格
     * @return 布尔类型的值
     */
    private static boolean getBooleanValue(Object cell) {
        if (cell instanceof XSSFCell)
            return ((XSSFCell)cell).getBooleanCellValue();
        if (cell instanceof HSSFCell)
            return ((HSSFCell)cell).getBooleanCellValue();
        return false;
    }

    /**
     * 获取字符串类型的值
     * <p>
     * 该方法获取字符串类型的值。
     * <p>
     * 处理流程： 1. 获取字符串类型的值 2. 返回获取后的值
     * 
     * @param cell
     *            单元格
     * @return 字符串类型的值
     */
    private static String getStringValue(Object cell) {
        if (cell instanceof XSSFCell)
            return ((XSSFCell)cell).getStringCellValue();
        if (cell instanceof HSSFCell)
            return ((HSSFCell)cell).getStringCellValue();
        return "";
    }

    // ================ 字符串处理 ================
    /**
     * 获取模型类名
     * <p>
     * 该方法获取模型类名。
     * <p>
     * 处理流程： 1. 获取模型类名 2. 返回获取后的字符串
     * 
     * @param sheetName
     *            表名
     * @param javaName
     *            类名
     * @return 模型类名
     */
    private static String getJavaName(String sheetName, String javaName) {
        // 如果名称包含Config 则不添加Config
        if (sheetName.toLowerCase().contains("config")) {
            return JavaNames.upperFirst(sheetName);
        }
        if (!sheetName.toLowerCase().contains("sheet")) {
            return JavaNames.upperFirst(sheetName) + "Config";
        }
        if (javaName.contains("config")) {
            return JavaNames.upperFirst(sheetName);
        }
        return javaName + "Config";
    }

    /**
     * 获取枚举类名
     * <p>
     * 该方法获取枚举类名。
     * <p>
     * 处理流程： 1. 获取枚举类名 2. 返回获取后的字符串
     * 
     * @param sheetName
     *            表名
     * @param javaName
     *            类名
     * @return 枚举类名
     */
    private static String getJavaNameConst(String sheetName, String javaName) {
        if (!sheetName.toLowerCase().contains("sheet")) {
            return JavaNames.camelCase(sheetName, true) + "Enum";
        }
        return javaName + "Enum";
    }

    /**
     * 统一弹框 + 批量生成 Manager + 批量更新配置
     * <p>
     * 该方法统一弹框 + 批量生成 Manager + 批量更新配置。
     * <p>
     * 处理流程： 1. 统一弹框 + 批量生成 Manager + 批量更新配置 2. 返回处理后的结果
     * 
     * @param serverPath
     *            服务器路径
     * @param pendingManagers
     *            待处理的管理器列表
     */
    private static void flushPendingManagers(String serverPath, List<ManagerGenInfo> pendingManagers) {
        if (pendingManagers == null || pendingManagers.isEmpty()) {
            return;
        }
        // 按 ConfigClassName 去重（保留第一个）
        Map<String, ManagerGenInfo> uniqueMap = new LinkedHashMap<>();
        for (ManagerGenInfo info : pendingManagers) {
            uniqueMap.putIfAbsent(info.getSheetName(), info);
        }
        pendingManagers = new ArrayList<>(uniqueMap.values());
        // 只对“配置模型类”做批量（不处理枚举类生成）
        // 1) 先显示 Manager 选择对话框，让用户选择哪些需要生成
        List<ManagerSelectionItem> selectedItems = ModelGenUiDialogs.showManagerSelectionDialog(ctx, pendingManagers);
        if (selectedItems == null) {
            return;
        }
        // 一次遍历统计生成与注册选择，避免 Stream 影响热更新兼容。
        boolean hasAnySelection = false;
        boolean needAddToConfig = false;
        int configCount = 0;
        for (ManagerSelectionItem selectedItem : selectedItems) {
            if (selectedItem.isGenerateManager() || selectedItem.isAddToConfig()) {
                hasAnySelection = true;
            }
            if (selectedItem.isAddToConfig()) {
                needAddToConfig = true;
                configCount++;
            }
        }
        if (!hasAnySelection) {
            ctx.logMessage("未选择任何 Manager 生成或配置，跳过处理");
            return;
        }
        // 2) 显示服务器配置选择对话框（只有需要添加配置时才显示）
        ServerConfigSelection serverSelection = null;
        if (needAddToConfig) {
            serverSelection = ModelGenUiDialogs.showServerConfigDialog(ctx, String.valueOf(configCount));
            if (serverSelection == null) {
                return;
            }
        }
        // 3) 批量生成选中的 Manager.java（先写 common）
        List<RegistrationWriter.Definition> registrationDefs = new ArrayList<>();
        for (ManagerSelectionItem item : selectedItems) {
            ManagerGenInfo info = item.getInfo();
            String managerClassName = info.getConfigClassName() + "Manager";
            // 生成 Manager 类
            if (item.isGenerateManager()) {
                try {
                    Title idTitle = findIdTitle(info.getTitleList());
                    String managerCode =
                        genManagerModel(managerClassName, info.getConfigClassName(), idTitle, info.getPackagePath());
                    String replace = DATA_PACKAGE_PREFIX.replace(".", "/");
                    String outPath = serverPath + "/common/src/" + replace + info.getPackagePath();
                    doWrite(managerClassName, outPath, managerCode);
                } catch (Exception e) {
                    ctx.logMessage("生成 Manager 类失败: " + managerClassName + " error: " + e);
                    System.out.println("生成 Manager 类失败: " + managerClassName + " error: " + e);
                }
            }
            // 收集需要添加到配置的项
            if (item.isAddToConfig()) {
                registrationDefs.add(new RegistrationWriter.Definition(managerClassName,
                    info.getConfigClassName(), info.getSheetName(), info.getPackagePath()));
            }
        }
        // 4) 批量更新 gameserver/worldserver 的 config.xml（去重）
        if (serverSelection != null && !registrationDefs.isEmpty()) {
            if (serverSelection.isGameServer()) {
                RegistrationWriter.write(serverPath + "/gameserver/src/config.xml",
                    serverPath + "/gameserver/src/com/gow/gameserver/util/BeanManager.java", registrationDefs, ctx);
            }
            if (serverSelection.isWorldServer()) {
                RegistrationWriter.write(serverPath + "/worldserver/src/config.xml",
                    serverPath + "/worldserver/src/com/gow/worldserver/util/BeanManager.java", registrationDefs, ctx);
            }
        }
    }

    /**
     * 查找用于 ID 的属性
     * <p>
     * 该方法查找用于 ID 的属性。
     * <p>
     * 处理流程： 1. 查找用于 ID 的属性 2. 返回查找后的属性
     * 
     * @param titleList
     *            标题列表
     * @return 查找后的属性
     */
    private static Title findIdTitle(List<Title> titleList) {
        // 首先查找原始类型是 vindex 的字段
        for (Title title : titleList) {
            if (title.isOriginalVindex()) {
                return title;
            }
        }
        // 如果没有 vindex，查找第一个 int 类型的字段，且名称包含 id 或 Id
        for (Title title : titleList) {
            if ("int".equals(title.getType())) {
                String name = title.getName().toLowerCase();
                if (name.contains("id")) {
                    return title;
                }
            }
        }
        // 如果还没有，返回第一个 int 类型字段
        for (Title title : titleList) {
            if ("int".equals(title.getType())) {
                return title;
            }
        }
        // 如果都没有，返回第一个字段
        return titleList.isEmpty() ? null : titleList.get(0);
    }

    /**
     * 生成 Manager 类代码
     * <p>
     * 该方法生成 Manager 类代码。
     * <p>
     * 处理流程： 1. 生成 Manager 类代码 2. 返回生成后的代码
     * 
     * @param managerClassName
     *            管理器类名
     * @param configClassName
     *            配置类名
     * @param idTitle
     *            用于 ID 的属性
     * @param packagePath
     *            包路径
     * @return 生成后的代码
     */
    private static String genManagerModel(String managerClassName, String configClassName, Title idTitle,
        String packagePath) {
        StringBuilder sb = new StringBuilder();

        // 包声明
        String packageName = DATA_PACKAGE_PREFIX + packagePath;
        sb.append("package ").append(packageName).append(";\n\n");

        // 导入
        sb.append("import com.gow.common.config.BaseGenericDataManager;\n\n");

        // 类注释（空行用 "*\n"，与 Config 模板一致，避免 "* " 尾空格）
        sb.append("/**\n");
        sb.append(" * ").append(configClassName).append("配置数据管理器\n");
        sb.append(" *\n");
        sb.append(" * @author ").append(getAuthor()).append("\n");
        sb.append(" */\n");

        // 类声明（与 Config 模板一致：类体前空一行）
        sb.append("public class ").append(managerClassName).append(" extends BaseGenericDataManager<")
            .append(configClassName).append("> {\n\n");

        // getId 方法
        sb.append("    @Override\n");
        sb.append("    protected int getId(").append(configClassName).append(" data) {\n");
        if (idTitle != null) {
            String getterMethod = "get" + JavaNames.upperFirst(idTitle.getName()) + "()";
            sb.append("        return data.").append(getterMethod).append(";\n");
        } else {
            sb.append("        return 0;\n");
        }
        sb.append("    }\n\n");

        sb.append("}\n");

        return sb.toString();
    }

    /**
     * 获取代码生成的作者信息。
     * <p>
     * 优先级： 1) JVM 参数覆盖：-Dgen.author=xxx 2) 当前登录用户名：Windows 环境变量 USERNAME；再兜底 user.name 3) 机器名兜底：Windows 环境变量
     * COMPUTERNAME
     */
    private static String getAuthor() {
        // 1) 允许外部覆盖（例如 CI 或共享机器）
        String author = System.getProperty("gen.author");
        if (author != null) {
            author = author.trim();
            if (!author.isEmpty()) {
                return author;
            }
        }

        // 2) 优先 Windows 用户名
        author = System.getenv("USERNAME");
        if (author != null) {
            author = author.trim();
            if (!author.isEmpty()) {
                return author;
            }
        }

        // 3) 通用兜底：JVM 用户名
        author = System.getProperty("user.name");
        if (author != null) {
            author = author.trim();
            if (!author.isEmpty()) {
                return author;
            }
        }

        // 4) 最后兜底：机器名
        String machine = System.getenv("COMPUTERNAME");
        if (machine != null) {
            machine = machine.trim();
            if (!machine.isEmpty()) {
                return machine;
            }
        }

        return "unknown";
    }
}
