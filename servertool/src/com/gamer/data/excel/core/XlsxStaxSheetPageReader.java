package com.gamer.data.excel.core;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.eventusermodel.ReadOnlySharedStringsTable;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.model.StylesTable;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;

import com.gamer.data.gdg.excel.XlsxOpcSession;
import com.gamer.data.gdg.excel.XlsxStyles;
import com.gamer.data.gdg.util.GdDataFormatter;

/**
 * xlsx StAX 分页读取：跳过非目标行整段 XML 子树，深页翻页不必解析中间行单元格。
 * <p>
 * 使用方：{@link SaxPagedExcelSource} Excel 预览深页优化。
 */
public final class XlsxStaxSheetPageReader {

    /** 表头行数，数据区从第 5 行（0-based）起 */
    private static final int HEADER_ROWS = 5;

    /** 同一 xlsx 并发读时串行化 */
    private static final Map<String, Object> READ_LOCKS = new ConcurrentHashMap<String, Object>();

    /**
     * 禁止实例化。
     */
    private XlsxStaxSheetPageReader() {}

    /**
     * 按数据行偏移读取一页（含 hasNext 探测，读完即停不扫尾页）。
     *
     * @param file
     *            xlsx 文件
     * @param sheetName
     *            Sheet 名
     * @param startDataIndex
     *            数据区起始行索引（0-based，已跳过表头）
     * @param pageSize
     *            每页行数
     * @return 分页结果
     * @throws Exception
     *             读取失败
     */
    public static PagedTablePage readPage(File file, String sheetName, int startDataIndex, int pageSize)
        throws Exception {
        return readPage(file, sheetName, startDataIndex, pageSize, null);
    }

    /**
     * 按数据行偏移读取一页。
     *
     * @param file
     *            xlsx 文件
     * @param sheetName
     *            Sheet 名
     * @param startDataIndex
     *            数据区起始行索引（0-based，已跳过表头）
     * @param pageSize
     *            每页行数
     * @param styleWarn
     *            样式读取失败回调，可为 null
     * @return 分页结果
     * @throws Exception
     *             读取失败
     */
    public static PagedTablePage readPage(File file, String sheetName, int startDataIndex, int pageSize,
        XlsxStyles.Warn styleWarn) throws Exception {
        synchronized (readLockFor(file)) {
            return readPageUnlocked(file, sheetName, startDataIndex, pageSize, styleWarn);
        }
    }

    /**
     * 在文件锁内执行 StAX 分页读。
     */
    private static PagedTablePage readPageUnlocked(File file, String sheetName, int startDataIndex, int pageSize,
        XlsxStyles.Warn styleWarn) throws Exception {
        XlsxOpcSession session = XlsxOpcSession.open(file);
        InputStream sheetStream = null;
        XMLEventReader eventReader = null;
        try {
            ReadOnlySharedStringsTable sst = new ReadOnlySharedStringsTable(session.getPackage());
            XSSFReader xssfReader = session.getReader();
            StylesTable styles = XlsxStyles.read(xssfReader, styleWarn);
            sheetStream = openSheetStream(xssfReader, sheetName);
            if (sheetStream == null) {
                throw new IllegalArgumentException("找不到sheet: " + sheetName);
            }

            XMLInputFactory factory = XMLInputFactory.newInstance();
            factory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, Boolean.TRUE);
            eventReader = factory.createXMLEventReader(sheetStream);

            GdDataFormatter formatter = new GdDataFormatter();
            List<Object[]> pageRows = new ArrayList<Object[]>();
            int dataIndex = 0;
            boolean hasNext = false;

            while (eventReader.hasNext()) {
                XMLEvent event = eventReader.nextEvent();
                if (!event.isStartElement()) {
                    continue;
                }
                StartElement start = event.asStartElement();
                if (!"row".equals(localName(start))) {
                    continue;
                }

                int rowNum0 = parseRowNum0(start);
                if (rowNum0 < HEADER_ROWS) {
                    skipSubtree(eventReader, 1);
                    continue;
                }

                int currentDataIndex = dataIndex;
                dataIndex++;

                if (currentDataIndex < startDataIndex) {
                    skipSubtree(eventReader, 1);
                    continue;
                }
                if (pageRows.size() >= pageSize) {
                    hasNext = true;
                    break;
                }

                TreeMap<Integer, String> colValues = parseRowCells(eventReader, sst, formatter, styles);
                pageRows.add(toRowArray(colValues));
            }

            return new PagedTablePage(pageRows, PagedTablePage.UNKNOWN_TOTAL_ROWS, hasNext);
        } finally {
            closeQuietly(eventReader);
            closeQuietly(sheetStream);
            session.close();
        }
    }

    /**
     * 解析 row 的 r 属性为 0-based 行号。
     */
    private static int parseRowNum0(StartElement rowStart) {
        String rowAttr = getAttribute(rowStart, "r");
        if (rowAttr == null || rowAttr.trim().isEmpty()) {
            return HEADER_ROWS;
        }
        try {
            return Integer.parseInt(rowAttr.trim()) - 1;
        } catch (NumberFormatException ex) {
            return HEADER_ROWS;
        }
    }

    /**
     * 跳过当前元素整段子树（调用时已消费 START）。
     *
     * @param reader
     *            事件流
     * @param depth
     *            当前嵌套深度
     */
    private static void skipSubtree(XMLEventReader reader, int depth) throws Exception {
        while (depth > 0 && reader.hasNext()) {
            XMLEvent event = reader.nextEvent();
            if (event.isStartElement()) {
                depth++;
            } else if (event.isEndElement()) {
                depth--;
            }
        }
    }

    /**
     * 解析一行单元格（row START 之后调用）。
     */
    private static TreeMap<Integer, String> parseRowCells(XMLEventReader reader, ReadOnlySharedStringsTable sst,
        GdDataFormatter formatter, StylesTable styles) throws Exception {
        TreeMap<Integer, String> colValues = new TreeMap<Integer, String>();
        while (reader.hasNext()) {
            XMLEvent event = reader.nextEvent();
            if (event.isStartElement()) {
                StartElement start = event.asStartElement();
                if ("c".equals(localName(start))) {
                    parseOneCell(start, reader, colValues, sst, formatter, styles);
                }
            } else if (event.isEndElement()) {
                if ("row".equals(localName(event.asEndElement()))) {
                    break;
                }
            }
        }
        return colValues;
    }

    /**
     * 解析单个 &lt;c&gt; 单元格。
     */
    private static void parseOneCell(StartElement cellStart, XMLEventReader reader, TreeMap<Integer, String> colValues,
        ReadOnlySharedStringsTable sst, GdDataFormatter formatter, StylesTable styles) throws Exception {
        String cellRef = getAttribute(cellStart, "r");
        if (cellRef == null || cellRef.trim().isEmpty()) {
            skipSubtree(reader, 1);
            return;
        }
        int col = new CellReference(cellRef).getCol();
        String cellType = getAttribute(cellStart, "t");
        String styleAttr = getAttribute(cellStart, "s");
        int styleIndex = parseIntSafe(styleAttr, -1);

        String valueText = null;
        String inlineText = null;
        boolean inV = false;
        boolean inT = false;
        StringBuilder buf = new StringBuilder();
        int depth = 1;

        while (depth > 0 && reader.hasNext()) {
            XMLEvent event = reader.nextEvent();
            if (event.isStartElement()) {
                depth++;
                String name = localName(event.asStartElement());
                if ("v".equals(name)) {
                    inV = true;
                    buf.setLength(0);
                } else if ("t".equals(name)) {
                    inT = true;
                    buf.setLength(0);
                }
            } else if (event.isCharacters()) {
                if (inV || inT) {
                    buf.append(event.asCharacters().getData());
                }
            } else if (event.isEndElement()) {
                EndElement end = event.asEndElement();
                String name = localName(end);
                if ("v".equals(name)) {
                    valueText = buf.toString();
                    inV = false;
                } else if ("t".equals(name) && inT) {
                    inlineText = buf.toString();
                    inT = false;
                }
                depth--;
            }
        }

        String formatted = formatCellValue(valueText, inlineText, cellType, styleIndex, sst, formatter, styles);
        if (formatted != null && !formatted.startsWith("=")) {
            colValues.put(Integer.valueOf(col), formatted.trim());
        }
    }

    /**
     * 将原始单元格值格式化为展示字符串。
     */
    private static String formatCellValue(String valueText, String inlineText, String cellType, int styleIndex,
        ReadOnlySharedStringsTable sst, DataFormatter formatter, StylesTable styles) {
        if ("inlineStr".equals(cellType) || (inlineText != null && !inlineText.isEmpty())) {
            return inlineText == null ? "" : inlineText;
        }
        if (valueText == null) {
            return "";
        }
        if ("s".equals(cellType)) {
            try {
                int idx = Integer.parseInt(valueText.trim());
                return sst.getEntryAt(idx);
            } catch (Exception ex) {
                return valueText;
            }
        }
        if ("b".equals(cellType)) {
            if ("1".equals(valueText) || "true".equalsIgnoreCase(valueText)) {
                return "Y";
            }
            if ("0".equals(valueText) || "false".equalsIgnoreCase(valueText)) {
                return "N";
            }
            return valueText;
        }
        if ("str".equals(cellType)) {
            return valueText;
        }
        try {
            double numeric = Double.parseDouble(valueText);
            if (styleIndex >= 0 && styles != null && formatter != null) {
                XSSFCellStyle style = styles.getStyleAt(styleIndex);
                if (style != null) {
                    String formatString = style.getDataFormatString();
                    short formatIndex = style.getDataFormat();
                    if (DateUtil.isADateFormat(formatIndex, formatString) && isWholeNumber(numeric)) {
                        return String.valueOf((long)numeric);
                    }
                    return formatter.formatRawCellContents(numeric, formatIndex, formatString);
                }
            }
            if (isWholeNumber(numeric)) {
                return String.valueOf((long)numeric);
            }
            return valueText;
        } catch (NumberFormatException ex) {
            return valueText;
        }
    }

    /**
     * 是否整型浮点值。
     */
    private static boolean isWholeNumber(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value) && value == Math.floor(value);
    }

    /**
     * 列 Map 转 Object[]。
     */
    private static Object[] toRowArray(TreeMap<Integer, String> colValues) {
        if (colValues == null || colValues.isEmpty()) {
            return new Object[0];
        }
        int max = colValues.lastKey().intValue();
        Object[] row = new Object[max + 1];
        for (int c = 0; c <= max; c++) {
            row[c] = colValues.get(Integer.valueOf(c));
        }
        return row;
    }

    /**
     * 取元素本地名。
     */
    private static String localName(StartElement element) {
        if (element == null) {
            return "";
        }
        QName name = element.getName();
        if (name == null) {
            return "";
        }
        String local = name.getLocalPart();
        return local == null ? "" : local;
    }

    /**
     * 取元素本地名。
     */
    private static String localName(EndElement element) {
        if (element == null) {
            return "";
        }
        QName name = element.getName();
        if (name == null) {
            return "";
        }
        String local = name.getLocalPart();
        return local == null ? "" : local;
    }

    /**
     * 取属性值。
     */
    private static String getAttribute(StartElement element, String attrName) {
        if (element == null || attrName == null) {
            return null;
        }
        javax.xml.stream.events.Attribute attr = (javax.xml.stream.events.Attribute)element.getAttributeByName(
            new QName(attrName));
        if (attr == null) {
            return null;
        }
        return attr.getValue();
    }

    /**
     * 安全解析整数。
     */
    private static int parseIntSafe(String text, int defaultValue) {
        if (text == null || text.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    /**
     * 按名称打开 Sheet 流。
     */
    private static InputStream openSheetStream(XSSFReader reader, String targetName) throws Exception {
        Iterator<InputStream> sheets = reader.getSheetsData();
        XSSFReader.SheetIterator shIt = (XSSFReader.SheetIterator)sheets;
        while (shIt.hasNext()) {
            InputStream stream = shIt.next();
            String name = shIt.getSheetName();
            if (name != null && name.equals(targetName)) {
                return stream;
            }
            closeQuietly(stream);
        }
        return null;
    }

    /**
     * 文件级读锁。
     */
    private static Object readLockFor(File xlsxFile) throws Exception {
        String key = xlsxFile.getCanonicalPath();
        Object lock = READ_LOCKS.get(key);
        if (lock == null) {
            Object created = new Object();
            Object existing = READ_LOCKS.putIfAbsent(key, created);
            lock = existing != null ? existing : created;
        }
        return lock;
    }

    /**
     * 关闭流。
     */
    private static void closeQuietly(InputStream in) {
        if (in == null) {
            return;
        }
        try {
            in.close();
        } catch (Exception ignored) {
        }
    }

    /**
     * 关闭事件读取器。
     */
    private static void closeQuietly(XMLEventReader reader) {
        if (reader == null) {
            return;
        }
        try {
            reader.close();
        } catch (Exception ignored) {
        }
    }
}
