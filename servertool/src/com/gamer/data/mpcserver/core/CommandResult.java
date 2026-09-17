package com.gamer.data.mpcserver.core;

/** 命令文本结果。 */
public final class CommandResult {
    public final String text;

    private CommandResult(String text) {
        this.text = text;
    }

    public static CommandResult of(String text) {
        return new CommandResult(text);
    }
}
