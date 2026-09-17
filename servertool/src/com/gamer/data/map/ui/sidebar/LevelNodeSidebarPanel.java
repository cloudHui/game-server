package com.gamer.data.map.ui.sidebar;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.gamer.data.ui.ViewUi;
import com.gamer.data.map.level.LevelDocument;
import com.gamer.data.map.level.LevelNodeBean;
import com.gamer.data.map.level.LevelNodeEditModule;
import com.gamer.data.map.level.NodeType;
import com.gamer.data.map.ui.MapUiUtils;
import com.gamer.data.map.ui.MapViewerSession;

/**
 * 关卡节点列表、筛选与编辑侧栏。
 */
public final class LevelNodeSidebarPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    /** Type 筛选哨兵：为 -2（或输入框留空）表示不按类型筛选 */
    public static final int LEVEL_NODE_FILTER_TYPE_ANY = -2;

    /**
     * 节点选中与数据变更通知。
     */
    public interface LevelNodeListener {

        void onLevelNodeSelected(LevelNodeBean node);

        void onLevelNodesChanged();

        void onLevelNodeDeleted();

        void onStatusMessage(String message);
    }

    private final MapViewerSession session;
    private final boolean edit;
    private final LevelNodeListener listener;
    private final java.awt.Component dialogParent;

    private DefaultListModel<String> levelNodeListModel;
    private JList<String> levelNodeList;
    private JLabel levelNodeCountLabel;
    private JTextField levelNodeFilterTypeField;
    private JTextField levelNodeFilterBelongField;

    /** 与列表展示顺序一致，受筛选影响 */
    private final List<LevelNodeBean> displayedLevelNodes = new ArrayList<>();

    /** 避免地图点选与列表 selection 重复触发 */
    private boolean syncingNodeListFromMap;

    /**
     * @param session
     *            共享会话
     * @param edit
     *            是否可编辑
     * @param dialogParent
     *            对话框父组件
     * @param listener
     *            回调
     */
    public LevelNodeSidebarPanel(MapViewerSession session, boolean edit, java.awt.Component dialogParent,
        LevelNodeListener listener) {
        this.session = session;
        this.edit = edit;
        this.dialogParent = dialogParent;
        this.listener = listener;
        buildUi();
    }

    /**
     * 按当前筛选条件刷新节点列表。
     */
    public void refreshLevelNodeList() {
        levelNodeListModel.clear();
        displayedLevelNodes.clear();
        if (session.currentLevelNodes == null) {
            updateLevelNodeCount();
            return;
        }
        String typeS = levelNodeFilterTypeField != null ? levelNodeFilterTypeField.getText() : "";
        String belongS = levelNodeFilterBelongField != null ? levelNodeFilterBelongField.getText() : "";
        Integer ft = parseLevelNodeFilterInt(typeS);
        Integer fb = parseLevelNodeFilterInt(belongS);
        for (LevelNodeBean node : session.currentLevelNodes) {
            if (!matchesLevelNodeFilter(node, ft, fb)) {
                continue;
            }
            displayedLevelNodes.add(node);
            levelNodeListModel.addElement(node.toString());
        }
        updateLevelNodeCount();
    }

    /**
     * 地图或列表选中节点后同步列表高亮。
     */
    public void syncNodeListSelection(LevelNodeBean target) {
        if (target == null || displayedLevelNodes.isEmpty()) {
            return;
        }
        for (int i = 0; i < displayedLevelNodes.size(); i++) {
            LevelNodeBean node = displayedLevelNodes.get(i);
            if (node == target || (node.getDataId() == target.getDataId() && node.getX() == target.getX()
                && node.getZ() == target.getZ())) {
                syncingNodeListFromMap = true;
                try {
                    levelNodeList.setSelectedIndex(i);
                    levelNodeList.ensureIndexIsVisible(i);
                } finally {
                    syncingNodeListFromMap = false;
                }
                return;
            }
        }
    }

    /**
     * 在地图右键菜单中追加节点编辑项。
     */
    public void appendNodeEditMenuItems(JPopupMenu menu, LevelNodeBean hit) {
        appendNodeEditDeleteMenuItems(menu, hit);
    }

    /**
     * 新建节点对话框。
     */
    public void showNewNodeDialog(int worldX, int worldZ) {
        List<LevelNodeBean> beans = session.currentLevelDoc.getFlatBeans();
        List<ObjectNode> jsons = session.currentLevelDoc.getFlatJson();

        if (beans.isEmpty() || jsons.isEmpty()) {
            JOptionPane.showMessageDialog(dialogParent, "关卡文件无节点，无法选择父节点。");
            return;
        }

        JPanel panel = new JPanel(new GridLayout(0, 2, 6, 4));
        final JComboBox<String> parentCombo = createParentComboBox(beans);
        final JSpinner insertSpin = new JSpinner(new SpinnerNumberModel(0, 0, 0, 1));
        final JComboBox<LevelNodeTypeItem> typeCombo = createTypeComboBox();

        parentCombo.addActionListener(e -> {
            int pi = parentCombo.getSelectedIndex();
            if (pi >= 0 && pi < jsons.size()) {
                int cnt = LevelDocument.getChildCount(jsons.get(pi));
                insertSpin.setModel(new SpinnerNumberModel(cnt, 0, cnt, 1));
            }
        });

        int initialCnt = LevelDocument.getChildCount(jsons.get(0));
        insertSpin.setModel(new SpinnerNumberModel(initialCnt, 0, initialCnt, 1));
        parentCombo.setSelectedIndex(0);

        panel.add(new JLabel("父节点（从属关系）"));
        panel.add(parentCombo);
        panel.add(new JLabel("插入子位置(0=最前,末尾=追加)"));
        panel.add(insertSpin);
        panel.add(new JLabel("节点类型"));
        panel.add(typeCombo);

        int result = JOptionPane.showConfirmDialog(dialogParent, new JScrollPane(panel), "新建关卡节点",
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) {
            return;
        }

        processNodeCreation(worldX, worldZ, parentCombo.getSelectedIndex(), (LevelNodeTypeItem)typeCombo.getSelectedItem(),
            ((Number)insertSpin.getValue()).intValue(), jsons);
    }

    /**
     * 列表右键菜单。
     */
    private void maybeShowLevelNodeListPopup(MouseEvent e) {
        if (!e.isPopupTrigger() || session.currentLevelDoc == null) {
            return;
        }
        int idx = levelNodeList.locationToIndex(e.getPoint());
        if (idx < 0 || idx >= levelNodeListModel.size()) {
            return;
        }
        Rectangle cell = levelNodeList.getCellBounds(idx, idx);
        if (cell == null || !cell.contains(e.getPoint())) {
            return;
        }
        levelNodeList.setSelectedIndex(idx);
        if (idx >= displayedLevelNodes.size()) {
            return;
        }
        LevelNodeBean bean = displayedLevelNodes.get(idx);
        JPopupMenu pm = new JPopupMenu();
        appendNodeEditDeleteMenuItems(pm, bean);
        pm.show(levelNodeList, e.getX(), e.getY());
    }

    private void buildUi() {
        setLayout(new BorderLayout(5, 5));
        ViewUi.titled(this, "关卡节点");
        setOpaque(true);
        setBackground(ViewUi.CARD);

        levelNodeCountLabel = new JLabel("总计: 0个节点");
        JPanel topPanel = new JPanel(new BorderLayout(0, 2));
        topPanel.add(levelNodeCountLabel, BorderLayout.NORTH);
        topPanel.add(createFilterBar(), BorderLayout.CENTER);
        add(topPanel, BorderLayout.NORTH);

        levelNodeListModel = new DefaultListModel<>();
        levelNodeList = new JList<>(levelNodeListModel);
        levelNodeList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        ViewUi.list(levelNodeList);
        levelNodeList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                onLevelNodeSelectedFromList();
            }
        });
        if (edit) {
            levelNodeList.addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    maybeShowLevelNodeListPopup(e);
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    maybeShowLevelNodeListPopup(e);
                }
            });
        }
        add(ViewUi.scroll(levelNodeList), BorderLayout.CENTER);
        setPreferredSize(new Dimension(260, 220));
        setMinimumSize(new Dimension(200, 160));
    }

    private JPanel createFilterBar() {
        levelNodeFilterTypeField = new JTextField(String.valueOf(LEVEL_NODE_FILTER_TYPE_ANY), 4);
        levelNodeFilterBelongField = new JTextField(String.valueOf(LEVEL_NODE_FILTER_TYPE_ANY), 4);
        MapUiUtils.addSelectAllOnFocus(levelNodeFilterTypeField);
        MapUiUtils.addSelectAllOnFocus(levelNodeFilterBelongField);

        JButton filterBtn = ViewUi.style(new JButton("筛选"));
        JButton clearBtn = ViewUi.style(new JButton("清除"));
        MapUiUtils.compactButton(filterBtn);
        MapUiUtils.compactButton(clearBtn);
        filterBtn.addActionListener(e -> refreshLevelNodeList());
        clearBtn.addActionListener(e -> {
            levelNodeFilterTypeField.setText(String.valueOf(LEVEL_NODE_FILTER_TYPE_ANY));
            levelNodeFilterBelongField.setText(String.valueOf(LEVEL_NODE_FILTER_TYPE_ANY));
            refreshLevelNodeList();
        });

        // 固定两行，避免窄侧栏时 FlowLayout 折行后被列表挤没
        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        row1.add(new JLabel("Type:"));
        row1.add(levelNodeFilterTypeField);
        row1.add(new JLabel("Belong:"));
        row1.add(levelNodeFilterBelongField);

        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        row2.add(filterBtn);
        row2.add(clearBtn);

        JPanel filterPanel = new JPanel(new GridLayout(2, 1, 0, 2));
        filterPanel.add(row1);
        filterPanel.add(row2);
        return filterPanel;
    }

    private void onLevelNodeSelectedFromList() {
        if (syncingNodeListFromMap) {
            return;
        }
        int idx = levelNodeList.getSelectedIndex();
        if (idx < 0 || idx >= displayedLevelNodes.size()) {
            if (session.canvas != null) {
                session.canvas.setSelectedLevelNode(null);
            }
            return;
        }
        listener.onLevelNodeSelected(displayedLevelNodes.get(idx));
    }

    private void appendNodeEditDeleteMenuItems(JPopupMenu menu, final LevelNodeBean hit) {
        JMenuItem menuItem = new JMenuItem("编辑节点...");
        menuItem.addActionListener(e -> showEditNodeDialog(hit));
        menu.add(menuItem);

        menuItem = new JMenuItem("删除节点...");
        menuItem.addActionListener(e -> confirmAndDeleteFlatIndex(hit));
        menu.add(menuItem);

        menuItem = new JMenuItem("展示节点...");
        menuItem.addActionListener(e -> showNodeDetailDialog(hit));
        menu.add(menuItem);
    }

    private void showNodeDetailDialog(LevelNodeBean node) {
        if (node == null) {
            JOptionPane.showMessageDialog(dialogParent, "节点为空。");
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Type: ").append(node.getType()).append("\n");
        sb.append("Index: ").append(node.getIndexId()).append("\n");
        sb.append("----------------------\n");
        sb.append("Position:\n");
        String posJson = String.format("{\"x\": %d, \"z\": %d}", node.getX(), node.getZ());
        sb.append(parseAndFormatJson(posJson));
        sb.append("\nParam:\n");
        sb.append(parseAndFormatJson(node.getParamRaw()));

        JTextArea textArea = new JTextArea(sb.toString());
        textArea.setEditable(false);
        textArea.setFont(new Font("Consolas", Font.PLAIN, 13));
        textArea.setBackground(new Color(245, 245, 245));
        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setPreferredSize(new Dimension(400, 350));
        JOptionPane.showMessageDialog(dialogParent, scrollPane, "节点详细信息", JOptionPane.INFORMATION_MESSAGE);
    }

    private String parseAndFormatJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return "  (无)\n";
        }
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);
            mapper.configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true);
            Object obj = mapper.readValue(json, Object.class);
            String prettyJson = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
            return "  " + prettyJson.replace("\n", "\n  ") + "\n";
        } catch (Exception ex) {
            return "  " + json.replace("\n", "\n  ") + "\n";
        }
    }

    private void confirmAndDeleteFlatIndex(LevelNodeBean hit) {
        int flatIdx = findFlatIndex(hit);
        if (flatIdx < 0) {
            JOptionPane.showMessageDialog(dialogParent, "无法在文件序列中定位该节点。");
            return;
        }
        if (session.currentLevelDoc == null) {
            return;
        }
        int r = JOptionPane.showConfirmDialog(dialogParent, "确定删除该节点及其子树？将立即写回关卡文件，且不可撤销。", "删除节点",
            JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (r != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            session.currentLevelDoc.saveRemoveNode(flatIdx);
            session.currentLevelNodes = session.currentLevelDoc.getFlatBeans();
            session.canvas.setLevelNodes(session.currentLevelNodes);
            session.canvas.setSelectedLevelNode(null);
            refreshLevelNodeList();
            listener.onLevelNodesChanged();
            listener.onLevelNodeDeleted();
        } catch (Exception ex) {
            listener.onStatusMessage(ex.getMessage());
            JOptionPane.showMessageDialog(dialogParent, "删除失败: " + ex.getMessage());
        }
    }

    private void showEditNodeDialog(LevelNodeBean hit) {
        int idx = findFlatIndex(hit);
        if (idx < 0) {
            JOptionPane.showMessageDialog(dialogParent, "无法在文件序列中定位该节点。");
            return;
        }

        JTextField xField = new JTextField(String.valueOf(hit.getX()), 8);
        JTextField zField = new JTextField(String.valueOf(hit.getZ()), 8);
        JComboBox<LevelNodeTypeItem> typeCombo = createTypeComboBox(hit.getType());
        JTextField dataIdTf = new JTextField(String.valueOf(hit.getDataId()), 8);
        JTextField belongIdTf = new JTextField(String.valueOf(hit.getBelongId()), 8);
        JTextField nextTf = new JTextField(String.valueOf(hit.getNextDataId()), 8);

        JPanel panel = assembleEditPanel(xField, zField, typeCombo, dataIdTf, belongIdTf, nextTf);
        int r = JOptionPane.showConfirmDialog(dialogParent, new JScrollPane(panel), "编辑关卡节点",
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (r != JOptionPane.OK_OPTION) {
            return;
        }
        executeNodeSave(idx, hit, xField, zField, typeCombo, dataIdTf, belongIdTf, nextTf);
    }

    private JComboBox<LevelNodeTypeItem> createTypeComboBox(int currentType) {
        JComboBox<LevelNodeTypeItem> typeCombo = new JComboBox<>();
        for (NodeType nt : NodeType.values()) {
            typeCombo.addItem(new LevelNodeTypeItem(nt.getId(), nt.getName()));
        }
        selectTypeCombo(typeCombo, currentType);
        return typeCombo;
    }

    private JComboBox<LevelNodeTypeItem> createTypeComboBox() {
        JComboBox<LevelNodeTypeItem> combo = new JComboBox<>();
        for (NodeType nt : NodeType.values()) {
            combo.addItem(new LevelNodeTypeItem(nt.getId(), nt.getName()));
        }
        return combo;
    }

    private static void selectTypeCombo(JComboBox<LevelNodeTypeItem> combo, int typeId) {
        int n = combo.getItemCount();
        for (int i = 0; i < n; i++) {
            LevelNodeTypeItem it = combo.getItemAt(i);
            if (it != null && it.id == typeId) {
                combo.setSelectedIndex(i);
                return;
            }
        }
    }

    private static JPanel assembleEditPanel(JTextField x, JTextField z, JComboBox<LevelNodeTypeItem> type,
        JTextField dataId, JTextField belongId, JTextField next) {
        JPanel panel = new JPanel(new GridLayout(0, 2, 6, 4));
        panel.add(new JLabel("地图 X"));
        panel.add(x);
        panel.add(new JLabel("地图 Z"));
        panel.add(z);
        panel.add(new JLabel("类型"));
        panel.add(type);
        panel.add(new JLabel("DataId"));
        panel.add(dataId);
        panel.add(new JLabel("BelongId"));
        panel.add(belongId);
        panel.add(new JLabel("NextDataId"));
        panel.add(next);
        return panel;
    }

    private void executeNodeSave(int idx, LevelNodeBean hit, JTextField xField, JTextField zField,
        JComboBox<LevelNodeTypeItem> typeCombo, JTextField dataIdTf, JTextField belongIdTf, JTextField nextTf) {
        LevelNodeBean beforeSnap = session.currentLevelDoc.copyBeanAt(idx);
        int newX = MapUiUtils.parseInt(xField.getText());
        int newZ = MapUiUtils.parseInt(zField.getText());
        LevelNodeTypeItem ti = (LevelNodeTypeItem)typeCombo.getSelectedItem();
        int newType = ti != null ? ti.id : hit.getType();
        int newDataId = MapUiUtils.parseInt(dataIdTf.getText());
        int newBelongId = MapUiUtils.parseInt(belongIdTf.getText());
        int newNextId = MapUiUtils.parseInt(nextTf.getText());

        LevelNodeEditModule.Values values = new LevelNodeEditModule.Values(newX, newZ, newType, newDataId,
            newBelongId, newNextId, ti == null ? "" : ti.name);
        String changes = LevelNodeEditModule.buildDiff(beforeSnap, values);
        if (changes.isEmpty()) {
            return;
        }
        String diffMsg = "检测到以下变更，是否确认保存？\n\n" + changes;
        int confirm = JOptionPane.showConfirmDialog(dialogParent, diffMsg, "确认修改", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }
        LevelNodeEditModule.apply(hit, values);
        try {
            session.currentLevelDoc.syncBeanToJson(idx, hit);
            session.currentLevelDoc.savePartialNodeEdit(idx, beforeSnap, hit);
            session.currentLevelNodes = session.currentLevelDoc.getFlatBeans();
            session.canvas.setLevelNodes(session.currentLevelNodes);
            refreshLevelNodeList();
            session.canvas.repaint();
            listener.onLevelNodesChanged();
        } catch (Exception ex) {
            listener.onStatusMessage(ex.getMessage());
            JOptionPane.showMessageDialog(dialogParent, "保存失败: " + ex.getMessage());
        }
    }

    private void processNodeCreation(int worldX, int worldZ, int pi, LevelNodeTypeItem selType, int insertIdx,
        List<ObjectNode> jsons) {
        if (selType == null || pi < 0) {
            return;
        }
        ObjectNode parent = jsons.get(pi);
        int typeId = selType.id;
        int newDataId = session.currentLevelDoc.findMaxDataId() + 1;
        int belongId = parent.path("DataId").asInt(1);

        LevelNodeBean bean = new LevelNodeBean(worldX, worldZ, typeId, newDataId, belongId, 0, 0, 0, "");
        session.currentLevelDoc.fillBeanFromSameBelongId(bean, belongId);

        ObjectNode template;
        JsonNode chNode = parent.get("children");
        if (chNode != null && chNode.isArray() && !chNode.isEmpty()) {
            template = (ObjectNode)chNode.get(0);
        } else {
            template = parent;
        }

        ObjectNode newJson = session.currentLevelDoc.createDefaultChildNode(bean, template);
        try {
            session.currentLevelDoc.saveInsertChild(pi, insertIdx, newJson, dialogParent);
            session.currentLevelNodes = session.currentLevelDoc.getFlatBeans();
            session.canvas.setLevelNodes(session.currentLevelNodes);
            refreshLevelNodeList();
            session.canvas.repaint();
            listener.onLevelNodesChanged();
        } catch (Exception ex) {
            listener.onStatusMessage(ex.getMessage());
            JOptionPane.showMessageDialog(dialogParent, "保存失败: " + ex.getMessage());
        }
    }

    private JComboBox<String> createParentComboBox(List<LevelNodeBean> beans) {
        JComboBox<String> combo = new JComboBox<>();
        for (int i = 0; i < beans.size(); i++) {
            LevelNodeBean b = beans.get(i);
            String label = "idx=" + i + " " + NodeType.getTypeName(b.getType()) + " DataId=" + b.getDataId()
                + " BelongId=" + b.getBelongId();
            combo.addItem(label);
        }
        return combo;
    }

    private int findFlatIndex(LevelNodeBean hit) {
        List<LevelNodeBean> list = session.currentLevelDoc.getFlatBeans();
        for (int i = 0; i < list.size(); i++) {
            LevelNodeBean n = list.get(i);
            if (n == hit) {
                return i;
            }
            if (n.getDataId() == hit.getDataId() && n.getBelongId() == hit.getBelongId() && n.getX() == hit.getX()
                && n.getZ() == hit.getZ() && n.getType() == hit.getType()) {
                return i;
            }
        }
        return -1;
    }

    private static Integer parseLevelNodeFilterInt(String s) {
        if (s == null) {
            return null;
        }
        s = s.trim();
        if (s.isEmpty()) {
            return null;
        }
        try {
            int v = Integer.parseInt(s);
            if (v == LEVEL_NODE_FILTER_TYPE_ANY) {
                return null;
            }
            return v;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean matchesLevelNodeFilter(LevelNodeBean node, Integer ft, Integer fb) {
        if (ft != null && node.getType() != ft) {
            return false;
        }
        return fb == null || node.getBelongId() == fb || (node.getBelongId() == -1 && node.getDataId() == fb);
    }

    private void updateLevelNodeCount() {
        if (levelNodeCountLabel != null) {
            levelNodeCountLabel.setText("总计: " + displayedLevelNodes.size() + "个节点");
        }
    }
}
