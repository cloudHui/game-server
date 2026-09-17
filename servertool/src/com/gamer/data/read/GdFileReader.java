package com.gamer.data.read;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.gamer.data.excel.core.PagedTablePage;

/**
 * GD文件读取器 用于读取WriteTools生成的.gd文件
 *
 * @author liuyunhui
 * @date 2025/12/05
 */
public class GdFileReader {

    /**
     * 打开 GD 分页上下文：读盘一次，解析表头并建立行偏移索引。
     *
     * @param gdFile
     *            GD 文件
     * @return 分页上下文
     * @throws IOException
     *             读取失败
     */
    public static GdPagingContext openPagingContext(File gdFile) throws IOException {
        if (gdFile == null || !gdFile.exists()) {
            throw new FileNotFoundException("GD文件不存在: " + gdFile);
        }
        byte[] fileBytes = readFileBytes(gdFile);
        ByteBuffer buffer = ByteBuffer.wrap(fileBytes);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        GdHeader header = parseHeader(buffer);
        if (buffer.remaining() >= 4) {
            buffer.position(buffer.position() + 4);
        }
        header.dataOffset = buffer.position();
        int[] rowOffsets = buildRowOffsets(fileBytes, header);
        return new GdPagingContext(header, fileBytes, rowOffsets);
    }

    /**
     * 按页读取 GD 数据行（使用已缓存字节与行偏移，翻页不再重复读盘）。
     *
     * @param context
     *            分页上下文
     * @param pageIndex
     *            页码（从 1 开始）
     * @param pageSize
     *            每页行数
     * @return 分页结果
     */
    public static PagedTablePage readDataPage(GdPagingContext context, int pageIndex, int pageSize) {
        if (context == null || context.getHeader() == null) {
            return new PagedTablePage(new ArrayList<>(), 0);
        }
        GdHeader header = context.getHeader();
        int[] rowOffsets = context.getRowOffsets();
        int totalRows = header.rows;
        int startRow = (pageIndex - 1) * pageSize;
        if (startRow >= totalRows || rowOffsets == null || rowOffsets.length == 0) {
            return new PagedTablePage(new ArrayList<>(), totalRows);
        }

        ByteBuffer buffer = ByteBuffer.wrap(context.getFileBytes());
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.position(rowOffsets[startRow]);

        List<Object[]> pageRows = new ArrayList<>();
        int endRow = startRow + pageSize;
        if (endRow > totalRows) {
            endRow = totalRows;
        }
        for (int rowIndex = startRow; rowIndex < endRow; rowIndex++) {
            Object[] row = readOneRow(buffer, header);
            if (row == null) {
                break;
            }
            pageRows.add(row);
        }
        return new PagedTablePage(pageRows, totalRows);
    }

    /**
     * 扫描数据区，记录每行在文件字节中的起始偏移。
     */
    private static int[] buildRowOffsets(byte[] fileBytes, GdHeader header) {
        int rowCount = header.rows;
        if (rowCount <= 0) {
            return new int[0];
        }
        int[] rowOffsets = new int[rowCount];
        ByteBuffer buffer = ByteBuffer.wrap(fileBytes);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.position(header.dataOffset);
        for (int i = 0; i < rowCount; i++) {
            if (buffer.remaining() <= 0) {
                break;
            }
            rowOffsets[i] = buffer.position();
            advanceOneRow(buffer, header);
        }
        return rowOffsets;
    }

    /**
     * 将缓冲区前进一行（不分配行对象）。
     */
    private static void advanceOneRow(ByteBuffer buffer, GdHeader header) {
        for (int j = 0; j < header.columns; j++) {
            String type = header.columnTypes.get(j);
            if ("s".equals(type)) {
                advanceStringCell(buffer);
            } else if ("f".equals(type)) {
                buffer.getFloat();
            } else if ("i".equals(type) || "v".equals(type)) {
                buffer.getInt();
            } else {
                throw new RuntimeException("未知的列类型: " + type);
            }
        }
    }

    /**
     * 跳过字符串单元格字节。
     */
    private static void advanceStringCell(ByteBuffer buffer) {
        if (buffer.remaining() < 4) {
            return;
        }
        int strLength = buffer.getInt();
        if (strLength > 0) {
            int newPos = buffer.position() + strLength;
            if (newPos <= buffer.limit()) {
                buffer.position(newPos);
            }
        }
    }

    /**
     * 读取文件全部字节。
     */
    private static byte[] readFileBytes(File gdFile) throws IOException {
        FileInputStream fis = null;
        BufferedInputStream bis = null;
        try {
            fis = new FileInputStream(gdFile);
            bis = new BufferedInputStream(fis);
            return readAllBytes(bis);
        } finally {
            if (bis != null) {
                try {
                    bis.close();
                } catch (IOException ignored) {
                }
            } else if (fis != null) {
                try {
                    fis.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    /**
     * 读取GD 文件
     */
    public static GdData readGdFile(File gdFile) throws IOException {
        if (gdFile == null || !gdFile.exists()) {
            throw new FileNotFoundException("GD文件不存在: " + gdFile);
        }

        try (FileInputStream fis = new FileInputStream(gdFile);
            BufferedInputStream bis = new BufferedInputStream(fis)) {

            // 读取所有字节
            byte[] allBytes = readAllBytes(bis);
            ByteBuffer buffer = ByteBuffer.wrap(allBytes);
            buffer.order(ByteOrder.LITTLE_ENDIAN);

            // 解析头部
            GdHeader header = parseHeader(buffer);

            // 解析数据部分
            List<Object[]> dataRows = parseData(buffer, header);

            return new GdData(header, dataRows);
        }
    }

    /**
     * 读取InputStream 中的所有字节
     */
    private static byte[] readAllBytes(InputStream inputStream) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[8192];
        int bytesRead;

        while ((bytesRead = inputStream.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, bytesRead);
        }

        buffer.flush();
        return buffer.toByteArray();
    }

    /**
     * 解析GD 文件头部
     */
    private static GdHeader parseHeader(ByteBuffer buffer) {
        GdHeader header = new GdHeader();

        try {
            // 读取第一部分的16字节
            if (buffer.remaining() < 16) {
                throw new RuntimeException("GD文件格式错误: 头部信息不足");
            }

            header.rows = buffer.getInt();
            header.columns = buffer.getInt();
            header.colNameLength = buffer.getInt();
            header.colTypeLength = buffer.getInt();

            // 验证数据
            if (header.rows < 0 || header.columns < 0 || header.colNameLength < 0 || header.colTypeLength < 0) {
                throw new RuntimeException("GD文件格式错误: 头部数据异常");
            }

            // 读取列名
            if (header.colNameLength > 0) {
                if (buffer.remaining() < header.colNameLength) {
                    throw new RuntimeException("GD文件格式错误: 列名数据长度不足");
                }

                byte[] colNameBytes = new byte[header.colNameLength];
                buffer.get(colNameBytes);
                String colNameStr = new String(colNameBytes, StandardCharsets.UTF_8);

                // 按空字符分割列名
                String[] colNames = colNameStr.split("\u0000");
                for (String name : colNames) {
                    if (!name.trim().isEmpty()) {
                        header.columnNames.add(name.trim());
                    }
                }
            }

            // 读取列类型
            if (header.colTypeLength > 0) {
                if (buffer.remaining() < header.colTypeLength) {
                    throw new RuntimeException("GD文件格式错误: 列类型数据长度不足");
                }

                byte[] colTypeBytes = new byte[header.colTypeLength];
                buffer.get(colTypeBytes);
                String colTypeStr = new String(colTypeBytes, StandardCharsets.UTF_8);

                // 按空字符分割列类型
                String[] colTypes = colTypeStr.split("\u0000");
                for (String type : colTypes) {
                    if (!type.trim().isEmpty()) {
                        header.columnTypes.add(type.trim());
                    }
                }
            }

            // 验证列名和列类型数量是否匹配
            if (header.columnNames.size() != header.columnTypes.size()) {
                throw new RuntimeException(String.format("GD文件格式错误: 列名数量(%d)与列类型数量(%d)不匹配", header.columnNames.size(),
                    header.columnTypes.size()));
            }

            // 如果列名数量与columns不一致，使用columns作为列数
            if (header.columnNames.size() != header.columns) {
                System.out.printf("警告: 实际列名数量(%d)与声明的列数(%d)不一致%n", header.columnNames.size(), header.columns);
                header.columns = header.columnNames.size();
            }

        } catch (Exception e) {
            throw new RuntimeException("解析GD文件头部失败: " + e.getMessage(), e);
        }

        return header;
    }

    /**
     * 解析数据部分
     */
    private static List<Object[]> parseData(ByteBuffer buffer, GdHeader header) {
        List<Object[]> dataRows = new ArrayList<>();
        try {
            if (buffer.remaining() < 4) {
                throw new RuntimeException("GD文件格式错误: 数据长度标记缺失");
            }
            buffer.position(buffer.position() + 4);
            header.dataOffset = buffer.position();
            for (int i = 0; i < header.rows; i++) {
                Object[] row = readOneRow(buffer, header);
                if (row != null) {
                    dataRows.add(row);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("解析GD文件数据失败: " + e.getMessage(), e);
        }
        return dataRows;
    }

    /**
     * 读取一行数据。
     */
    private static Object[] readOneRow(ByteBuffer buffer, GdHeader header) {
        if (buffer.remaining() <= 0) {
            return null;
        }
        Object[] row = new Object[header.columns];
        for (int j = 0; j < header.columns; j++) {
            String type = header.columnTypes.get(j);
            if ("s".equals(type)) {
                row[j] = readStringCell(buffer);
            } else if ("f".equals(type)) {
                row[j] = buffer.getFloat();
            } else if ("i".equals(type) || "v".equals(type)) {
                row[j] = buffer.getInt();
            } else {
                throw new RuntimeException("未知的列类型: " + type);
            }
        }
        return row;
    }

    /**
     * 读取字符串列单元格。
     */
    private static String readStringCell(ByteBuffer buffer) {
        int strLength = buffer.getInt();
        if (strLength <= 0) {
            return "";
        }
        if (buffer.remaining() < strLength) {
            throw new RuntimeException("GD文件格式错误: 字符串数据不足");
        }
        byte[] strBytes = new byte[strLength];
        buffer.get(strBytes);
        return new String(strBytes, StandardCharsets.UTF_8);
    }
}