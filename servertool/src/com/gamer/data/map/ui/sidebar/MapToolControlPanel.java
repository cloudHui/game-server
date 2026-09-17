package com.gamer.data.map.ui.sidebar;

import java.awt.FlowLayout;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

import com.gamer.data.map.ui.MapUiUtils;
import com.gamer.data.map.ui.MapViewerSession;
import com.gamer.data.map.ui.distance.MeasureController;

/**
 * 地图文件区下方的定位、测距与缩放工具栏。
 */
public final class MapToolControlPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private final MapViewerSession session;
    private final MeasureController distanceController;

    private JTextField worldXField;
    private JTextField worldZField;
    private JButton zoomInButton;
    private JButton zoomOutButton;
    private JButton resetZoomButton;

    /**
     * @param session
     *            共享会话
     * @param distanceController
     *            测距控制器
     */
    public MapToolControlPanel(MapViewerSession session, MeasureController distanceController) {
        this.session = session;
        this.distanceController = distanceController;
        buildUi();
    }

    public JTextField getWorldXField() {
        return worldXField;
    }

    public JTextField getWorldZField() {
        return worldZField;
    }

    /**
     * 根据当前缩放更新按钮启用状态。
     */
    public void updateZoomButtonStates() {
        if (zoomInButton == null || resetZoomButton == null || zoomOutButton == null || session.canvas == null) {
            return;
        }
        zoomInButton.setEnabled(session.canvas.canBigger());
        resetZoomButton.setEnabled(session.canvas.canReset());
        zoomOutButton.setEnabled(session.canvas.canSmaller());
    }

    private void buildUi() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

        JPanel locateRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 1));
        locateRow.add(new JLabel("定位"));
        locateRow.add(new JLabel("X:"));
        worldXField = new JTextField("0", 4);
        MapUiUtils.addSelectAllOnFocus(worldXField);
        locateRow.add(worldXField);
        locateRow.add(new JLabel("Z:"));
        worldZField = new JTextField("0", 4);
        MapUiUtils.addSelectAllOnFocus(worldZField);
        locateRow.add(worldZField);
        JButton showBtn = new JButton("显示");
        MapUiUtils.compactButton(showBtn);
        showBtn.addActionListener(e -> showMarkerFromInput());
        locateRow.add(showBtn);
        MapUiUtils.alignRowLeft(locateRow);

        JPanel distancePointRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 1));
        distancePointRow.add(new JLabel("测距"));
        distancePointRow.add(new JLabel("点1:"));
        JTextField distancePoint1Field = distanceController.createDistanceField();
        distancePointRow.add(distancePoint1Field);
        distancePointRow.add(new JLabel("点2:"));
        JTextField distancePoint2Field = distanceController.createDistanceField();
        distancePointRow.add(distancePoint2Field);
        MapUiUtils.alignRowLeft(distancePointRow);

        JPanel distanceActionRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 1));
        distanceActionRow.add(new JLabel("操作"));
        JButton calcDistanceBtn = new JButton("算距离");
        MapUiUtils.compactButton(calcDistanceBtn);
        calcDistanceBtn.addActionListener(e -> distanceController.syncDistanceFromInput());
        distanceActionRow.add(calcDistanceBtn);
        JButton clearDistanceBtn = new JButton("清空");
        MapUiUtils.compactButton(clearDistanceBtn);
        clearDistanceBtn.addActionListener(e -> distanceController.clearDistanceInputs());
        distanceActionRow.add(clearDistanceBtn);
        JLabel distanceResultLabel = new JLabel("距离: —");
        distanceActionRow.add(distanceResultLabel);
        MapUiUtils.alignRowLeft(distanceActionRow);

        distanceController.bindFields(distancePoint1Field, distancePoint2Field, distanceResultLabel);

        JPanel zoomRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 1));
        zoomRow.add(new JLabel("缩放"));
        zoomInButton = new JButton("放大");
        MapUiUtils.compactButton(zoomInButton);
        zoomInButton.addActionListener(e -> {
            if (session.canvas != null) {
                session.canvas.zoomIn();
            }
        });
        zoomRow.add(zoomInButton);

        zoomOutButton = new JButton("缩小");
        MapUiUtils.compactButton(zoomOutButton);
        zoomOutButton.addActionListener(e -> {
            if (session.canvas != null) {
                session.canvas.zoomOut();
            }
        });
        zoomRow.add(zoomOutButton);

        resetZoomButton = new JButton("还原");
        MapUiUtils.compactButton(resetZoomButton);
        resetZoomButton.addActionListener(e -> {
            if (session.canvas != null) {
                session.canvas.setZoomScale(1);
            }
        });
        zoomRow.add(resetZoomButton);
        MapUiUtils.alignRowLeft(zoomRow);

        add(locateRow);
        add(distancePointRow);
        add(distanceActionRow);
        add(zoomRow);
    }

    private void showMarkerFromInput() {
        if (session.currentMapData == null || session.canvas == null) {
            return;
        }
        int x = MapUiUtils.parseInt(worldXField.getText());
        int z = MapUiUtils.parseInt(worldZField.getText());
        session.canvas.setSelectedLevelNode(null);
        session.canvas.setMarkerPoint(x, z);
    }
}
