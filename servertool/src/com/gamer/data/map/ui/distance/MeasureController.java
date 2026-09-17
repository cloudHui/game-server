package com.gamer.data.map.ui.distance;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import com.gamer.data.map.ui.MapUiUtils;
import com.gamer.data.map.ui.MapViewerSession;

/**
 * 测距输入与地图标记同步。点格式与寻路一致：x,z。
 */
public final class MeasureController {

    /** 测距输入框未设点时的坐标哨兵 */
    public static final int DISTANCE_COORD_EMPTY = -1;

    private final MapViewerSession session;

    private JTextField distancePoint1Field;
    private JTextField distancePoint2Field;
    private javax.swing.JLabel distanceResultLabel;

    /**
     * @param session
     *            共享会话
     */
    public MeasureController(MapViewerSession session) {
        this.session = session;
    }

    /**
     * 绑定测距 UI 控件。
     */
    public void bindFields(JTextField point1, JTextField point2, javax.swing.JLabel resultLabel) {
        this.distancePoint1Field = point1;
        this.distancePoint2Field = point2;
        this.distanceResultLabel = resultLabel;
    }

    /**
     * 创建测距点输入框并注册实时同步。
     */
    public JTextField createDistanceField() {
        JTextField field = MapUiUtils.createPointField(7);
        field.setText(MapUiUtils.formatPoint(DISTANCE_COORD_EMPTY, DISTANCE_COORD_EMPTY));
        attachDistanceFieldSyncListener(field);
        return field;
    }

    /**
     * 测距输入框变更时同步地图标记与距离标签。
     */
    public void syncDistanceFromInput() {
        if (distancePoint1Field == null || distancePoint2Field == null) {
            return;
        }
        int[] p1 = parseOrEmpty(distancePoint1Field.getText());
        int[] p2 = parseOrEmpty(distancePoint2Field.getText());
        boolean point1Set = isDistancePointSet(p1[0], p1[1]);
        boolean point2Set = isDistancePointSet(p2[0], p2[1]);
        if (session.canvas != null && session.currentMapData != null) {
            session.canvas.setDistanceMarkersFromUi(p1[0], p1[1], p2[0], p2[1]);
        } else if (session.canvas != null) {
            session.canvas.clearDistanceMarkers();
        }
        if (distanceResultLabel != null) {
            if (point1Set && point2Set) {
                long dx = (long)p2[0] - p1[0];
                long dz = (long)p2[1] - p1[1];
                double dist = Math.sqrt(dx * dx + dz * dz);
                distanceResultLabel.setText(String.format("距离: %.1f", dist));
            } else {
                distanceResultLabel.setText("距离: —");
            }
        }
    }

    /**
     * 清空测距输入、地图测距标记与结果标签。
     */
    public void clearDistanceInputs() {
        if (distancePoint1Field != null) {
            distancePoint1Field.setText(MapUiUtils.formatPoint(DISTANCE_COORD_EMPTY, DISTANCE_COORD_EMPTY));
        }
        if (distancePoint2Field != null) {
            distancePoint2Field.setText(MapUiUtils.formatPoint(DISTANCE_COORD_EMPTY, DISTANCE_COORD_EMPTY));
        }
        if (session.canvas != null) {
            session.canvas.clearDistanceMarkers();
        }
        if (distanceResultLabel != null) {
            distanceResultLabel.setText("距离: —");
        }
    }

    /**
     * 在地图右键菜单中追加测距填入项。
     *
     * @return 是否添加了至少一项
     */
    public boolean appendDistanceFillMenuItems(JPopupMenu menu, final int worldX, final int worldZ) {
        if (distancePoint1Field == null) {
            return false;
        }
        int[] p1 = parseOrEmpty(distancePoint1Field.getText());
        int[] p2 = parseOrEmpty(distancePoint2Field.getText());
        boolean point1Set = isDistancePointSet(p1[0], p1[1]);
        boolean point2Set = isDistancePointSet(p2[0], p2[1]);
        if (point1Set && point2Set) {
            return false;
        }
        if (!point1Set) {
            JMenuItem fill1 = new JMenuItem("填入测距点1");
            fill1.addActionListener(e -> fillDistancePointFromMap(worldX, worldZ, true));
            menu.add(fill1);
        }
        if (!point2Set) {
            JMenuItem fill2 = new JMenuItem("填入测距点2");
            fill2.addActionListener(e -> fillDistancePointFromMap(worldX, worldZ, false));
            menu.add(fill2);
        }
        return true;
    }

    private void fillDistancePointFromMap(int worldX, int worldZ, boolean fillPoint1) {
        if (fillPoint1) {
            distancePoint1Field.setText(MapUiUtils.formatPoint(worldX, worldZ));
        } else {
            distancePoint2Field.setText(MapUiUtils.formatPoint(worldX, worldZ));
        }
        syncDistanceFromInput();
    }

    private void attachDistanceFieldSyncListener(final JTextField field) {
        field.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                syncDistanceFromInput();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                syncDistanceFromInput();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                syncDistanceFromInput();
            }
        });
    }

    private static int[] parseOrEmpty(String text) {
        int[] point = MapUiUtils.parsePoint(text);
        if (point != null) {
            return point;
        }
        return new int[] {DISTANCE_COORD_EMPTY, DISTANCE_COORD_EMPTY};
    }

    private static boolean isDistancePointSet(int worldX, int worldZ) {
        return !(worldX == DISTANCE_COORD_EMPTY && worldZ == DISTANCE_COORD_EMPTY);
    }
}
