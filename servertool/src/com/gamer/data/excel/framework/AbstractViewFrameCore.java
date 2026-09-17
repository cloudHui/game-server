package com.gamer.data.excel.framework;

import com.gamer.data.log.Log;

import java.awt.CardLayout;
import java.awt.Color;
import java.io.File;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import javax.swing.DefaultListModel;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

import com.gamer.data.ui.ViewUi;
import com.gamer.data.gdg.excel.ExcelOperate;
import com.gamer.data.gdg.progress.GdProgressContext;

/**
 * Excel 视图公共基类（文件列表、日志、目录工具），不含四表盘分页。
 */
public abstract class AbstractViewFrameCore extends JFrame {

    public JList<String> fileList;
    public DefaultListModel<String> listModel;
    public JTextPane logArea;
    public final BaseCheck baseCheck;
    public ExcelOperate currentExcelOperate;
    public final Map<String, ExcelOperate> fileMap = new HashMap<>();

    public JPanel excelFileDisplayPanel;

    public AbstractViewFrameCore(BaseCheck baseCheck) {
        this.baseCheck = baseCheck;
    }

    /**
     * 安装文件列表右键菜单；子类可覆盖（如 StrategyTool 删除限制）。
     */
    protected void installFileListContextMenu() {}

    /**
     * 文件列表选中回调；子类可覆盖（如后台打开大文件）。
     *
     * @param excelOperate
     *            选中的 Excel
     */
    protected void onExcelFileSelected(ExcelOperate excelOperate) {}

    /**
     * 释放除当前选中文件外已打开的 Workbook，降低多文件同时占内存。
     *
     * @param keepFileName
     *            保留的文件名
     */
    protected void releaseOtherWorkbooks(String keepFileName) {
        for (Map.Entry<String, ExcelOperate> entry : fileMap.entrySet()) {
            if (keepFileName != null && keepFileName.equals(entry.getKey())) {
                continue;
            }
            ExcelOperate other = entry.getValue();
            if (other != null) {
                other.releaseWorkbook();
            }
        }
    }

    /**
     * 记录普通日志。
     *
     * @param message
     *            消息
     */
    public void logMessage(String message) {
        logMessage(message, false);
    }

    /**
     * 记录日志（可标红/进度色）。
     *
     * @param message
     *            消息
     * @param redShow
     *            是否红色强调
     */
    public void logMessage(String message, boolean redShow) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> logMessage(message, redShow));
            return;
        }
        if (logArea == null) {
            System.err.println(message);
            return;
        }
        String timestamp = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        String logText = "[" + timestamp + "] " + message + "\n";
        StyledDocument logDoc = logArea.getStyledDocument();
        Style logStyle = logArea.addStyle("LogStyle", null);
        StyleConstants.setFontFamily(logStyle, ViewUi.FONT.getFamily());
        StyleConstants.setFontSize(logStyle, ViewUi.FONT.getSize());
        if (redShow) {
            StyleConstants.setForeground(logStyle, ViewUi.DANGER);
            StyleConstants.setBold(logStyle, true);
        } else if (message != null && message.contains(GdProgressContext.TAG_DONE)) {
            StyleConstants.setForeground(logStyle, new Color(0, 128, 0));
            StyleConstants.setBold(logStyle, false);
        } else if (message != null
            && (message.contains(GdProgressContext.TAG_PROGRESS) || message.contains(GdProgressContext.TAG_WAIT))) {
            StyleConstants.setForeground(logStyle, ViewUi.LINK);
            StyleConstants.setBold(logStyle, false);
        } else {
            StyleConstants.setForeground(logStyle, ViewUi.TITLE);
            StyleConstants.setBold(logStyle, false);
        }
        try {
            logDoc.insertString(logDoc.getLength(), logText, logStyle);
            logArea.setCaretPosition(logDoc.getLength());
        } catch (BadLocationException e) {
            System.err.println(message);
        }
    }

    /**
     * 切换文件列表卡片（有文件 / 无文件）。
     */
    public void showExcelFileList() {
        CardLayout cardLayout = (CardLayout)excelFileDisplayPanel.getLayout();
        if (!listModel.isEmpty()) {
            cardLayout.show(excelFileDisplayPanel, "FILE_LIST");
            fileList.setSelectedIndex(0);
        } else {
            cardLayout.show(excelFileDisplayPanel, "NO_FILE");
        }
    }

    /**
     * 关闭所有已登记 Excel。
     */
    public void closeWebhook() {
        closeAllExcelOperates();
    }

    /**
     * 关闭所有 ExcelOperate。
     */
    public void closeAllExcelOperates() {
        for (ExcelOperate eo : fileMap.values()) {
            closeExcelOperate(eo);
        }
    }

    /**
     * 关闭单个 ExcelOperate。
     *
     * @param eo
     *            操作对象
     */
    public void closeExcelOperate(ExcelOperate eo) {
        if (eo == null) {
            return;
        }
        try {
            eo.releaseWorkbook();
        } catch (Exception e) {
            logMessage(e.getMessage());
        }
    }

    /**
     * 列出目录下合法英文名的 xlsx。
     *
     * @param dir
     *            目录
     * @return xlsx 文件数组
     */
    public File[] listExcelFiles(File dir) {
        return dir.listFiles(file -> {
            String name = file.getName().toLowerCase();
            String[] split = name.split("\\.");
            if (split.length != 2) {
                return false;
            }
            return !checkNotFitSheetName(split[0]) && split[1].equals("xlsx");
        });
    }

    /**
     * 供 gdg 模块写日志的适配器。
     */
    public final Log log = new Log() {
        @Override
        public void logMessage(String message) {
            AbstractViewFrameCore.this.logMessage(message);
        }

        @Override
        public void logMessage(String message, boolean redShow) {
            AbstractViewFrameCore.this.logMessage(message, redShow);
        }
    };

    /**
     * Sheet 名是否为合法英文名。
     *
     * @param name
     *            Sheet 名
     * @return 不合法返回 true
     */
    public boolean checkNotFitSheetName(String name) {
        return !Pattern.matches("[a-zA-Z][a-zA-Z_]+", name);
    }
}
