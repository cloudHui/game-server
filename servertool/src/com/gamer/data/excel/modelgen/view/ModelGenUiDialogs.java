package com.gamer.data.excel.modelgen.view;

import java.util.ArrayList;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import com.gamer.data.excel.modelgen.ModelGenContext;
import com.gamer.data.excel.modelgen.domain.ManagerGenInfo;
import com.gamer.data.excel.modelgen.domain.ManagerSelectionItem;
import com.gamer.data.excel.modelgen.domain.ServerConfigSelection;

/**
 * 模型生成相关的 Swing 对话框。
 */
public class ModelGenUiDialogs {

    private ModelGenUiDialogs() {}

    /**
     * 显示 Manager 选择对话框。
     *
     * @param ctx
     *            生成上下文
     * @param pendingManagers
     *            待处理 Manager 列表
     * @return 用户选择结果，取消返回 null
     */
    public static List<ManagerSelectionItem> showManagerSelectionDialog(ModelGenContext ctx,
        List<ManagerGenInfo> pendingManagers) {
        try {
            List<ManagerSelectionItem> items = new ArrayList<>();
            for (ManagerGenInfo info : pendingManagers) {
                items.add(new ManagerSelectionItem(info));
            }

            JCheckBox[] managerCheckBoxes = new JCheckBox[items.size()];
            JCheckBox[] configCheckBoxes = new JCheckBox[items.size()];

            for (int i = 0; i < items.size(); i++) {
                managerCheckBoxes[i] = new JCheckBox();
                managerCheckBoxes[i].setSelected(true);
                configCheckBoxes[i] = new JCheckBox();
                configCheckBoxes[i].setSelected(true);
            }

            JPanel mainPanel = new JPanel(new java.awt.BorderLayout());
            JPanel tablePanel = new JPanel(new java.awt.GridBagLayout());
            java.awt.GridBagConstraints gbc = new java.awt.GridBagConstraints();
            gbc.fill = java.awt.GridBagConstraints.HORIZONTAL;
            gbc.insets = new java.awt.Insets(2, 5, 2, 5);

            gbc.gridy = 0;
            gbc.gridx = 0;
            tablePanel.add(new JLabel("配置类名"), gbc);
            gbc.gridx = 1;
            tablePanel.add(new JLabel("Sheet名"), gbc);
            gbc.gridx = 2;
            tablePanel.add(new JLabel("生成Manager"), gbc);
            gbc.gridx = 3;
            tablePanel.add(new JLabel("添加到config"), gbc);

            for (int i = 0; i < items.size(); i++) {
                gbc.gridy = i + 1;
                gbc.gridx = 0;
                tablePanel.add(new JLabel(items.get(i).getInfo().getConfigClassName()), gbc);
                gbc.gridx = 1;
                tablePanel.add(new JLabel(items.get(i).getInfo().getSheetName()), gbc);
                gbc.gridx = 2;
                tablePanel.add(managerCheckBoxes[i], gbc);
                gbc.gridx = 3;
                tablePanel.add(configCheckBoxes[i], gbc);
            }

            JPanel buttonPanel = createSelectionButtonPanel(managerCheckBoxes, configCheckBoxes);
            JScrollPane scrollPane = new JScrollPane(tablePanel);
            scrollPane.setPreferredSize(new java.awt.Dimension(600, Math.min(400, 30 + items.size() * 30)));

            mainPanel.add(buttonPanel, java.awt.BorderLayout.NORTH);
            mainPanel.add(scrollPane, java.awt.BorderLayout.CENTER);

            String title = "选择要处理的 Manager（共 " + items.size() + " 个）";
            int result = JOptionPane.showConfirmDialog(null, mainPanel, title, JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE);

            if (result == JOptionPane.OK_OPTION) {
                for (int i = 0; i < items.size(); i++) {
                    items.get(i).setGenerateManager(managerCheckBoxes[i].isSelected());
                    items.get(i).setAddToConfig(configCheckBoxes[i].isSelected());
                }
                return items;
            }
            return null;
        } catch (Exception e) {
            ctx.logMessage("无法显示 Manager 选择对话框: " + e);
            System.out.println("无法显示 Manager 选择对话框: " + e);
            return null;
        }
    }

    /**
     * 显示服务器配置选择对话框。
     *
     * @param ctx
     *            生成上下文
     * @param sheetCountText
     *            Sheet 数量文本
     * @return 用户选择结果，取消返回 null
     */
    public static ServerConfigSelection showServerConfigDialog(ModelGenContext ctx, String sheetCountText) {
        try {
            JPanel panel = new JPanel();
            panel.setLayout(new java.awt.GridLayout(2, 1));

            JCheckBox gameServerCheck = new JCheckBox("生成 gameserver 配置", false);
            JCheckBox worldServerCheck = new JCheckBox("生成 worldserver 配置", false);

            panel.add(gameServerCheck);
            panel.add(worldServerCheck);
            gameServerCheck.setSelected(true);
            int result = JOptionPane.showConfirmDialog(null, panel,
                "为 " + sheetCountText + " 个Sheet批量生成配置（Manager/Bean/config.xml）", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.QUESTION_MESSAGE);

            if (result == JOptionPane.OK_OPTION) {
                return new ServerConfigSelection(gameServerCheck.isSelected(), worldServerCheck.isSelected());
            }
            return null;
        } catch (Exception e) {
            ctx.logMessage("无法显示对话框，跳过服务器配置生成: " + e);
            System.out.println("无法显示对话框，跳过服务器配置生成: " + e);
            return null;
        }
    }

    private static JPanel createSelectionButtonPanel(final JCheckBox[] managerCheckBoxes,
        final JCheckBox[] configCheckBoxes) {
        JPanel buttonPanel = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT));
        JButton selectAllManagerBtn = new JButton("全选Manager");
        JButton deselectAllManagerBtn = new JButton("取消全选Manager");
        JButton selectAllConfigBtn = new JButton("全选Config");
        JButton deselectAllConfigBtn = new JButton("取消全选Config");

        selectAllManagerBtn.addActionListener(e -> {
            for (JCheckBox cb : managerCheckBoxes) {
                cb.setSelected(true);
            }
        });
        deselectAllManagerBtn.addActionListener(e -> {
            for (JCheckBox cb : managerCheckBoxes) {
                cb.setSelected(false);
            }
        });
        selectAllConfigBtn.addActionListener(e -> {
            for (JCheckBox cb : configCheckBoxes) {
                cb.setSelected(true);
            }
        });
        deselectAllConfigBtn.addActionListener(e -> {
            for (JCheckBox cb : configCheckBoxes) {
                cb.setSelected(false);
            }
        });

        buttonPanel.add(selectAllManagerBtn);
        buttonPanel.add(deselectAllManagerBtn);
        buttonPanel.add(selectAllConfigBtn);
        buttonPanel.add(deselectAllConfigBtn);
        return buttonPanel;
    }
}
