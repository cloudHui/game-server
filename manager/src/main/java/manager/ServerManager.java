package manager;

import manager.ui.ManagerFrame;
import manager.util.ProjectPaths;

import javax.swing.SwingUtilities;

public final class ServerManager {
    private ServerManager() {}

    public static void main(String[] args) {
        if (Runtime.version().feature() < 17) {
            System.err.println("ServerManager 需要 JDK 17+");
            System.exit(2);
        }
        SwingUtilities.invokeLater(() -> new ManagerFrame(ProjectPaths.findRoot()).open());
    }
}
