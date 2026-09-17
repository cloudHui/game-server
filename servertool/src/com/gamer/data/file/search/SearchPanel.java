package com.gamer.data.file.search;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;

import com.gamer.data.excel.ui.ViewHtmlHighlightUtil;
import com.gamer.data.ui.ViewUi;
import com.gamer.data.file.utils.Utils;

/**
 * 文件名检索面板：搜索输入、结果表格、防抖扫描、点击分发与全局键盘追加。
 */
public final class SearchPanel {

    /** 搜索结果表格列定义 */
    private static final String[] RESULT_COLUMNS = {"名称", "类型", "相对路径"};

    /** 最大展示结果数量 */
    private final int maxResults;

    /** 输入防抖和单双击判定间隔（毫秒） */
    private final int debounceMillis;

    /** 搜索结果动作回调 */
    private final Listener listener;

    /** 目录扫描 */
    private final DirSearch dirSearch;

    /** 根面板 */
    private final JPanel view = new JPanel(new BorderLayout(5, 5));

    /** 当前检索目录说明 */
    private final JLabel scopeLabel = new JLabel("范围：请先选择目录");

    /** 关键词输入框 */
    private final JTextField searchField = new JTextField();

    /** 清空关键词按钮 */
    private final JButton clearButton = new JButton("清空");

    /** 搜索结果表格模型 */
    private final DefaultTableModel resultModel = createResultModel();

    /** 搜索结果表格 */
    private final JTable resultTable = new JTable(resultModel);

    /** 状态与结果数量说明 */
    private final JLabel statusLabel = new JLabel("请选择左侧目录后输入名称");

    /** 当前结果快照 */
    private final List<File> results = new ArrayList<>();

    /** 当前搜索范围目录 */
    private File scopeDirectory;

    /** 搜索防抖定时器 */
    private Timer searchTimer;

    /** 文件单双击判定定时器 */
    private Timer clickTimer;

    /** 等待判定单双击的文件 */
    private File pendingClickFile;

    /** 搜索代数，丢弃过期结果 */
    private int searchGeneration;

    /** 程序化清空输入框，避免文档监听重入 */
    private boolean clearingText;

    /** 搜索结果操作回调 */
    public interface Listener {
        void onFileSingleClick(File file);

        void onFileDoubleClick(File file);

        void onDirectoryDoubleClick(File directory);
    }

    /**
     * @param maxResults
     *            单次最多展示的结果数量
     * @param debounceMillis
     *            输入防抖与单双击判定间隔
     * @param listener
     *            结果操作回调
     */
    public SearchPanel(int maxResults, int debounceMillis, Listener listener) {
        this.maxResults = maxResults;
        this.debounceMillis = debounceMillis;
        this.listener = listener;
        this.dirSearch = new DirSearch(maxResults);
        buildView();
    }

    /**
     * @return 可直接加入主界面的搜索面板
     */
    public JPanel getView() {
        return view;
    }

    /**
     * @return 当前搜索范围目录，未设置时 null
     */
    public File getScope() {
        return scopeDirectory;
    }

    /**
     * @return 已选中目录且搜索框可用时 true
     */
    public boolean hasNotActiveScope() {
        return scopeDirectory == null || !searchField.isEnabled();
    }

    /**
     * @return 搜索框当前是否持有键盘焦点
     */
    public boolean isSearchFieldFocused() {
        return searchField.hasFocus();
    }

    /**
     * 判断指定控件是否为检索输入框。
     *
     * @param component
     *            待判断控件
     * @return 是检索输入框时 true
     */
    public boolean isSearchField(Component component) {
        return component == searchField;
    }

    /**
     * 全局键盘分发：追加一个检索字符并触发防抖扫描。
     *
     * @param ch
     *            用户输入字符
     * @return 成功追加时 true
     */
    public boolean appendSearchChar(char ch) {
        if (hasNotActiveScope() || Character.isISOControl(ch)) {
            return false;
        }
        searchField.requestFocusInWindow();
        searchField.replaceSelection(String.valueOf(ch));
        return true;
    }

    /**
     * 全局键盘分发：删除关键词末尾一个字符。
     *
     * @return 成功删除时 true
     */
    public boolean backspaceSearchChar() {
        if (hasNotActiveScope()) {
            return false;
        }
        String text = searchField.getText();
        if (text.isEmpty()) {
            return false;
        }
        searchField.requestFocusInWindow();
        searchField.setText(text.substring(0, text.length() - 1));
        return true;
    }

    /**
     * 全局键盘分发：清空当前关键词与结果，保留检索范围。
     *
     * @return 成功清空时 true
     */
    public boolean clearSearchQuery() {
        if (hasNotActiveScope()) {
            return false;
        }
        clearSearch();
        searchField.requestFocusInWindow();
        return true;
    }

    /**
     * 设置当前搜索范围，并清空旧关键词与结果；范围未变时保留关键词与结果，避免在同一目录内点选文件把检索清掉。
     *
     * @param directory
     *            新的搜索根目录
     */
    public void setScope(File directory) {
        if (scopeDirectory != null && scopeDirectory.getAbsolutePath().equals(directory.getAbsolutePath())) {
            return;
        }
        scopeDirectory = directory;
        scopeLabel.setText("范围：" + directory.getAbsolutePath());
        searchField.setEnabled(true);
        clearSearch();
        SwingUtilities.invokeLater(searchField::requestFocusInWindow);
    }

    /**
     * 清除当前范围和结果，恢复为等待选择目录状态。
     */
    public void clearScope() {
        scopeDirectory = null;
        searchGeneration++;
        stopSearchTimer();
        clearSearchText();
        searchField.setEnabled(false);
        clearButton.setEnabled(false);
        clearResults();
        scopeLabel.setText("范围：请先选择目录");
        statusLabel.setText("请选择左侧目录后输入名称");
    }

    /** 创建搜索输入、结果表和状态栏。 */
    private void buildView() {
        view.setOpaque(false);

        ViewUi.label(scopeLabel);
        ViewUi.compactField(searchField);
        searchField.setToolTipText("选中目录后可在页面任意位置输入；仅匹配当前目录及子目录中的名称");
        searchField.setEnabled(false);
        ViewUi.style(clearButton);
        clearButton.setEnabled(false);
        clearButton.addActionListener(e -> clearSearch());

        JPanel inputRow = new JPanel(new BorderLayout(6, 0));
        inputRow.setOpaque(false);
        inputRow.add(searchField, BorderLayout.CENTER);
        inputRow.add(clearButton, BorderLayout.EAST);

        JPanel inputPanel = new JPanel(new BorderLayout(4, 6));
        inputPanel.setOpaque(false);
        inputPanel.add(scopeLabel, BorderLayout.NORTH);
        inputPanel.add(inputRow, BorderLayout.CENTER);
        view.add(inputPanel, BorderLayout.NORTH);

        configureResultTable();
        view.add(ViewUi.scroll(resultTable), BorderLayout.CENTER);

        ViewUi.statusBar(statusLabel);
        view.add(statusLabel, BorderLayout.SOUTH);

        installSearchTextListener();
    }

    /** @return 只读结果表格模型 */
    private DefaultTableModel createResultModel() {
        return new DefaultTableModel(RESULT_COLUMNS, 0) {
            private static final long serialVersionUID = 1L;

            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }

    /** 设置搜索结果表格样式与点击监听。 */
    private void configureResultTable() {
        ViewUi.table(resultTable);
        resultTable.getColumnModel().getColumn(0).setPreferredWidth(180);
        resultTable.getColumnModel().getColumn(1).setPreferredWidth(70);
        resultTable.getColumnModel().getColumn(2).setPreferredWidth(360);
        resultTable.addMouseListener(createResultMouseListener());
    }

    /** 监听关键词变化，空关键词立即清空，非空关键词延迟扫描。 */
    private void installSearchTextListener() {
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                onSearchTextChanged();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                onSearchTextChanged();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                onSearchTextChanged();
            }
        });
    }

    /** @return 搜索结果鼠标监听器 */
    private MouseAdapter createResultMouseListener() {
        return new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int row = resultTable.rowAtPoint(e.getPoint());
                if (row < 0 || row >= results.size()) {
                    return;
                }
                resultTable.setRowSelectionInterval(row, row);
                File result = results.get(row);
                if (result.isDirectory()) {
                    if (e.getClickCount() == 2) {
                        listener.onDirectoryDoubleClick(result);
                    }
                    return;
                }
                scheduleFileClick(result);
            }
        };
    }

    /** 搜索输入变化后清空旧结果或启动防抖检索。 */
    private void onSearchTextChanged() {
        if (clearingText) {
            return;
        }
        String query = searchField.getText().trim();
        clearButton.setEnabled(!query.isEmpty());
        if (query.isEmpty()) {
            searchGeneration++;
            clearResults();
            statusLabel.setText(scopeDirectory == null ? "请选择左侧目录后输入名称" : "请输入目录名或文件名");
            return;
        }
        if (scopeDirectory != null) {
            scheduleSearch();
        }
    }

    /** 创建或重启搜索防抖定时器。 */
    private void scheduleSearch() {
        if (searchTimer == null) {
            searchTimer = new Timer(debounceMillis, e -> executeSearch());
            searchTimer.setRepeats(false);
        }
        searchTimer.restart();
    }

    /** 递归检索当前范围，只接受最新一代结果。 */
    private void executeSearch() {
        final File baseDirectory = scopeDirectory;
        final String query = searchField.getText().trim();
        if (baseDirectory == null || !baseDirectory.isDirectory() || query.isEmpty()) {
            return;
        }
        final int generation = ++searchGeneration;
        statusLabel.setText("正在检索...");
        dirSearch.searchAsync(baseDirectory, query, scanResult -> {
            if (!isCurrentSearch(generation, baseDirectory, query)) {
                return;
            }
            applySearchResults(baseDirectory, query, scanResult);
        });
    }

    /** 判断后台结果是否仍属于当前搜索条件。 */
    private boolean isCurrentSearch(int generation, File baseDirectory, String query) {
        return generation == searchGeneration && scopeDirectory != null
            && scopeDirectory.getAbsolutePath().equals(baseDirectory.getAbsolutePath())
            && searchField.getText().trim().equals(query);
    }

    /** 写入搜索结果表格，并在名称列高亮关键词。 */
    private void applySearchResults(File baseDirectory, String query, DirSearch.Result scanResult) {
        results.clear();
        results.addAll(scanResult.files);
        resultModel.setRowCount(0);
        for (File file : results) {
            String highlightedName = ViewHtmlHighlightUtil.highlightSubstringRedHtml(file.getName(), query);
            String type = file.isDirectory() ? "文件夹" : Utils.getFileExtension(file.getName()).toUpperCase();
            resultModel.addRow(new Object[] {highlightedName, type, getRelativePathForDisplay(baseDirectory, file)});
        }
        String suffix = scanResult.limitReached ? "，已达到 " + maxResults + " 条上限，请缩小关键词" : "";
        statusLabel.setText("共 " + results.size() + " 条" + suffix);
    }

    /** 清空关键词与当前结果，但保留搜索范围。 */
    private void clearSearch() {
        searchGeneration++;
        stopSearchTimer();
        clearSearchText();
        clearButton.setEnabled(false);
        clearResults();
        statusLabel.setText(scopeDirectory == null ? "请选择左侧目录后输入名称" : "请输入目录名或文件名");
    }

    /** 停止延迟搜索，避免旧任务在范围切换后触发。 */
    private void stopSearchTimer() {
        if (searchTimer != null) {
            searchTimer.stop();
        }
    }

    /** 程序化清空输入框。 */
    private void clearSearchText() {
        clearingText = true;
        try {
            searchField.setText("");
        } finally {
            clearingText = false;
        }
    }

    /** 清空结果缓存与表格行。 */
    private void clearResults() {
        results.clear();
        resultModel.setRowCount(0);
    }

    /** 文件单击/双击判定。 */
    private void scheduleFileClick(File file) {
        boolean sameFile = pendingClickFile != null && pendingClickFile.equals(file);
        if (clickTimer != null && clickTimer.isRunning() && sameFile) {
            clickTimer.stop();
            clickTimer = null;
            pendingClickFile = null;
            listener.onFileDoubleClick(file);
            return;
        }
        if (clickTimer != null) {
            clickTimer.stop();
        }
        pendingClickFile = file;
        clickTimer = new Timer(debounceMillis, e -> {
            clickTimer.stop();
            clickTimer = null;
            File clickedFile = pendingClickFile;
            pendingClickFile = null;
            listener.onFileSingleClick(clickedFile);
        });
        clickTimer.setRepeats(false);
        clickTimer.start();
    }

    /** 生成相对搜索根目录的展示路径。 */
    private String getRelativePathForDisplay(File baseDirectory, File file) {
        try {
            return baseDirectory.toPath().toAbsolutePath().normalize()
                .relativize(file.toPath().toAbsolutePath().normalize()).toString();
        } catch (Exception e) {
            return file.getAbsolutePath();
        }
    }
}
