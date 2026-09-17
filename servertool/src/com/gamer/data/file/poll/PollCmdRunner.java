package com.gamer.data.file.poll;

import java.util.Arrays;

import com.gamer.data.file.job.Pipe;

/**
 * 执行一次 cmd，合并 stdout/stderr，按关键字判定是否命中。
 */
final class PollCmdRunner {

    private PollCmdRunner() {}

    /**
     * @return true 表示输出命中关键字
     */
    static boolean runOnce(PollCmdTask task) throws Exception {
        final String needle = task.keyword.toLowerCase();
        final boolean[] hit = new boolean[1];
        int exit = Pipe.runLines(Arrays.asList("cmd", "/c", task.command), task.workDir, null, true, Pipe.cmdCs(),
                line -> {
                PollCmdUi.append(task, line);
                    if (!hit[0] && line.toLowerCase().contains(needle)) {
                        hit[0] = true;
                    }
                });
        PollCmdUi.append(task, "[exit=" + exit + (hit[0] ? ", HIT" : "") + "]");
        return hit[0];
    }
}
