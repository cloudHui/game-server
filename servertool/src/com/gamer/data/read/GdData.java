package com.gamer.data.read;

import java.util.List;

/**
 * GD 文件数据（全量或分页填充 dataRows）。
 */
public class GdData {
    public GdHeader header;
    public List<Object[]> dataRows;

    public GdData(GdHeader header, List<Object[]> dataRows) {
        this.header = header;
        this.dataRows = dataRows;
    }

    /**
     * @return 数据行数
     */
    public int getRowCount() {
        return dataRows != null ? dataRows.size() : 0;
    }
}