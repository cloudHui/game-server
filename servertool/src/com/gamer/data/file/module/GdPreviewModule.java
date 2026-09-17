package com.gamer.data.file.module;

import com.gamer.data.task.BackgroundTasks;

import java.awt.BorderLayout;
import java.awt.Component;
import java.io.File;
import java.util.List;

import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;

import com.gamer.data.ui.ViewUi;
import com.gamer.data.file.utils.Const;
import com.gamer.data.file.utils.Utils;
import com.gamer.data.read.GdData;
import com.gamer.data.read.GdFileReader;

/**
 * GD 预览 Module：封装文件选择、读取、分页状态和表格渲染。
 *
 * <p>主窗口只需要组装 {@link #getView()}，并通过 {@link #openFileAsync(File, OpenListener)} 打开文件。</p>
 */
public final class GdPreviewModule {

    /** 异步打开完成回调 Interface。 */
    public interface OpenListener {
        /** @param success 是否成功打开 */
        void onFinished(boolean success);
    }

    /** 默认每页展示行数。 */
    private static final int PAGE_SIZE = 50;

    /**
     * 「打开 GD」文件选择框默认目录（相对 user.dir / servertool）。
     * 解析后不存在时回退到 user.dir。
     */
    private static final String DEFAULT_GD_DIR = "D:/code/WorkSpace/Document/DevelopmentGD&ConfigurationExcel/GD/data";

    /** Module 所属的父界面，用于文件选择与提示框定位。 */
    private final Component owner;

    /** GD 数据分页内容 Module，统一处理页码与每页数量变化。 */
    private final PagedContentModule<Object[]> pagedRows;

    /** 根面板。 */
    private final JPanel view = new JPanel(new BorderLayout());

    /** 文件及数据量说明。 */
    private final JLabel infoLabel = new JLabel("请打开 GD 文件或将 GD 文件拖放到窗口");

    /** 表格模型。 */
    private DefaultTableModel tableModel = new DefaultTableModel();

    /** 数据表格。 */
    private final JTable table = new JTable(tableModel);

    /** 当前 GD 数据，用于读取列名。 */
    private GdData data;

    /**
     * @param owner
     *            父界面
     */
    public GdPreviewModule(Component owner) {
        this.owner = owner;
        pagedRows = new PagedContentModule<>(PAGE_SIZE, this::renderPageRows);
        buildView();
    }

    /**
     * @return 可直接装入页签的 GD 预览面板
     */
    public JPanel getView() {
        return view;
    }

    /**
     * 读取并展示一个 GD 文件。
     *
     * @param file
     *            GD 文件
     * @param listener
     *            异步打开结果回调，可为 null
     */
    public void openFileAsync(final File file, final OpenListener listener) {
        final GdData[] loadedHolder = new GdData[1];
        BackgroundTasks.start("gd-preview-loader", () -> {
            loadedHolder[0] = GdFileReader.readGdFile(file);
            return 0;
        }, new BackgroundTasks.Listener() {
            @Override
            public void onStarted(long startMillis) {
                // GD 读取耗时由后台任务承载，界面在成功/失败回调中一次性刷新。
            }

            @Override
            public void onSucceeded(int resultCode, long endMillis) {
                SwingUtilities.invokeLater(() -> {
                    applyLoadedData(file, loadedHolder[0]);
                    notifyFinished(listener, true);
                });
            }

            @Override
            public void onFailed(final Exception error, long endMillis) {
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(owner, "读取 GD 文件失败: " + error.getMessage(), "错误",
                        JOptionPane.ERROR_MESSAGE);
                    notifyFinished(listener, false);
                });
            }

            @Override
            public void onFinished() {
                // GD 预览没有运行中按钮状态，完成通知已在成功/失败分支发送。
            }
        });
    }

    /**
     * 在 EDT 应用已读取的 GD 数据。
     *
     * @param file GD 文件
     * @param loaded 已读取数据
     */
    private void applyLoadedData(File file, GdData loaded) {
        try {
            if (loaded == null) {
                throw new IllegalStateException("GD 数据为空");
            }
            // 文件读取与 UI 状态更新集中在同一 Module，避免主窗口持有 GD 内部状态。
            data = loaded;
            renderColumns();
            pagedRows.setItems(data.dataRows);
            infoLabel.setText(String.format("文件: %s | 行数: %d | 列数: %d", file.getName(),
                data.dataRows.size(), data.header.columns));
            JOptionPane.showMessageDialog(owner,
                String.format("成功加载 GD 文件:\n文件: %s\n行数: %d\n列数: %d", file.getName(),
                    data.dataRows.size(), data.header.columns),
                "GD 文件加载成功", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(owner, "读取 GD 文件失败: " + ex.getMessage(), "错误",
                JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * 安全通知打开结果。
     *
     * @param listener 回调
     * @param success 是否成功
     */
    private void notifyFinished(OpenListener listener, boolean success) {
        if (listener != null) {
            listener.onFinished(success);
        }
    }

    /**
     * 创建并装配 GD 预览界面。
     */
    private void buildView() {
        ViewUi.page(view);
        ViewUi.label(infoLabel);
        infoLabel.setHorizontalAlignment(SwingConstants.CENTER);
        ViewUi.table(table);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        JPanel toolPanel = new JPanel(new BorderLayout());
        toolPanel.setOpaque(false);
        toolPanel.add(ViewUi.row(ViewUi.click("打开 GD", this::chooseFile)), BorderLayout.WEST);
        toolPanel.add(infoLabel, BorderLayout.CENTER);
        view.add(toolPanel, BorderLayout.NORTH);
        view.add(ViewUi.card("GD 文件数据", ViewUi.scroll(table)), BorderLayout.CENTER);
        view.add(pagedRows.createPaginationPanel(), BorderLayout.SOUTH);
    }

    /**
     * 弹出 GD 文件选择框并打开选中的文件。
     * 默认定位到 {@link #DEFAULT_GD_DIR}；目录不存在时回退 user.dir。
     */
    private void chooseFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("选择 GD 文件");
        // 每次打开都落到默认相对目录，不记忆上次选择
        chooser.setCurrentDirectory(resolveDefaultGdDir());
        chooser.setFileFilter(new javax.swing.filechooser.FileFilter() {
            @Override
            public boolean accept(File file) {
                return file.isDirectory() || Const.GD.equalsIgnoreCase(Utils.getFileExtension(file.getName()));
            }

            @Override
            public String getDescription() {
                return "GD 文件 (*.gd)";
            }
        });
        if (chooser.showOpenDialog(owner) == JFileChooser.APPROVE_OPTION) {
            openFileAsync(chooser.getSelectedFile(), null);
        }
    }

    /**
     * 解析「打开 GD」默认目录：相对 user.dir 的 DEFAULT_GD_DIR，不存在则回退 user.dir。
     *
     * @return 可用的默认目录
     */
    private static File resolveDefaultGdDir() {
        File gdDir = new File(DEFAULT_GD_DIR);
        if (gdDir.isDirectory()) {
            return gdDir;
        }
        return new File(System.getProperty("user.dir"));
    }

    /**
     * 按当前 GD 表头重建只读表格模型。
     */
    private void renderColumns() {
        String[] columns = data.header.columnNames.toArray(new String[0]);
        tableModel = new DefaultTableModel(new Object[0][], columns) {
            private static final long serialVersionUID = 1L;

            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        table.setModel(tableModel);
        for (int i = 0; i < table.getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(120);
        }
    }

    /**
     * 渲染当前页 GD 数据行。
     *
     * @param pageRows
     *            当前页行数据
     */
    private void renderPageRows(List<Object[]> pageRows) {
        tableModel.setRowCount(0);
        for (Object[] row : pageRows) {
            tableModel.addRow(row);
        }
    }
}
