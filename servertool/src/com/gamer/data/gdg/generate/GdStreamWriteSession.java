package com.gamer.data.gdg.generate;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;

import com.gamer.data.gdg.validate.GdSheetSchema;

/**
 * 流式写 GD 文件会话（先缓冲数据区，finish 时按实际写入行数写头块）。
 */
public class GdStreamWriteSession {

    private final ByteBuffer dataBuf;
    private final GdSheetSchema schema;
    private final File outFile;
    private int writtenRowCount;
    private boolean finished;

    /**
     * @param schema
     *            Schema
     * @param outFile
     *            目标文件（可为 .gd.tmp）
     */
    public static GdStreamWriteSession open(GdSheetSchema schema, File outFile) {
        return new GdStreamWriteSession(schema, outFile);
    }

    private GdStreamWriteSession(GdSheetSchema schema, File outFile) {
        this.schema = schema;
        this.outFile = outFile;
        this.dataBuf = ByteBuffer.allocate(16777216);
        this.dataBuf.order(WriteTools.byteOrder);
        this.writtenRowCount = 0;
    }

    public void writeDataRow(String[] rowValues) throws Exception {
        WriteTools.appendRowBytes(dataBuf, schema, rowValues);
        writtenRowCount++;
    }

    /**
     * @return 已写入的有效数据行数
     */
    public int getWrittenRowCount() {
        return writtenRowCount;
    }

    public void finish() throws Exception {
        if (finished) {
            return;
        }
        try (FileOutputStream fos = new FileOutputStream(outFile)) {
            fos.write(WriteTools.buildHeaderBlock(schema, writtenRowCount).array());
            fos.write(WriteTools.buildMetaBlock(schema).array());
            writeDataBlock(fos);
            fos.flush();
        }
        finished = true;
    }

    private void writeDataBlock(FileOutputStream fos) throws Exception {
        int pos = dataBuf.position();
        ByteBuffer len = ByteBuffer.allocate(4);
        len.order(WriteTools.byteOrder);
        len.putInt(pos);
        len.flip();
        fos.write(len.array());
        fos.write(Arrays.copyOfRange(dataBuf.array(), 0, pos));
    }

    public void abort() {
        if (!finished && outFile.exists()) {
            outFile.delete();
        }
    }
}
