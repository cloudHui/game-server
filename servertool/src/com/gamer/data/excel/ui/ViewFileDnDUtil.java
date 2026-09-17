package com.gamer.data.excel.ui;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetListener;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.TransferHandler;

/**
 * 单文件拖放通用工具（JTable 导出 + 多组件 COPY 接收）。
 */
public final class ViewFileDnDUtil {

    /**
     * 放下文件后的业务回调。
     */
    public interface DroppedFileCallback {
        /**
         * @param file
         *            拖入的文件
         */
        void onFileDropped(File file);
    }

    /**
     * 拖放失败日志回调。
     */
    public interface DropErrorCallback {
        /**
         * @param message
         *            错误信息
         */
        void onDropError(String message);
    }

    /**
     * 根据 JList 选中项解析源文件。
     */
    public interface SelectedFileResolver {
        /**
         * @param selectedValue
         *            列表选中值
         * @return 源文件，无法拖动时返回 null
         */
        File resolveSelectedFile(Object selectedValue);
    }

    private ViewFileDnDUtil() {
    }

    /**
     * 从拖放数据中提取第一个文件。
     *
     * @param data
     *            Transferable 数据
     * @return 第一个 File，无效时 null
     */
    private static File extractFirstDroppedFile(Object data) {
        if (!(data instanceof List)) {
            return null;
        }
        List<?> fileListData = (List<?>)data;
        if (fileListData.isEmpty()) {
            return null;
        }
        Object first = fileListData.get(0);
        if (!(first instanceof File)) {
            return null;
        }
        return (File)first;
    }

    /**
     * 为多个组件安装 COPY 文件拖放目标。
     *
     * @param onDropped
     *            放下回调
     * @param onError
     *            错误回调
     * @param targets
     *            接收区组件
     */
    public static void installCopyDropTarget(DroppedFileCallback onDropped, DropErrorCallback onError,
        JComponent... targets) {
        DropTargetListener listener = createFileListDropListener(onDropped, onError);
        for (JComponent target : targets) {
            if (target == null) {
                continue;
            }
            new DropTarget(target, DnDConstants.ACTION_COPY, listener);
        }
    }

    /**
     * 创建接受 javaFileListFlavor 的 DropTargetListener。
     *
     * @param onDropped
     *            放下回调
     * @param onError
     *            错误回调
     * @return DropTargetListener
     */
    private static DropTargetListener createFileListDropListener(final DroppedFileCallback onDropped,
        final DropErrorCallback onError) {
        return new DropTargetAdapter() {
            @Override
            public void dragEnter(DropTargetDragEvent dtde) {
                acceptFileListDrag(dtde);
            }

            @Override
            public void dragOver(DropTargetDragEvent dtde) {
                acceptFileListDrag(dtde);
            }

            @Override
            public void drop(DropTargetDropEvent dtde) {
                handleFileListDrop(dtde, onDropped, onError);
            }
        };
    }

    /**
     * 拖动经过时接受文件列表风味。
     *
     * @param dtde
     *            拖动事件
     */
    private static void acceptFileListDrag(DropTargetDragEvent dtde) {
        if (dtde.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
            dtde.acceptDrag(DnDConstants.ACTION_COPY);
        } else {
            dtde.rejectDrag();
        }
    }

    /**
     * 处理文件放下。
     *
     * @param dtde
     *            放下事件
     * @param onDropped
     *            业务回调
     * @param onError
     *            错误回调
     */
    private static void handleFileListDrop(DropTargetDropEvent dtde, DroppedFileCallback onDropped,
        DropErrorCallback onError) {
        try {
            if (!dtde.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                dtde.rejectDrop();
                return;
            }
            dtde.acceptDrop(DnDConstants.ACTION_COPY);
            Object data = dtde.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
            File source = extractFirstDroppedFile(data);
            if (source == null) {
                dtde.dropComplete(false);
                return;
            }
            if (onDropped != null) {
                onDropped.onFileDropped(source);
            }
            dtde.dropComplete(true);
        } catch (Exception ex) {
            if (onError != null) {
                onError.onDropError("拖放复制失败: " + ex.getMessage());
            }
            dtde.dropComplete(false);
        }
    }

    /**
     * 创建 JTable 拖出单文件的 TransferHandler（选中行第 0 列为文件名）。
     *
     * @param resolver
     *            按文件名解析源文件
     * @return TransferHandler
     */
    public static TransferHandler createJTableFileExportHandler(final SelectedFileResolver resolver) {
        return new TransferHandler() {
            @Override
            public int getSourceActions(JComponent c) {
                return COPY;
            }

            @Override
            protected Transferable createTransferable(JComponent c) {
                if (!(c instanceof JTable) || resolver == null) {
                    return null;
                }
                JTable table = (JTable)c;
                int row = table.getSelectedRow();
                if (row < 0) {
                    return null;
                }
                Object value = table.getValueAt(row, 0);
                if (value == null) {
                    return null;
                }
                File source = resolver.resolveSelectedFile(value);
                if (source == null) {
                    return null;
                }
                return new SingleFileTransferable(source);
            }
        };
    }

    /**
     * 单文件 javaFileListFlavor Transferable。
     */
    private static final class SingleFileTransferable implements Transferable {

        private static final DataFlavor FILE_LIST_FLAVOR = DataFlavor.javaFileListFlavor;
        private final File file;

        private SingleFileTransferable(File file) {
            this.file = file;
        }

        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[] {FILE_LIST_FLAVOR};
        }

        @Override
        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return FILE_LIST_FLAVOR.equals(flavor);
        }

        @Override
        public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
            if (!isDataFlavorSupported(flavor)) {
                throw new UnsupportedFlavorException(flavor);
            }
            List<File> files = new ArrayList<>();
            files.add(file);
            return files;
        }
    }
}
