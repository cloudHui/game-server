package com.gamer.data.map.ui.path;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;

import com.gamer.data.map.path.compare.PathSimulationMode;
import com.gamer.data.map.path.tool.ToolPathStrategyType;
import com.gamer.data.map.path.world.WorldMapDisplayMode;

/**
 * 寻路调试栏控件引用。
 */
final class PathDebugUi {

    JPanel actionPanel;
    JPanel paramPanel;
    JPanel coordinatePanel;
    JPanel worldMapPanel;
    JPanel statusPanel;
    JPanel logPanel;
    JPanel legendPanel;

    JComboBox<PathSimulationMode> modeCombo;
    JComboBox<ToolPathStrategyType> strategyCombo;
    JComboBox<WorldMapDisplayMode> worldDisplayModeCombo;
    JLabel currentMapLabel;
    JLabel statusLabel;
    JLabel startLabel;
    JLabel endLabel;
    JLabel resultLabel;
    JTextArea logArea;
    JTextField startPointField;
    JTextField endPointField;
    JTextField batchSizeField;
    JCheckBox allowCutCornerCheck;
    JCheckBox showSearchCheck;
    JCheckBox showToolPathCheck;
    JCheckBox showWorldPathCheck;
}
