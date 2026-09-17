package com.gamer.data.mpcserver.commands.excel.edit;

import com.fasterxml.jackson.databind.JsonNode;
import com.gamer.data.mpcserver.commands.CommandHandler;
import com.gamer.data.mpcserver.core.CommandContext;
import com.gamer.data.mpcserver.core.CommandResult;
import com.gamer.data.mpcserver.core.McpServiceLog;

/** Excel 写命令公共生命周期。 */
public abstract class EditCommand implements CommandHandler {

    @Override
    public final CommandResult handle(CommandContext ctx, JsonNode params) throws Exception {
        if (ctx == null) {
            throw new IllegalArgumentException("CommandContext不能为空");
        }
        EditSession session = EditSession.open(ctx, params);
        try {
            Result result = edit(ctx, params, session);
            session.save();
            McpServiceLog.cmdAndResult(ctx.log(), McpServiceLog.SERVICE_EXCEL, result.commandLog, result.output);
            return CommandResult.of(result.output);
        } catch (Exception e) {
            session.discard();
            throw e;
        }
    }

    /**
     * @param ctx
     *            命令上下文
     * @param params
     *            命令参数
     * @param session
     *            编辑会话
     * @return 编辑结果
     * @throws Exception
     *             编辑失败
     */
    protected abstract Result edit(CommandContext ctx, JsonNode params, EditSession session) throws Exception;

    protected final Result result(String output, String commandLog) {
        return new Result(output, commandLog);
    }

    /** 编辑输出与审计日志。 */
    protected static final class Result {
        private final String output;
        private final String commandLog;

        private Result(String output, String commandLog) {
            this.output = output;
            this.commandLog = commandLog;
        }
    }
}
