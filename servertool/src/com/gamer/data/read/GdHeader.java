package com.gamer.data.read;

import java.util.ArrayList;
import java.util.List;

// GD 文件头部信息
public class GdHeader {
    public int rows;
    public int columns;
    public int colNameLength;
    public int colTypeLength;
    public List<String> columnNames;
    public List<String> columnTypes;

    /** 数据区在文件中的起始偏移（解析头部后填充） */
    public int dataOffset;

    public GdHeader() {
        columnNames = new ArrayList<>();
        columnTypes = new ArrayList<>();
    }
}