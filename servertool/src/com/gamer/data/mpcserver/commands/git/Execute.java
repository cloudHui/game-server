package com.gamer.data.mpcserver.commands.git;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gamer.data.mpcserver.commands.CommandHandler;
import com.gamer.data.mpcserver.core.CommandContext;
import com.gamer.data.mpcserver.core.CommandResult;
import com.gamer.data.mpcserver.core.GitCommandSupport;
import com.gamer.data.mpcserver.core.McpUtils;
import com.gamer.data.mpcserver.core.Process;

/**
 * 执行只读 git 子命令（白名单：log/show/diff/diff-tree/rev-parse/status）。
 *
 * @author liuyunhui
 * @date 2026/05/20
 */
@Process(value = "git_run", description = "执行白名单内的只读 Git 命令。", required = {"args"},
    optional = {"maxOutputChars", "timeoutSeconds"})
public class Execute implements CommandHandler {

    /**
     * 从 params.args 解析参数列表，兼容 JSON 数组、JSON 数组字符串、空白分隔字符串。
     */
    private static List<String> parseArgs(JsonNode params, ObjectMapper mapper) {
        List<String> list = new ArrayList<>();
        if (params == null) {
            return list;
        }
        JsonNode argsNode = params.get("args");
        if (argsNode == null || argsNode.isNull()) {
            return list;
        }
        if (argsNode.isArray()) {
            collectArray(argsNode, list);
            return list;
        }
        String text = argsNode.asText();
        if (text == null || text.trim().isEmpty()) {
            return list;
        }
        String trimmed = text.trim();
        if (mapper != null && trimmed.startsWith("[")) {
            try {
                JsonNode parsed = mapper.readTree(trimmed);
                if (parsed != null && parsed.isArray()) {
                    collectArray(parsed, list);
                    return list;
                }
            } catch (Exception ignored) {
                // 不是 JSON 数组则按空白拆分
            }
        }
        String[] parts = trimmed.split("\\s+");
        for (String part : parts) {
            if (part != null && !part.isEmpty()) {
                list.add(part);
            }
        }
        return list;
    }

    private static void collectArray(JsonNode arrayNode, List<String> list) {
        Iterator<JsonNode> it = arrayNode.elements();
        while (it.hasNext()) {
            JsonNode n = it.next();
            if (n != null && !n.isNull()) {
                String text = n.asText();
                if (text != null && !text.trim().isEmpty()) {
                    list.add(text);
                }
            }
        }
    }

    @Override
    public CommandResult handle(CommandContext ctx, JsonNode params) throws Exception {
        File repo = GitCommandSupport.requireRepo(ctx.gitDefaults());
        List<String> gitArgs = parseArgs(params, ctx.mapper());
        if (gitArgs.isEmpty()) {
            throw new IllegalArgumentException("args 不能为空（JSON 数组或空白分隔的 git 子命令）");
        }
        GitCommandSupport.assertReadOnlySubCommand(gitArgs.get(0));
        GitCommandSupport.assertSafeArgs(gitArgs);

        int maxChars = McpUtils.intOrDefault(params, "maxOutputChars", 500000);
        int timeoutSec = McpUtils.intOrDefault(params, "timeoutSeconds", 60);

        String out = GitCommandSupport.runGit(repo, gitArgs, maxChars, timeoutSec);
        return CommandResult.of(out);
    }
}
