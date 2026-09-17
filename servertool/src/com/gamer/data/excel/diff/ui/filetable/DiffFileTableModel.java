package com.gamer.data.excel.diff.ui.filetable;

import java.io.File;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import javax.swing.table.AbstractTableModel;

/**
 * Excel 文件台账表格模型。上层含 SVN 列，当前不含。
 */
public final class DiffFileTableModel extends AbstractTableModel {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("MM-dd HH:mm");
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    /** 单行：文件名 / SVN / 修改时间 */
    public static final class Row {
        public final String fileName;
        public final String svn;
        public final String mtime;

        public Row(String fileName, String svn, String mtime) {
            this.fileName = fileName;
            this.svn = svn;
            this.mtime = mtime;
        }
    }

    private final boolean showSvn;
    private final String[] columns;
    private final List<Row> rows = new ArrayList<>();

    public DiffFileTableModel(boolean showSvn) {
        this.showSvn = showSvn;
        this.columns = showSvn ? new String[] {"文件名", "SVN", "修改时间"} : new String[] {"文件名", "修改时间"};
    }

    public void setRows(List<Row> newRows) {
        rows.clear();
        if (newRows != null) {
            rows.addAll(newRows);
        }
        fireTableDataChanged();
    }

    public void clear() {
        rows.clear();
        fireTableDataChanged();
    }

    public String getFileName(int index) {
        return index < 0 || index >= rows.size() ? null : rows.get(index).fileName;
    }

    public static Row createRow(String fileName, File file, String svnStatus) {
        String svn = svnStatus == null || svnStatus.isEmpty() ? "-" : svnStatus;
        return new Row(fileName, svn, formatMtime(file));
    }

    public static String formatMtime(File file) {
        if (file == null || !file.exists()) {
            return "";
        }
        return TIME_FMT.format(LocalDateTime.ofInstant(Instant.ofEpochMilli(file.lastModified()), ZONE));
    }

    @Override
    public int getRowCount() {
        return rows.size();
    }

    @Override
    public int getColumnCount() {
        return columns.length;
    }

    @Override
    public String getColumnName(int column) {
        return columns[column];
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        Row row = rows.get(rowIndex);
        if (showSvn) {
            switch (columnIndex) {
            case 0:
                return row.fileName;
            case 1:
                return row.svn;
            case 2:
                return row.mtime;
            default:
                return "";
            }
        }
        return columnIndex == 0 ? row.fileName : columnIndex == 1 ? row.mtime : "";
    }
}
