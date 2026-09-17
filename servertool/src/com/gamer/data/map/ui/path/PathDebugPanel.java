package com.gamer.data.map.ui.path;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.LayoutManager;
import java.awt.event.ActionListener;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;

import com.gamer.data.ui.ViewUi;
import com.gamer.data.map.path.compare.PathSimulationMode;
import com.gamer.data.map.path.tool.ToolPathStrategyType;
import com.gamer.data.map.path.world.WorldMapDisplayMode;
import com.gamer.data.map.ui.MapUiUtils;
import com.gamer.data.map.ui.path.replay.ReplayController;

/**
 * 寻路模拟调试侧栏 UI。
 */
public final class PathDebugPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    public static final int DEFAULT_WIDTH_PX = 320;

    private static final int PATH_PANEL_GAP = 4;
    private static final int PATH_LOG_HEIGHT_PX = 320;

    private final PathDebugController controller;
    private JButton pathToggleButton;
    private JButton pathClearButton;

    public PathDebugPanel(PathDebugController controller) {
        this.controller = controller;
        buildUi();
    }

    private void buildUi() {
        setLayout(new BorderLayout(0, 0));
        ViewUi.titled(this, "寻路模拟");
        setPreferredSize(new Dimension(DEFAULT_WIDTH_PX, 100));
        setMinimumSize(new Dimension(DEFAULT_WIDTH_PX, 100));

        PathDebugUi ui = new PathDebugUi();
        ui.actionPanel = createActionSection();
        ui.paramPanel = createParamSection(ui);
        ui.coordinatePanel = createCoordinateSection(ui);
        ui.worldMapPanel = createWorldMapSection(ui);
        ui.statusPanel = createStatusSection(ui);
        ui.logPanel = createLogSection(ui);
        ui.legendPanel = createLegendSection();
        controller.bindUi(ui);
        add(assembleContentPanel(ui), BorderLayout.CENTER);
    }

    private JPanel createActionSection() {
        JPanel actionPanel = createSection("操作", new FlowLayout(FlowLayout.LEFT, PATH_PANEL_GAP, 0));
        pathToggleButton = createPathButton("开启模拟", e -> {
            controller.togglePathSimulation();
            controller.refreshToggleButtons(pathToggleButton, pathClearButton);
        });
        pathClearButton = createPathButton("清空", e -> {
            controller.resetPathSelection("寻路: 请选择起点");
            controller.refreshToggleButtons(pathToggleButton, pathClearButton);
        });
        pathClearButton.setEnabled(false);
        actionPanel.add(pathToggleButton);
        actionPanel.add(pathClearButton);
        return actionPanel;
    }

    private JPanel createParamSection(PathDebugUi ui) {
        JPanel paramPanel = createSection("参数", new GridLayout(0, 1, 0, 0));

        JPanel modeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, PATH_PANEL_GAP, 0));
        ui.modeCombo = new JComboBox<>(PathSimulationMode.values());
        ui.modeCombo.addActionListener(e -> {
            controller.onSimulationModeChanged();
            controller.refreshToggleButtons(pathToggleButton, pathClearButton);
        });
        ui.strategyCombo = new JComboBox<>(ToolPathStrategyType.values());
        ui.strategyCombo.addActionListener(e -> {
            controller.onStrategyChanged();
            controller.refreshToggleButtons(pathToggleButton, pathClearButton);
        });
        modeRow.add(new JLabel("运行:"));
        modeRow.add(ui.modeCombo);
        modeRow.add(new JLabel("工具:"));
        modeRow.add(ui.strategyCombo);

        JPanel batchRow = new JPanel(new FlowLayout(FlowLayout.LEFT, PATH_PANEL_GAP, 0));
        ui.batchSizeField = new JTextField(String.valueOf(ReplayController.DEFAULT_BATCH_SIZE), 4);
        ui.allowCutCornerCheck = new JCheckBox("允许切角");
        batchRow.add(new JLabel("每帧:"));
        batchRow.add(ui.batchSizeField);
        batchRow.add(new JLabel("点"));
        batchRow.add(ui.allowCutCornerCheck);

        JPanel layerRow = new JPanel(new FlowLayout(FlowLayout.LEFT, PATH_PANEL_GAP, 0));
        ui.showSearchCheck = new JCheckBox("搜索", true);
        ui.showToolPathCheck = new JCheckBox("Tool", true);
        ui.showWorldPathCheck = new JCheckBox("World", true);
        ActionListener layerListener = e -> controller.applyPathLayers();
        ui.showSearchCheck.addActionListener(layerListener);
        ui.showToolPathCheck.addActionListener(layerListener);
        ui.showWorldPathCheck.addActionListener(layerListener);
        layerRow.add(ui.showSearchCheck);
        layerRow.add(ui.showToolPathCheck);
        layerRow.add(ui.showWorldPathCheck);

        paramPanel.add(modeRow);
        paramPanel.add(batchRow);
        paramPanel.add(layerRow);
        return paramPanel;
    }

    private JPanel createCoordinateSection(PathDebugUi ui) {
        JPanel coordinatePanel = createSection("坐标", new GridLayout(0, 1, 0, 0));
        ui.startPointField = MapUiUtils.createPointField(8);
        ui.endPointField = MapUiUtils.createPointField(8);
        JPanel startRow = flowRow("起点:", ui.startPointField);
        JPanel endRow = flowRow("终点:", ui.endPointField);
        JButton apply = createPathButton("应用坐标", e -> controller.applyInputPathPoints());
        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.LEFT, PATH_PANEL_GAP, 0));
        buttonRow.add(apply);
        coordinatePanel.add(startRow);
        coordinatePanel.add(endRow);
        coordinatePanel.add(buttonRow);
        return coordinatePanel;
    }

    private JPanel createWorldMapSection(PathDebugUi ui) {
        JPanel worldMapPanel = createSection("地图", new GridLayout(0, 1, 0, 0));
        ui.currentMapLabel = new JLabel("当前: -");
        JPanel directionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, PATH_PANEL_GAP, 0));
        directionPanel.add(new JLabel("方向:"));
        ui.worldDisplayModeCombo = new JComboBox<>(WorldMapDisplayMode.values());
        ui.worldDisplayModeCombo.addActionListener(e -> {
            controller.onWorldDisplayModeChanged();
            controller.refreshToggleButtons(pathToggleButton, pathClearButton);
        });
        directionPanel.add(ui.worldDisplayModeCombo);
        worldMapPanel.add(ui.currentMapLabel);
        worldMapPanel.add(directionPanel);
        return worldMapPanel;
    }

    private JPanel createStatusSection(PathDebugUi ui) {
        JPanel statusPanel = createSection("状态", new GridLayout(0, 1, 0, 0));
        ui.statusLabel = new JLabel("寻路: 未开启");
        ui.startLabel = new JLabel("起点: -");
        ui.endLabel = new JLabel("终点: -");
        ui.resultLabel = new JLabel("结果: -");
        statusPanel.add(ui.statusLabel);
        statusPanel.add(ui.startLabel);
        statusPanel.add(ui.endLabel);
        statusPanel.add(ui.resultLabel);
        return statusPanel;
    }

    private JPanel createLogSection(PathDebugUi ui) {
        JPanel logPanel = createSection("日志", new BorderLayout());
        ui.logArea = new JTextArea();
        ui.logArea.setEditable(false);
        ui.logArea.setLineWrap(false);
        ui.logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane logScroll = new JScrollPane(ui.logArea);
        logScroll.setPreferredSize(new Dimension(DEFAULT_WIDTH_PX - 16, PATH_LOG_HEIGHT_PX));
        logPanel.add(logScroll, BorderLayout.CENTER);
        return logPanel;
    }

    private JPanel createLegendSection() {
        JPanel legendPanel = createSection("图例", new GridLayout(0, 1, 0, 0));
        legendPanel.add(new JLabel("青色实线: Tool路径"));
        legendPanel.add(new JLabel("橙色虚线: World路径"));
        legendPanel.add(new JLabel("黄色半透: 搜索展开点"));
        legendPanel.add(new JLabel("红色诊断: 卡点/阻挡范围"));
        legendPanel.add(new JLabel("彩色圆点: 关卡节点"));
        return legendPanel;
    }

    private JPanel assembleContentPanel(PathDebugUi ui) {
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.add(stretchRow(ui.actionPanel));
        content.add(stretchRow(ui.paramPanel));
        content.add(stretchRow(ui.coordinatePanel));
        content.add(stretchRow(ui.worldMapPanel));
        content.add(stretchRow(ui.statusPanel));
        content.add(stretchRow(ui.logPanel));
        content.add(stretchRow(ui.legendPanel));
        content.add(Box.createVerticalGlue());
        return content;
    }

    private static JPanel flowRow(String label, Component field) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, PATH_PANEL_GAP, 0));
        row.add(new JLabel(label));
        row.add(field);
        return row;
    }

    private static JButton createPathButton(String text, ActionListener listener) {
        JButton button = ViewUi.style(new JButton(text));
        button.addActionListener(listener);
        return button;
    }

    private static JPanel stretchRow(JPanel section) {
        JPanel row = new JPanel(new BorderLayout(0, 0));
        row.add(section, BorderLayout.CENTER);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, section.getPreferredSize().height));
        return row;
    }

    private static JPanel createSection(String title, LayoutManager layout) {
        JPanel section = new JPanel(layout);
        ViewUi.titled(section, title);
        section.setOpaque(true);
        section.setBackground(ViewUi.CARD);
        return section;
    }
}
