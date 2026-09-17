package com.gamer.data.gdg.excel;

import java.io.File;
import java.io.InputStream;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import javax.xml.parsers.SAXParserFactory;

import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.eventusermodel.ReadOnlySharedStringsTable;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler.SheetContentsHandler;
import org.apache.poi.xssf.model.StylesTable;
import org.apache.poi.xssf.usermodel.XSSFComment;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import com.gamer.data.gdg.util.GdDataFormatter;

/**
 * xlsx SAX 按行读取（供 GD 流式校验/写入）。
 */
public final class XlsxSheetStreamReader {

    /** 同一 xlsx 并发 SAX 读时串行化，避免 OPC 包争用 */
    private static final Map<String, Object> READ_LOCKS = new ConcurrentHashMap<>();

    /**
     * 禁止实例化。
     */
    private XlsxSheetStreamReader() {
    }

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
     * 读取 xlsx 全部 Sheet 名称（顺序与 Excel 一致）。
     *
     * @param xlsxFile xlsx 文件
     * @return Sheet 名列表
     * @throws Exception 读取失败
     */
    public static List<String> readSheetNames(File xlsxFile) throws Exception {
        synchronized (readLockFor(xlsxFile)) {
            return readSheetNamesUnlocked(xlsxFile);
        }
    }

    private static List<String> readSheetNamesUnlocked(File xlsxFile) throws Exception {
        XlsxOpcSession session = XlsxOpcSession.open(xlsxFile);
        try {
            return session.readSheetNames();
        } finally {
            session.close();
        }
    }

    /**
     * 流式读取指定 Sheet 的每一行。
     *
     * @param xlsxFile xlsx 文件
     * @param sheetName Sheet 名
     * @param handler 行回调
     * @throws Exception 读取失败
     */
    public static void readSheetRows(File xlsxFile, String sheetName, GdStreamRowHandler handler) throws Exception {
        readSheetRows(xlsxFile, sheetName, handler, null);
    }

    /**
     * 流式读取指定 Sheet 的每一行。
     *
     * @param xlsxFile
     *            xlsx 文件
     * @param sheetName
     *            Sheet 名
     * @param handler
     *            行回调
     * @param styleWarn
     *            样式读取失败回调，可为 null
     * @throws Exception
     *             读取失败
     */
    public static void readSheetRows(File xlsxFile, String sheetName, GdStreamRowHandler handler,
        XlsxStyles.Warn styleWarn) throws Exception {
        synchronized (readLockFor(xlsxFile)) {
            XlsxOpcSession session = XlsxOpcSession.open(xlsxFile);
            try {
                readSheetRows(session, sheetName, handler, styleWarn);
            } finally {
                session.close();
            }
        }
    }

    /**
     * 在已打开的 {@link XlsxOpcSession} 上流式读取 Sheet（不重复 open）。
     *
     * @param session
     *            已打开会话
     * @param sheetName
     *            Sheet 名
     * @param handler
     *            行回调
     * @throws Exception
     *             读取失败
     */
    public static void readSheetRows(XlsxOpcSession session, String sheetName, GdStreamRowHandler handler)
        throws Exception {
        readSheetRows(session, sheetName, handler, null);
    }

    /**
     * 在已打开的 {@link XlsxOpcSession} 上流式读取 Sheet（不重复 open）。
     *
     * @param session
     *            已打开会话
     * @param sheetName
     *            Sheet 名
     * @param handler
     *            行回调
     * @param styleWarn
     *            样式读取失败回调，可为 null
     * @throws Exception
     *             读取失败
     */
    public static void readSheetRows(XlsxOpcSession session, String sheetName, GdStreamRowHandler handler,
        XlsxStyles.Warn styleWarn) throws Exception {
        OPCPackage pkg = session.getPackage();
        InputStream sheetStream = null;
        try {
            ReadOnlySharedStringsTable sst = new ReadOnlySharedStringsTable(pkg);
            XSSFReader reader = session.getReader();
            StylesTable styles = XlsxStyles.read(reader, styleWarn);
            sheetStream = openSheetStream(reader, sheetName);
            if (sheetStream == null) {
                throw new IllegalArgumentException("找不到sheet: " + sheetName);
            }
            GdDataFormatter formatter = new GdDataFormatter();
            SheetContentsHandler rowHandler = new GdSaxRowAdapter(handler);
            // false：读公式求值结果（#N/A 等），供 Excel错误值 校验；true 时共享公式格可能读出空串而漏检
            XSSFSheetXMLHandler sheetHandler = new XSSFSheetXMLHandler(styles, null, sst, rowHandler, formatter, false);
            XMLReader xmlReader = newNamespaceAwareXmlReader();
            xmlReader.setContentHandler(sheetHandler);
            xmlReader.parse(new InputSource(sheetStream));
        } finally {
            closeQuietly(sheetStream);
        }
    }

    /**
     * 创建 namespace-aware XMLReader。
     */
    private static XMLReader newNamespaceAwareXmlReader() throws Exception {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newSAXParser().getXMLReader();
    }

    /**
     * 按名称打开 Sheet 流。
     */
    private static InputStream openSheetStream(XSSFReader reader, String targetName) throws Exception {
        Iterator<InputStream> sheets = reader.getSheetsData();
        XSSFReader.SheetIterator shIt = (XSSFReader.SheetIterator) sheets;
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
     * 关闭 InputStream。
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
     * SAX 行适配：把 POI 行事件转为 {@link GdStreamRowHandler}。
     */
    private static final class GdSaxRowAdapter implements SheetContentsHandler {
        /** 业务行回调 */
        private final GdStreamRowHandler handler;
        /** 当前行号（0-based） */
        private int currentRowNum = -1;
        /** 当前行单元格 */
        private final TreeMap<Integer, String> colValues = new TreeMap<>();

        GdSaxRowAdapter(GdStreamRowHandler handler) {
            this.handler = handler;
        }

        @Override
        public void startRow(int rowNum) {
            // 记录行号并清空列缓存
            currentRowNum = rowNum;
            colValues.clear();
        }

        @Override
        public void endRow(int rowNum) {
            // 通知业务层一行结束
            handler.onRowEnd(rowNum, colValues);
            colValues.clear();
        }

        @Override
        public void cell(String cellReference, String formattedValue, XSSFComment comment) {
            // 空引用忽略
            if (cellReference == null || cellReference.trim().isEmpty()) {
                return;
            }
            // 解析列号
            int col = new CellReference(cellReference).getCol();
            String v = formattedValue == null ? "" : formattedValue.trim();
            // 公式以 = 开头
            boolean isFormula = v.startsWith("=");
            if (isFormula) {
                handler.onFormulaCell(currentRowNum, col);
                return;
            }
            // 布尔规范化
            if ("TRUE".equalsIgnoreCase(v)) {
                v = "Y";
            } else if ("FALSE".equalsIgnoreCase(v)) {
                v = "N";
            }
            colValues.put(col, v);
        }

        @Override
        public void headerFooter(String text, boolean isHeader, String tagName) {
            // GD 不处理页眉页脚
        }
    }
}
