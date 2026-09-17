package com.gamer.data.file.module;

import java.awt.BorderLayout;
import java.awt.Font;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import javax.swing.JPanel;
import javax.swing.JTextArea;

import com.gamer.data.ui.ViewUi;
import com.gamer.data.file.utils.Utils;

/**
 * 文本文件预览 Module：封装读取、分页、元信息和只读文本渲染。
 */
public final class FilePreviewModule {

    /** 默认每页行数。 */
    private static final int PAGE_SIZE = 50;

    /** 文件行分页内容 Module，统一处理页码与每页数量变化。 */
    private final PagedContentModule<String> pagedContent;

    /** 文本展示区。 */
    private final JTextArea textArea = new JTextArea();

    /** 根面板。 */
    private final JPanel view = new JPanel(new BorderLayout());

    /** 当前文件。 */
    private File file;

    /** 当前文件扩展名。 */
    private String fileType;

    public FilePreviewModule() {
        pagedContent = new PagedContentModule<>(PAGE_SIZE, this::renderTextPage);
        buildView();
    }

    /**
     * 读取文件并返回可复用的预览面板。
     *
     * @param target
     *            文本文件
     * @return 预览面板
     * @throws IOException
     *             文件读取失败
     */
    public JPanel open(File target) throws IOException {
        file = target;
        fileType = Utils.getFileExtension(target.getName());
        pagedContent.setItems(Files.readAllLines(target.toPath(), StandardCharsets.UTF_8));
        return view;
    }

    /**
     * 创建只读文本区和分页栏。
     */
    private void buildView() {
        ViewUi.page(view);
        ViewUi.log(textArea);
        textArea.setFont(new Font("等线", Font.PLAIN, 12));
        textArea.setEditable(false);
        textArea.setLineWrap(false);
        textArea.setTabSize(4);
        view.add(ViewUi.card("文件内容", ViewUi.scroll(textArea)), BorderLayout.CENTER);
        view.add(pagedContent.createPaginationPanel(), BorderLayout.SOUTH);
    }

    /**
     * 展示目标页的文件元信息和带行号内容。
     *
     * @param pageLines
     *            当前页文本行
     */
    private void renderTextPage(List<String> pageLines) {
        if (file == null) {
            return;
        }
        StringBuilder content = new StringBuilder();
        appendFileHeader(content);

        // 当前页只遍历分页 Module 返回的切片，行号通过页偏移计算。
        int startIndex = (pagedContent.getCurrentPage() - 1) * pagedContent.getPageSize();
        for (int i = 0; i < pageLines.size(); i++) {
            content.append(String.format("%6d: ", startIndex + i + 1)).append(pageLines.get(i)).append('\n');
        }
        textArea.setText(content.toString());
        textArea.setCaretPosition(0);
    }

    /**
     * 写入当前文件的固定元信息头。
     *
     * @param content
     *            输出缓冲区
     */
    private void appendFileHeader(StringBuilder content) {
        content.append("文件: ").append(file.getAbsolutePath()).append('\n');
        content.append("类型: ").append(fileType.toUpperCase()).append('\n');
        content.append("大小: ").append(Utils.formatFileSize(file.length())).append('\n');
        content.append("修改时间: ")
            .append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(file.lastModified()))).append('\n');
        content.append("行数: ").append(pagedContent.getTotalItems()).append('\n');
        content.append("当前页: ").append(pagedContent.getCurrentPage()).append('/').append(pagedContent.getTotalPages())
            .append("\n\n");
    }
}
