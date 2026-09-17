package com.gamer.data.file.db;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

import com.gamer.data.ui.ViewUi;
import com.gamer.data.file.config.DbConfig;

/**
 * 数据库浏览 Module：表树、筛选分页、BLOB proto 展示。连接与 BLOB 规则只读自 {@link DbConfig}。
 */
public final class BrowseModule {

    /** 加载中占位 */
    private static final String LOADING = "正在加载...";

    /** 筛选运算符选项：展示标签 -> SQL 运算符 */
    private static final String[][] FILTER_OPS = {{"等于", "="}, {"不等于", "!="}, {"大于", ">"}, {"小于", "<"},
        {"大于等于", ">="}, {"小于等于", "<="}, {"包含", "LIKE"}, {"为空", "IS NULL"}, {"不为空", "IS NOT NULL"}};

    /** 父组件 */
    private final Component owner;

    /** 外挂 jar 加载器 */
    private final JarLoader jarLoader = new JarLoader();

    /** proto 解码器 */
    private final ProtoBlobDecoder decoder = new ProtoBlobDecoder();

    /** JDBC 客户端 */
    private final JdbcClient jdbcClient = new JdbcClient();

    /** 界面日志 */
    private final UiLog uiLog;

    /** 根面板 */
    private final JPanel view = new JPanel(new BorderLayout(6, 6));

    /** 连接与表树 */
    private final JTree browseTree = new JTree(new DefaultMutableTreeNode("数据库"));

    /** 数据表格 */
    private final JTable dataTable = new JTable();

    /** 状态说明 */
    private final JLabel statusLabel = new JLabel("双击连接展开表，双击表查看数据");

    /** 筛选：列 */
    private final JComboBox<String> filterColumnBox = new JComboBox<>();

    /** 筛选：运算符 */
    private final JComboBox<String> filterOpBox = new JComboBox<>();

    /** 筛选：值 */
    private final JTextField filterValueField = new JTextField(12);

    /** 分页说明 */
    private final JLabel pageLabel = new JLabel("第 - 页");

    /** 上一页 */
    private final JButton prevPageBtn = new JButton("上一页");

    /** 下一页 */
    private final JButton nextPageBtn = new JButton("下一页");

    /** BLOB 规则表格 */
    private final JTable ruleTable = new JTable();

    /** 当前连接 */
    private ConnectionConfig currentConfig;

    /** 当前表名 */
    private String currentTable;

    /** 当前页（从 1 开始） */
    private int currentPage = 1;

    /** 当前筛选 */
    private final QueryFilter currentFilter = new QueryFilter();

    /** 当前查询原始结果 */
    private JdbcClient.QueryResult currentQuery;

    /** 查询版本，避免旧表的异步结果覆盖当前表。 */
    private int queryVersion;

    /** 当前查询任务。 */
    private SwingWorker<JdbcClient.QueryResult, Void> queryWorker;

    /** 全部列名 */
    private List<String> allColumns = new ArrayList<>();

    /** 可见列名（空表示全部） */
    private List<String> visibleColumns = new ArrayList<>();

    /** 切换表后首 次渲染使用默认列宽。 */
    private boolean resetColumnWidths = true;

    /** jar 是否已初始化 */
    private boolean jarsReady;

    /**
     * @param owner
     *            父窗口
     */
    public BrowseModule(Component owner) {
        this.owner = owner;
        JTextArea logArea = new JTextArea(4, 80);
        logArea.setEditable(false);
        ViewUi.log(logArea);
        uiLog = new UiLog(logArea);
        buildView(logArea);
        initFilterOperators();
        reloadConnectionTree();
        reloadRuleTable();
        uiLog.info("数据库 Tab 就绪");
    }

    /**
     * @return Tab 面板
     */
    public JPanel getView() {
        return view;
    }

    /**
     * 组装界面布局。
     */
    private void buildView(JTextArea logArea) {
        ViewUi.statusBar(statusLabel);
        ViewUi.page(view);
        view.add(statusLabel, BorderLayout.NORTH);
        view.add(ViewUi.splitH(buildTreePanel(), buildDataPanel(logArea), 0.26), BorderLayout.CENTER);
    }

    /**
     * 初始化筛选运算符下拉。
     */
    private void initFilterOperators() {
        DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
        for (String[] op : FILTER_OPS) {
            model.addElement(op[0]);
        }
        filterOpBox.setModel(model);
        filterOpBox.addActionListener(e -> updateFilterValueEnabled());
        updateFilterValueEnabled();
    }

    /**
     * IS NULL 类运算符时禁用值输入框。
     */
    private void updateFilterValueEnabled() {
        String op = resolveOperatorSql();
        boolean needValue = !"IS NULL".equals(op) && !"IS NOT NULL".equals(op);
        filterValueField.setEnabled(needValue);
    }

    /**
     * 顶部工具栏。
     */
    private JPanel buildToolbar() {
        return ViewUi.row(ViewUi.click("测试连接", this::onTestConnection),
            ViewUi.click("关闭连接", this::onCloseConnection), ViewUi.click("重载 jar", this::onReloadProtoJar));
    }

    /**
     * 左侧连接/表树 + BLOB 规则。
     */
    private JPanel buildTreePanel() {
        ViewUi.tree(browseTree);
        browseTree.setRootVisible(true);
        browseTree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    onTreeDoubleClick();
                }
            }
        });

        ruleTable.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
        ViewUi.table(ruleTable);
        ViewUi.enableCellCopy(ruleTable);
        JScrollPane ruleScroll = ViewUi.scroll(ruleTable);
        ruleScroll.setPreferredSize(new Dimension(100, 132));
        JPanel rules = new JPanel(new BorderLayout(0, 4));
        rules.setOpaque(false);
        rules.add(ViewUi.hint("BLOB 规则（改 DbConfig.BLOB_RULES）"), BorderLayout.NORTH);
        rules.add(ruleScroll, BorderLayout.CENTER);

        JPanel body = new JPanel(new BorderLayout(4, 6));
        body.add(buildToolbar(), BorderLayout.NORTH);
        body.add(ViewUi.scroll(browseTree), BorderLayout.CENTER);
        body.add(rules, BorderLayout.SOUTH);
        return ViewUi.card("连接 / 表", body);
    }

    /**
     * 右侧数据区：筛选、表格、分页、日志。
     */
    private JPanel buildDataPanel(JTextArea logArea) {
        JScrollPane logScroll = ViewUi.scroll(logArea);
        logScroll.setPreferredSize(new Dimension(100, 72));
        JPanel south = new JPanel(new BorderLayout(0, 4));
        south.setOpaque(false);
        south.add(buildPaginationBar(), BorderLayout.NORTH);
        south.add(logScroll, BorderLayout.CENTER);

        JPanel body = new JPanel(new BorderLayout(4, 4));
        body.add(buildFilterBar(), BorderLayout.NORTH);
        body.add(buildTableArea(), BorderLayout.CENTER);
        body.add(south, BorderLayout.SOUTH);
        return ViewUi.card("数据", body);
    }

    /**
     * 筛选条件栏。
     */
    private JPanel buildFilterBar() {
        ViewUi.compactCombo(filterColumnBox);
        ViewUi.compactCombo(filterOpBox);
        ViewUi.compactField(filterValueField);
        JPanel filter = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        filter.setOpaque(false);
        filter.add(ViewUi.label("列"));
        filter.add(filterColumnBox);
        filter.add(ViewUi.label("运算符"));
        filter.add(filterOpBox);
        filter.add(ViewUi.label("值"));
        filter.add(filterValueField);
        filter.add(ViewUi.click("查询", this::onApplyFilter));
        filter.add(ViewUi.click("重置", this::onResetFilter));
        filter.add(ViewUi.click("选择列", this::onSelectColumns));
        return filter;
    }

    /**
     * 表格区域。
     */
    private JPanel buildTableArea() {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        dataTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        ViewUi.table(dataTable);
        ViewUi.enableCellCopy(dataTable);
        dataTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    onCellDoubleClick();
                }
            }
        });
        wrapper.add(ViewUi.scroll(dataTable), BorderLayout.CENTER);
        return wrapper;
    }

    /**
     * 分页栏。
     */
    private JPanel buildPaginationBar() {
        ViewUi.style(prevPageBtn);
        ViewUi.style(nextPageBtn);
        ViewUi.label(pageLabel);
        prevPageBtn.addActionListener(e -> changePage(currentPage - 1));
        nextPageBtn.addActionListener(e -> changePage(currentPage + 1));
        prevPageBtn.setEnabled(false);
        nextPageBtn.setEnabled(false);
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        panel.setOpaque(false);
        panel.add(prevPageBtn);
        panel.add(nextPageBtn);
        panel.add(pageLabel);
        return panel;
    }

    /**
     * 刷新连接树。
     */
    private void reloadConnectionTree() {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("数据库");
        for (ConnectionConfig conn : DbConfig.connections()) {
            root.add(new DefaultMutableTreeNode(new TreeConn(conn)));
        }
        browseTree.setModel(new DefaultTreeModel(root));
        browseTree.expandRow(0);
    }

    /**
     * 刷新 BLOB 规则表。
     */
    private void reloadRuleTable() {
        List<BlobParseRule> rules = DbConfig.blobRules();
        String[] cols = {"表", "列", "proto消息"};
        Object[][] rows = new Object[rules.size()][3];
        for (int i = 0; i < rules.size(); i++) {
            BlobParseRule rule = rules.get(i);
            rows[i][0] = rule.getTable();
            rows[i][1] = rule.getColumn();
            rows[i][2] = rule.getProtoMsg();
        }
        ruleTable.setModel(new DefaultTableModel(rows, cols) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        });
        if (ruleTable.getColumnCount() >= 3) {
            ruleTable.getColumnModel().getColumn(0).setPreferredWidth(108);
            ruleTable.getColumnModel().getColumn(1).setPreferredWidth(96);
            ruleTable.getColumnModel().getColumn(2).setPreferredWidth(180);
        }
    }

    /**
     * 加载 proto 相关 ClassLoader（common-proto + protobuf-java）。
     *
     * @return proto ClassLoader
     * @throws Exception
     *             加载失败
     */
    private ClassLoader loadProtoClassLoader() throws Exception {
        return jarLoader.getProtoLoader(DbConfig.PROTO_JAR, DbConfig.PROTOBUF_JAVA_JAR);
    }

    /**
     * 懒加载 jar。
     */
    private boolean ensureJars() {
        if (jarsReady) {
            return true;
        }
        uiLog.info("加载 jar: mysql=" + DbConfig.MYSQL_JAR);
        uiLog.info("加载 jar: proto=" + DbConfig.PROTO_JAR);
        uiLog.info("加载 jar: protobuf-java=" + DbConfig.PROTOBUF_JAVA_JAR);
        try {
            jarLoader.getMysqlLoader(DbConfig.MYSQL_JAR);
            ClassLoader protoLoader = loadProtoClassLoader();
            decoder.bindClassLoader(protoLoader);
            jarsReady = true;
            uiLog.info("jar 加载成功");
            return true;
        } catch (Exception e) {
            uiLog.error("jar 加载失败: " + e.getMessage());
            JOptionPane.showMessageDialog(owner,
                "加载 jar 失败:\nprotoJar=" + DbConfig.PROTO_JAR + "\nprotobufJavaJar="
                    + DbConfig.PROTOBUF_JAVA_JAR + "\nmysqlJar=" + DbConfig.MYSQL_JAR + "\n\n" + e.getMessage(),
                "错误", JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    /**
     * 树节点双击。
     */
    private void onTreeDoubleClick() {
        TreePath path = browseTree.getSelectionPath();
        if (path == null) {
            return;
        }
        Object last = ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject();
        if (last instanceof TreeConn) {
            DefaultMutableTreeNode connNode = (DefaultMutableTreeNode) path.getLastPathComponent();
            if (hasLoadedTables(connNode)) {
                browseTree.expandPath(path);
                return;
            }
            loadTablesForConnection(connNode, ((TreeConn) last).config);
        } else if (last instanceof TreeTable) {
            TreeTable ref = (TreeTable) last;
            openTable(ref.config, ref.tableName);
        }
    }

    /**
     * 连接节点是否已加载表列表。
     */
    private boolean hasLoadedTables(DefaultMutableTreeNode connNode) {
        if (connNode.getChildCount() == 0) {
            return false;
        }
        return ((DefaultMutableTreeNode) connNode.getChildAt(0)).getUserObject() instanceof TreeTable;
    }

    /**
     * 异步加载表名列表。
     */
    private void loadTablesForConnection(final DefaultMutableTreeNode connNode, final ConnectionConfig config) {
        if (!ensureJars()) {
            return;
        }
        connNode.removeAllChildren();
        connNode.add(new DefaultMutableTreeNode(LOADING));
        ((DefaultTreeModel) browseTree.getModel()).nodeStructureChanged(connNode);
        statusLabel.setText("正在连接 " + config.getAlias() + " ...");
        uiLog.info("连接 " + config.getAlias() + "，加载表列表...");
        new SwingWorker<List<String>, Void>() {
            @Override
            protected List<String> doInBackground() throws Exception {
                ClassLoader mysqlLoader = jarLoader.getMysqlLoader(DbConfig.MYSQL_JAR);
                return jdbcClient.listTables(config, mysqlLoader);
            }

            @Override
            protected void done() {
                try {
                    List<String> tables = get();
                    connNode.removeAllChildren();
                    for (String table : tables) {
                        connNode.add(new DefaultMutableTreeNode(new TreeTable(config, table)));
                    }
                    ((DefaultTreeModel) browseTree.getModel()).nodeStructureChanged(connNode);
                    browseTree.expandPath(new TreePath(connNode.getPath()));
                    statusLabel.setText(config.getAlias() + "：共 " + tables.size() + " 张表");
                    uiLog.info("表列表加载完成，共 " + tables.size() + " 张表");
                } catch (Exception e) {
                    connNode.removeAllChildren();
                    connNode.add(new DefaultMutableTreeNode("加载失败，双击重试"));
                    ((DefaultTreeModel) browseTree.getModel()).nodeStructureChanged(connNode);
                    statusLabel.setText("加载表失败");
                    uiLog.error("表列表加载失败: " + e.getMessage());
                    JOptionPane.showMessageDialog(owner, "加载表失败: " + e.getMessage(), "错误",
                        JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    /**
     * 打开表：重置筛选与分页，加载第 1 页。
     */
    private void openTable(ConnectionConfig config, String table) {
        cancelQuery();
        currentConfig = config;
        currentTable = table;
        currentPage = 1;
        currentQuery = null;
        allColumns = new ArrayList<>();
        visibleColumns = new ArrayList<>();
        resetColumnWidths = true;
        onResetFilterSilent();
        loadCurrentPage();
    }

    /**
     * 加载当前页数据。
     */
    private void loadCurrentPage() {
        if (currentConfig == null || currentTable == null) {
            return;
        }
        if (!ensureJars()) {
            return;
        }
        syncFilterFromUi();
        cancelQuery();
        final int page = currentPage;
        final int requestVersion = ++queryVersion;
        final String requestTable = currentTable;
        final ConnectionConfig requestConfig = currentConfig;
        final QueryFilter effectiveFilter = resolveEffectiveFilter();
        statusLabel.setText("正在读取 " + currentTable + " 第 " + page + " 页 ...");
        uiLog.info("查询 " + currentTable + " 第 " + page + " 页" + filterSummary(effectiveFilter));
        queryWorker = new SwingWorker<JdbcClient.QueryResult, Void>() {
            @Override
            protected JdbcClient.QueryResult doInBackground() throws Exception {
                ClassLoader mysqlLoader = jarLoader.getMysqlLoader(DbConfig.MYSQL_JAR);
                decoder.bindClassLoader(loadProtoClassLoader());
                int offset = (page - 1) * DbConfig.ROW_LIMIT;
                return jdbcClient.queryTablePage(requestConfig, mysqlLoader, requestTable, effectiveFilter, offset,
                    DbConfig.ROW_LIMIT);
            }

            @Override
            protected void done() {
                try {
                    JdbcClient.QueryResult result = get();
                    if (requestVersion != queryVersion || requestConfig != currentConfig
                        || !requestTable.equals(currentTable)) {
                        return;
                    }
                    currentQuery = result;
                    allColumns = new ArrayList<>(result.getColumns());
                    if (visibleColumns.isEmpty()) {
                        visibleColumns = new ArrayList<>(allColumns);
                    } else {
                        visibleColumns.retainAll(allColumns);
                        if (visibleColumns.isEmpty()) {
                            visibleColumns = new ArrayList<>(allColumns);
                        }
                    }
                    refreshFilterColumnBox();
                    renderQueryResult();
                    updatePagination(result.getTotalCount());
                    statusLabel.setText(currentTable + "：本页 " + result.getRows().size() + " 条，共 "
                        + result.getTotalCount() + " 条");
                    uiLog.info("查询完成，本页 " + result.getRows().size() + " 条，共 " + result.getTotalCount() + " 条");
                } catch (Exception e) {
                    if (isCancelled()) {
                        return;
                    }
                    statusLabel.setText("读取失败");
                    uiLog.error("查询失败: " + e.getMessage());
                    JOptionPane.showMessageDialog(owner, "读取表失败: " + e.getMessage(), "错误",
                        JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        queryWorker.execute();
    }

    /** 取消上一条未完成查询。 */
    private void cancelQuery() {
        if (queryWorker != null && !queryWorker.isDone()) {
            queryWorker.cancel(true);
        }
        queryWorker = null;
        queryVersion++;
    }

    /**
     * 复制筛选条件。
     */
    private QueryFilter copyFilter(QueryFilter src) {
        QueryFilter copy = new QueryFilter();
        copy.setColumn(src.getColumn());
        copy.setOperator(src.getOperator());
        copy.setValue(src.getValue());
        return copy;
    }

    /**
     * 解析实际生效的筛选：值为空时（非 IS NULL 类）视为无筛选。
     *
     * @return 生效条件，无筛选时 null
     */
    private QueryFilter resolveEffectiveFilter() {
        if (currentFilter.isEmpty()) {
            return null;
        }
        if (currentFilter.isNullOperator()) {
            return copyFilter(currentFilter);
        }
        String value = currentFilter.getValue();
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return copyFilter(currentFilter);
    }

    /**
     * 筛选摘要日志文本。
     */
    private String filterSummary(QueryFilter filter) {
        if (filter == null || filter.isEmpty()) {
            return "";
        }
        return " WHERE " + filter.getColumn() + " " + filter.getOperator()
            + (filter.isNullOperator() ? "" : " " + filter.getValue());
    }

    /**
     * 从 UI 同步筛选到 currentFilter。
     */
    private void syncFilterFromUi() {
        currentFilter.setColumn((String) filterColumnBox.getSelectedItem());
        currentFilter.setOperator(resolveOperatorSql());
        currentFilter.setValue(filterValueField.getText());
    }

    /**
     * 当前选中的运算符 SQL。
     */
    private String resolveOperatorSql() {
        int idx = filterOpBox.getSelectedIndex();
        if (idx < 0 || idx >= FILTER_OPS.length) {
            return "=";
        }
        return FILTER_OPS[idx][1];
    }

    /**
     * 刷新筛选列下拉。
     */
    private void refreshFilterColumnBox() {
        DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
        for (String col : allColumns) {
            model.addElement(col);
        }
        filterColumnBox.setModel(model);
    }

    /**
     * 渲染表格（按可见列）。
     */
    private void renderQueryResult() {
        if (currentQuery == null) {
            return;
        }
        List<String> showCols = resolveVisibleColumns();
        Map<String, Integer> widths = resetColumnWidths ? new HashMap<>() : captureColumnWidths();
        Object[][] rows = new Object[currentQuery.getRows().size()][showCols.size()];
        for (int r = 0; r < currentQuery.getRows().size(); r++) {
            JdbcClient.RowData row = currentQuery.getRows().get(r);
            for (int c = 0; c < showCols.size(); c++) {
                String col = showCols.get(c);
                Object raw = row.get(col);
                rows[r][c] = CellFormatter.formatForTable(raw, currentTable, col, decoder);
            }
        }
        dataTable.setModel(new DefaultTableModel(rows, showCols.toArray()) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        });
        restoreColumnWidths(widths);
        resetColumnWidths = false;
    }

    /** 记录当前表的列宽，仅在当前表生命周期内使用。 */
    private Map<String, Integer> captureColumnWidths() {
        Map<String, Integer> widths = new HashMap<>();
        for (int i = 0; i < dataTable.getColumnModel().getColumnCount(); i++) {
            javax.swing.table.TableColumn column = dataTable.getColumnModel().getColumn(i);
            widths.put(String.valueOf(column.getHeaderValue()), column.getWidth());
        }
        return widths;
    }

    /** 将当前表之前的列宽恢复到新模型。 */
    private void restoreColumnWidths(Map<String, Integer> widths) {
        for (int i = 0; i < dataTable.getColumnModel().getColumnCount(); i++) {
            javax.swing.table.TableColumn column = dataTable.getColumnModel().getColumn(i);
            Integer width = widths.get(String.valueOf(column.getHeaderValue()));
            if (width != null && width > 0) {
                column.setPreferredWidth(width);
                column.setWidth(width);
            }
        }
    }

    /**
     * 解析当前应展示的列（默认全部）。
     */
    private List<String> resolveVisibleColumns() {
        if (visibleColumns == null || visibleColumns.isEmpty()) {
            return new ArrayList<>(allColumns);
        }
        Set<String> selected = new HashSet<>(visibleColumns);
        List<String> show = new ArrayList<>();
        for (String col : allColumns) {
            if (selected.contains(col)) {
                show.add(col);
            }
        }
        return show.isEmpty() ? new ArrayList<>(allColumns) : show;
    }

    /**
     * 更新分页控件状态。
     */
    private void updatePagination(long totalCount) {
        int pageSize = DbConfig.ROW_LIMIT;
        int totalPages = totalCount <= 0 ? 1 : (int) ((totalCount + pageSize - 1) / pageSize);
        if (currentPage > totalPages) {
            currentPage = totalPages;
        }
        if (currentPage < 1) {
            currentPage = 1;
        }
        pageLabel.setText("第 " + currentPage + " / " + totalPages + " 页（共 " + totalCount + " 条）");
        prevPageBtn.setEnabled(currentPage > 1);
        nextPageBtn.setEnabled(currentPage < totalPages);
    }

    /**
     * 翻页。
     */
    private void changePage(int page) {
        if (page < 1 || currentTable == null) {
            return;
        }
        currentPage = page;
        loadCurrentPage();
    }

    /**
     * 应用筛选（回到第 1 页）。
     */
    private void onApplyFilter() {
        if (currentTable == null) {
            JOptionPane.showMessageDialog(owner, "请先双击打开一张表", "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        currentPage = 1;
        loadCurrentPage();
    }

    /**
     * 重置筛选并重新查询。
     */
    private void onResetFilter() {
        onResetFilterSilent();
        if (currentTable != null) {
            currentPage = 1;
            loadCurrentPage();
        }
    }

    /**
     * 仅清空筛选 UI，不触发查询。
     */
    private void onResetFilterSilent() {
        currentFilter.setColumn(null);
        currentFilter.setOperator(null);
        currentFilter.setValue(null);
        filterValueField.setText("");
        if (filterOpBox.getItemCount() > 0) {
            filterOpBox.setSelectedIndex(0);
        }
        updateFilterValueEnabled();
    }

    /**
     * 列勾选对话框。
     */
    private void onSelectColumns() {
        if (allColumns.isEmpty()) {
            JOptionPane.showMessageDialog(owner, "请先打开表加载数据", "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        List<String> selected =
            ColumnSelectDialog.show(owner, allColumns, visibleColumns.isEmpty() ? allColumns : visibleColumns);
        if (selected != null && !selected.isEmpty()) {
            visibleColumns = selected;
            renderQueryResult();
            uiLog.info("展示列: " + selected);
        }
    }

    /**
     * 双击单元格查看详情。
     */
    private void onCellDoubleClick() {
        if (currentQuery == null || currentTable == null) {
            return;
        }
        int row = dataTable.getSelectedRow();
        int col = dataTable.getSelectedColumn();
        if (row < 0 || col < 0 || row >= currentQuery.getRows().size()) {
            return;
        }
        List<String> showCols = resolveVisibleColumns();
        if (col >= showCols.size()) {
            return;
        }
        String column = showCols.get(col);
        Object raw = currentQuery.getRows().get(row).get(column);
        if (!(raw instanceof byte[])) {
            return;
        }
        String detail = CellFormatter.formatForDetail(raw, currentTable, column, decoder);
        FormDialogs.showDetailDialog(owner, currentTable + "." + column, detail);
    }

    /**
     * 关闭当前连接：收起表树并清空数据区。
     */
    private void onCloseConnection() {
        TreeConn ref = getSelectedConnection();
        if (ref == null) {
            JOptionPane.showMessageDialog(owner, "请先选中一个连接节点", "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        TreePath path = findConnectionPath(ref.config.getAlias());
        if (path != null) {
            DefaultMutableTreeNode connNode = (DefaultMutableTreeNode) path.getLastPathComponent();
            connNode.removeAllChildren();
            ((DefaultTreeModel) browseTree.getModel()).nodeStructureChanged(connNode);
            browseTree.collapsePath(path);
        }
        if (currentConfig != null && currentConfig.getAlias().equals(ref.config.getAlias())) {
            clearDataView();
        }
        uiLog.info("关闭连接: " + ref.config.getAlias());
        statusLabel.setText("已关闭连接 " + ref.config.getAlias());
    }

    /**
     * 按别名查找连接树路径。
     */
    private TreePath findConnectionPath(String alias) {
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) browseTree.getModel().getRoot();
        for (int i = 0; i < root.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) root.getChildAt(i);
            Object obj = child.getUserObject();
            if (obj instanceof TreeConn && ((TreeConn) obj).config.getAlias().equals(alias)) {
                return new TreePath(child.getPath());
            }
        }
        return null;
    }

    /**
     * 清空右侧数据区。
     */
    private void clearDataView() {
        queryVersion++;
        resetColumnWidths = true;
        currentConfig = null;
        currentTable = null;
        currentQuery = null;
        currentPage = 1;
        allColumns.clear();
        visibleColumns.clear();
        dataTable.setModel(new DefaultTableModel());
        filterColumnBox.setModel(new DefaultComboBoxModel<>());
        onResetFilterSilent();
        pageLabel.setText("第 - 页");
        prevPageBtn.setEnabled(false);
        nextPageBtn.setEnabled(false);
    }

    private void onTestConnection() {
        TreeConn ref = getSelectedConnection();
        if (ref == null) {
            JOptionPane.showMessageDialog(owner, "请先选中一个连接节点", "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        if (!ensureJars()) {
            return;
        }
        statusLabel.setText("测试连接中...");
        uiLog.info("测试连接: " + ref.config.getAlias());
        final ConnectionConfig config = ref.config;
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                ClassLoader mysqlLoader = jarLoader.getMysqlLoader(DbConfig.MYSQL_JAR);
                jdbcClient.testConnection(config, mysqlLoader);
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    statusLabel.setText(config.getAlias() + " 连接成功");
                    uiLog.info("连接成功: " + config.getAlias());
                    JOptionPane.showMessageDialog(owner, "连接成功", "提示", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception e) {
                    statusLabel.setText("连接失败");
                    uiLog.error("连接失败: " + e.getMessage());
                    JOptionPane.showMessageDialog(owner, "连接失败: " + e.getMessage(), "错误",
                        JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void onReloadProtoJar() {
        jarLoader.invalidateAll();
        jarsReady = false;
        uiLog.info("重载 proto / mysql jar...");
        if (ensureJars()) {
            JOptionPane.showMessageDialog(owner, "proto / mysql jar 已重载", "提示", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    /**
     * 获取当前选中的连接。
     */
    private TreeConn getSelectedConnection() {
        TreePath path = browseTree.getSelectionPath();
        while (path != null) {
            Object obj = ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject();
            if (obj instanceof TreeConn) {
                return (TreeConn) obj;
            }
            path = path.getParentPath();
        }
        return null;
    }

    /** 树节点：连接 */
    private static final class TreeConn {
        private final ConnectionConfig config;

        TreeConn(ConnectionConfig config) {
            this.config = config;
        }

        @Override
        public String toString() {
            return config.getAlias() + " (" + config.summary() + ")";
        }
    }

    /** 树节点：表 */
    private static final class TreeTable {
        private final ConnectionConfig config;
        private final String tableName;

        TreeTable(ConnectionConfig config, String tableName) {
            this.config = config;
            this.tableName = tableName;
        }

        @Override
        public String toString() {
            return tableName;
        }
    }
}
