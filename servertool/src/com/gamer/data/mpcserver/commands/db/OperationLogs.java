package com.gamer.data.mpcserver.commands.db;

import com.gamer.data.mpcserver.core.Process;

@Process(value = "local_get_operation_logs", description = "读取操作日志。", optional = {"target", "limit", "offset"})
public final class OperationLogs extends GeneralLogs {
    public OperationLogs() {
        super("OPERATION", "argument LIKE 'INSERT %' OR argument LIKE 'UPDATE %' OR argument LIKE 'DELETE %' "
            + "OR argument LIKE 'REPLACE %'");
    }
}
