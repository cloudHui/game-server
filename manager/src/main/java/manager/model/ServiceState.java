package manager.model;

public record ServiceState(Status status, long pid, boolean managerOwned) {
    public enum Status { STOPPED, RUNNING, PROCESS_WITHOUT_PORT, PORT_CONFLICT }

    public String displayText() {
        String base = switch (status) {
            case STOPPED -> "已停止";
            case RUNNING -> "运行中 / 进程+端口";
            case PROCESS_WITHOUT_PORT -> "异常 / 有进程无端口";
            case PORT_CONFLICT -> "异常 / 端口被占用";
        };
        return base + "   PID " + (pid > 0 ? pid : "-") + (managerOwned ? "   [本管理器持有]" : "");
    }
}
