package manager.ui;

import manager.model.ServiceSpec;
import manager.model.ServiceState;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.function.BiConsumer;

final class ServiceRow extends JPanel {
    enum Action { START, STOP, RESTART, LOG }
    private final JLabel stateLabel = new JLabel("检查中...");

    ServiceRow(ServiceSpec spec, BiConsumer<Action, ServiceSpec> handler) {
        super(new BorderLayout(8, 0));
        setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(210, 210, 210)), new EmptyBorder(8, 8, 8, 8)));
        JLabel title = new JLabel(spec.name() + "  :" + spec.port());
        title.setPreferredSize(new Dimension(130, 32));
        JPanel operations = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        addButton(operations, "启动", Action.START, spec, handler);
        addButton(operations, "停止", Action.STOP, spec, handler);
        addButton(operations, "重启", Action.RESTART, spec, handler);
        addButton(operations, "日志", Action.LOG, spec, handler);
        add(title, BorderLayout.WEST);
        add(stateLabel, BorderLayout.CENTER);
        add(operations, BorderLayout.EAST);
    }

    void update(ServiceState state) {
        stateLabel.setText(state.displayText());
        stateLabel.setForeground(switch (state.status()) {
            case RUNNING -> new Color(0, 125, 55);
            case PROCESS_WITHOUT_PORT, PORT_CONFLICT -> Color.RED.darker();
            case STOPPED -> Color.DARK_GRAY;
        });
    }

    private void addButton(JPanel panel, String text, Action action, ServiceSpec spec, BiConsumer<Action, ServiceSpec> handler) {
        JButton button = new JButton(text);
        button.addActionListener(event -> handler.accept(action, spec));
        panel.add(button);
    }
}
