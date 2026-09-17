package com.gamer.data.gdg.excel;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.SAXParserFactory;

import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackageAccess;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

/**
 * 复用已打开的 OPC 包，避免同一文件多次 {@link OPCPackage#open}。
 */
public final class XlsxOpcSession {

    private final File file;
    private OPCPackage pkg;
    private XSSFReader reader;

    private XlsxOpcSession(File file) {
        this.file = file;
    }

    /**
     * 打开 xlsx（须在 finally 中 {@link #close()}）。
     */
    public static XlsxOpcSession open(File xlsxFile) throws Exception {
        ExcelOperate.applyLargeFileZipSettings();
        XlsxOpcSession session = new XlsxOpcSession(xlsxFile);
        session.pkg = openReadOnlyPackage(xlsxFile);
        session.reader = new XSSFReader(session.pkg);
        return session;
    }

    public File getFile() {
        return file;
    }

    /**
     * 读取 Sheet 名列表。
     */
    public List<String> readSheetNames() throws Exception {
        InputStream wbStream = null;
        try {
            wbStream = reader.getWorkbookData();
            final List<String> names = new ArrayList<>();
            XMLReader xmlReader = newNamespaceAwareXmlReader();
            xmlReader.setContentHandler(new DefaultHandler() {
                @Override
                public void startElement(String uri, String localName, String qName, Attributes attributes) {
                    String tag = localName == null ? "" : localName;
                    if (tag.isEmpty() && qName != null) {
                        int p = qName.lastIndexOf(':');
                        tag = p >= 0 ? qName.substring(p + 1) : qName;
                    }
                    if (!"sheet".equals(tag) || attributes == null) {
                        return;
                    }
                    String n = attributes.getValue("name");
                    if (n != null && !n.trim().isEmpty()) {
                        names.add(n);
                    }
                }
            });
            xmlReader.parse(new InputSource(wbStream));
            return names;
        } finally {
            closeQuietly(wbStream);
        }
    }

    /**
     * 流式读取指定 Sheet（复用本会话 OPC）。
     */
    public void readSheetRows(String sheetName, GdStreamRowHandler handler) throws Exception {
        XlsxSheetStreamReader.readSheetRows(this, sheetName, handler);
    }

    /**
     * 以只读方式打开 xlsx（不触发 close/save 改写磁盘修改时间）。
     */
    public static OPCPackage openReadOnlyPackage(File xlsxFile) throws Exception {
        ExcelOperate.applyLargeFileZipSettings();
        return OPCPackage.open(xlsxFile, PackageAccess.READ);
    }

    /**
     * 关闭只读 OPC 包（使用 revert，勿对 READ 包调用 save 型 close）。
     */
    public static void closeReadOnlyPackage(OPCPackage pkg) {
        if (pkg == null) {
            return;
        }
        try {
            pkg.revert();
        } catch (Exception ignored) {
            // 关闭失败时忽略
        }
    }

    /**
     * 关闭 OPC 包。
     */
    public void close() {
        OPCPackage toClose = pkg;
        pkg = null;
        reader = null;
        closeReadOnlyPackage(toClose);
    }

    /**
     * 已打开的 OPC 包（调用方勿 close，由 {@link #close()} 统一释放）。
     *
     * @return OPC 包
     */
    public OPCPackage getPackage() {
        return pkg;
    }

    /**
     * 已创建的 XSSF 读取器。
     *
     * @return XSSFReader
     */
    public XSSFReader getReader() {
        return reader;
    }

    private static XMLReader newNamespaceAwareXmlReader() throws Exception {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newSAXParser().getXMLReader();
    }

    private static void closeQuietly(InputStream in) {
        if (in == null) {
            return;
        }
        try {
            in.close();
        } catch (Exception ignored) {
        }
    }
}
