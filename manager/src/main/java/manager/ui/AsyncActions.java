package manager.ui;

import javax.swing.SwingWorker;
import java.util.function.Consumer;

final class AsyncActions {
    @FunctionalInterface interface Task { void run() throws Exception; }

    void run(Task task, Runnable completed, Consumer<Throwable> failed) {
        new SwingWorker<Void, Void>() {
            protected Void doInBackground() throws Exception { task.run(); return null; }
            protected void done() {
                try { get(); completed.run(); }
                catch (Exception error) { failed.accept(error.getCause() == null ? error : error.getCause()); }
            }
        }.execute();
    }
}
