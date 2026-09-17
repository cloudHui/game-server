package com.gamer.data.gdg.generate;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import com.gamer.data.gdg.util.DataType;
import com.gamer.data.gdg.validate.GdErrorMessage;
import com.gamer.data.gdg.validate.GdSheetSchema;
/**
 * GD 二进制文件写入工具。
 */
public class WriteTools {

    /** 字节序 */
    public static final ByteOrder byteOrder;

    static {
        byteOrder = ByteOrder.LITTLE_ENDIAN;
    }

    /**
     * 构建 GD 文件头 16 字节块。
     *
     * @param schema Schema
     * @param dataRowCount 数据行数
     * @return 头块
     */
    public static ByteBuffer buildHeaderBlock(GdSheetSchema schema, int dataRowCount) {
        ByteBuffer buf = ByteBuffer.allocate(16);
        buf.order(byteOrder);
        buf.putInt(dataRowCount);
        buf.putInt(countActiveColumns(schema));
        byte[] colNames = buildColNameBytes(schema);
        byte[] colTypes = buildColTypeBytes(schema);
        buf.putInt(colNames.length);
        buf.putInt(colTypes.length);
        buf.flip();
        return buf;
    }

    /**
     * 构建列名+列类型块。
     *
     * @param schema Schema
     * @return 元数据块
     */
    public static ByteBuffer buildMetaBlock(GdSheetSchema schema) {
        byte[] colNames = buildColNameBytes(schema);
        byte[] colTypes = buildColTypeBytes(schema);
        ByteBuffer buf = ByteBuffer.allocate(colNames.length + colTypes.length);
        buf.order(byteOrder);
        buf.put(colNames);
        buf.put(colTypes);
        buf.flip();
        return buf;
    }

    /**
     * 统计有效列数（类型非空）。
     */
    private static int countActiveColumns(GdSheetSchema schema) {
        int col = schema.getColumns();
        int empty = 0;
        for (String s : schema.getHeadRows().get(2)) {
            if (s.isEmpty()) {
                empty++;
            }
        }
        return col - empty;
    }

    /**
     * 构建列名字节串。
     */
    private static byte[] buildColNameBytes(GdSheetSchema schema) {
        return buildColNameBuffer(schema).toString().getBytes();
    }

    /**
     * 构建列类型字节串。
     */
    private static byte[] buildColTypeBytes(GdSheetSchema schema) {
        return buildColTypeBuffer(schema).toString().getBytes();
    }

    /**
     * 列名缓冲。
     */
    private static StringBuffer buildColNameBuffer(GdSheetSchema schema) {
        StringBuffer colNa = new StringBuffer();
        for (String colName : schema.getHeadRows().get(0)) {
            if (!colName.isEmpty()) {
                colNa.append(colName).append('\u0000');
            }
        }
        return colNa;
    }

    /**
     * 列类型缓冲。
     */
    private static StringBuffer buildColTypeBuffer(GdSheetSchema schema) {
        StringBuffer colTp = new StringBuffer();
        for (String colType : schema.getHeadRows().get(2)) {
            if (!colType.isEmpty()) {
                colTp.append(Objects.requireNonNull(DataType.parseName(colType)).getShortName()).append('\u0000');
            }
        }
        return colTp;
    }

    /**
     * 将一行写入 ByteBuffer。
     */
    public static void appendRowBytes(ByteBuffer buf, GdSheetSchema schema, String[] rowValues) throws Exception {
        String[] types = schema.getHeadRows().get(2);
        String sheetName = schema.getSheetName();
        for (int j = 0; j < types.length; j++) {
            if (types[j].isEmpty()) {
                continue;
            }
            String cellValue = j < rowValues.length ? rowValues[j] : "";
            String colName = schema.getColumnName(j);
            DataType dt = DataType.parseName(types[j]);
            if (dt == DataType.TYPE_STR) {
                byte[] strByte = cellValue.getBytes(StandardCharsets.UTF_8);
                buf.putInt(strByte.length);
                buf.put(strByte);
            } else if (dt == DataType.TYPE_FLOAT) {
                appendFloatCell(buf, sheetName, colName, cellValue);
            } else if (dt == DataType.TYPE_INT || dt == DataType.TYPE_V_IDX) {
                appendIntCell(buf, sheetName, colName, cellValue);
            }
        }
    }

    /**
     * 写入浮点单元格。
     */
    private static void appendFloatCell(ByteBuffer buf, String sheetName, String colName, String cellValue) throws Exception {
        try {
            buf.putFloat(Float.parseFloat(cellValue));
        } catch (NumberFormatException e) {
            throw new Exception(GdErrorMessage.formatCellError(sheetName, 0, colName, cellValue,
                "数值写入失败: " + e.getMessage()));
        }
    }

    /**
     * 写入整型单元格。
     */
    private static void appendIntCell(ByteBuffer buf, String sheetName, String colName, String cellValue) throws Exception {
        try {
            buf.putInt(Integer.parseInt(cellValue));
        } catch (NumberFormatException e) {
            throw new Exception(GdErrorMessage.formatCellError(sheetName, 0, colName, cellValue,
                "数值写入失败: " + e.getMessage()));
        }
    }
}


