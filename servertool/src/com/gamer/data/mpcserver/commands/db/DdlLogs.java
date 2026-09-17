package com.gamer.data.mpcserver.commands.db;

import com.gamer.data.mpcserver.core.Process;

@Process(value = "local_get_ddl_sql_logs", description = "读取 DDL 日志。", optional = {"target", "limit", "offset"})
public final class DdlLogs extends GeneralLogs {
    public DdlLogs() {
        super("DDL", "argument LIKE 'CREATE %' OR argument LIKE 'ALTER %' OR argument LIKE 'DROP %' "
            + "OR argument LIKE 'TRUNCATE %' OR argument LIKE 'RENAME %'");
    }
}
