package com.gamer.data.excel.browse;

import java.awt.BorderLayout;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDropEvent;
import java.io.File;
import java.util.List;

import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingWorker;

import com.gamer.data.excel.core.GdPagedTableSource;
import com.gamer.data.excel.ui.PagedPreviewPanel;
import com.gamer.data.excel.ui.PagedPreviewPanelMode;
import com.gamer.data.ui.ViewUi;

/**
 * 独立 GD 文件查看窗口：拖放/打开 .gd，流式分页预览。
 * 使用方：CodeTool {@link ExcelViewer} 菜单「打开GD文件」与拖放。
 */
public class GdViewerFrame extends JFrame {

    private static final long serialVersionUID = 1L;

    /** 当前 GD 文件 */
    private File currentFile;

    /** 流式预览面板 */
    private PagedPreviewPanel previewPanel;

    /**
     * 打开并流式加载 GD 文件。
     *
     * @param file
     *            GD 文件，可为 null（空窗体，等待用户打开）
     */
    public GdViewerFrame(File file) {
        this.currentFile = file;
        setTitle("GD文件查看 - " + (file != null ? file.getName() : ""));
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(900, 600);
        setLocationRelativeTo(null);

        previewPanel = new PagedPreviewPanel("GD文件数据", null, PagedPreviewPanelMode.WITH_TOTAL);

        JPanel main = new JPanel(new BorderLayout(8, 8));
        ViewUi.page(main);
        JPanel toolPanel = new JPanel(new BorderLayout());
        toolPanel.setOpaque(false);
        toolPanel.add(ViewUi.click("打开GD", this::selectGdFile), BorderLayout.WEST);
        main.add(toolPanel, BorderLayout.NORTH);
        main.add(previewPanel.createView(), BorderLayout.CENTER);
        setContentPane(main);

        setupDragAndDrop();
        if (file != null) {
            loadGdFileAsync(file);
        }
    }

    /**
     * 异步流式加载 GD 并绑定预览面板。
     *
     * @param file
     *            GD 文件
     */
    private void loadGdFileAsync(final File file) {
        SwingWorker<GdPagedTableSource, Void> worker = new SwingWorker<GdPagedTableSource, Void>() {
            @Override
            protected GdPagedTableSource doInBackground() throws Exception {
                return new GdPagedTableSource(file);
            }

            @Override
            protected void done() {
                try {
                    GdPagedTableSource src = get();
                    currentFile = file;
                    setTitle("GD文件查看 - " + file.getName());
                    previewPanel.bindSource(src.getColumnHeaders(), src);
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(GdViewerFrame.this, "读取GD文件失败: " + e.getMessage(), "错误",
                        JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    /**
     * 文件选择对话框打开 GD。
     */
    private void selectGdFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("选择GD文件");
        chooser.setFileFilter(new javax.swing.filechooser.FileFilter() {
            @Override
            public boolean accept(File f) {
                if (f.isDirectory()) {
                    return true;
                }
                return f.getName().toLowerCase().endsWith(".gd");
            }

            @Override
            public String getDescription() {
                return "GD文件 (*.gd)";
            }
        });
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            loadGdFileAsync(chooser.getSelectedFile());
        }
    }

    /**
     * 支持拖放 .gd 到窗口。
     */
    private void setupDragAndDrop() {
        new DropTarget(this, DnDConstants.ACTION_COPY_OR_MOVE, new DropTargetAdapter() {
            @Override
            public void drop(DropTargetDropEvent dtde) {
                try {
                    dtde.acceptDrop(DnDConstants.ACTION_COPY_OR_MOVE);
                    Transferable transferable = dtde.getTransferable();
                    if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                        List<File> files = (List<File>) transferable.getTransferData(DataFlavor.javaFileListFlavor);
                        if (!files.isEmpty()) {
                            File file = files.get(0);
                            if (file.getName().toLowerCase().endsWith(".gd")) {
                                loadGdFileAsync(file);
                                dtde.dropComplete(true);
                                return;
                            }
                        }
                    }
                    dtde.dropComplete(true);
                } catch (Exception e) {
                    dtde.dropComplete(false);
                    JOptionPane.showMessageDialog(GdViewerFrame.this, "拖放失败: " + e.getMessage(), "错误",
                        JOptionPane.ERROR_MESSAGE);
                }
            }
        });
    }
}
