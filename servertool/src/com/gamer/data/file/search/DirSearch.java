package com.gamer.data.file.search;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.swing.SwingUtilities;

import com.gamer.data.task.BackgroundTasks;

/**
 * 目录名/文件名递归检索：后台线程、去环、结果上限与排序。
 */
final class DirSearch {

    /** 检索完成回调。 */
    interface Listener {
        /**
         * @param result
         *            检索结果
         */
        void onCompleted(Result result);
    }

    /** 不可变检索结果。 */
    static final class Result {
        /** 匹配文件列表 */
        final List<File> files;
        /** 是否触达结果上限 */
        final boolean limitReached;

        private Result(List<File> files, boolean limitReached) {
            this.files = Collections.unmodifiableList(new ArrayList<>(files));
            this.limitReached = limitReached;
        }
    }

    /** 最大结果数 */
    private final int maxResults;

    /**
     * @param maxResults
     *            最大结果数
     */
    DirSearch(int maxResults) {
        if (maxResults <= 0) {
            throw new IllegalArgumentException("maxResults must be greater than zero");
        }
        this.maxResults = maxResults;
    }

    /**
     * 在守护线程中检索，并在 EDT 回调结果。
     *
     * @param baseDirectory
     *            检索根目录
     * @param query
     *            关键词
     * @param listener
     *            完成回调
     */
    void searchAsync(final File baseDirectory, final String query, final Listener listener) {
        final Result[] resultHolder = new Result[1];
        BackgroundTasks.start("file-name-search-worker", () -> {
            resultHolder[0] = scan(baseDirectory, query.toLowerCase(Locale.ROOT));
            return 0;
        }, new BackgroundTasks.Listener() {
            @Override
            public void onStarted(long startMillis) {}

            @Override
            public void onSucceeded(int resultCode, long endMillis) {
                SwingUtilities.invokeLater(() -> listener.onCompleted(resultHolder[0]));
            }

            @Override
            public void onFailed(Exception error, long endMillis) {
                SwingUtilities.invokeLater(() -> listener.onCompleted(new Result(Collections.emptyList(), false)));
            }

            @Override
            public void onFinished() {}
        });
    }

    /**
     * 递归扫描并按绝对路径排序。
     *
     * @param baseDirectory
     *            检索根目录
     * @param queryLower
     *            小写关键词
     * @return 检索结果
     */
    private Result scan(File baseDirectory, String queryLower) {
        MutableResult result = new MutableResult();
        collect(baseDirectory, queryLower, result, new HashSet<>());
        result.files.sort((left, right) -> left.getAbsolutePath().compareToIgnoreCase(right.getAbsolutePath()));
        return new Result(result.files, result.limitReached);
    }

    /**
     * 递归收集匹配项；达到上限后停止，符号链接目录不下钻。
     */
    private void collect(File directory, String queryLower, MutableResult result, Set<String> visited) {
        if (result.limitReached || directory == null || !directory.isDirectory()) {
            return;
        }
        String canonicalPath;
        try {
            canonicalPath = directory.getCanonicalPath();
        } catch (IOException ex) {
            canonicalPath = directory.getAbsolutePath();
        }
        if (!visited.add(canonicalPath)) {
            return;
        }
        File[] children = directory.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (child.getName().toLowerCase(Locale.ROOT).contains(queryLower)) {
                result.files.add(child);
                if (result.files.size() >= maxResults) {
                    result.limitReached = true;
                    return;
                }
            }
            if (child.isDirectory() && !Files.isSymbolicLink(child.toPath())) {
                collect(child, queryLower, result, visited);
                if (result.limitReached) {
                    return;
                }
            }
        }
    }

    /** 扫描过程可变累积。 */
    private static final class MutableResult {
        private final List<File> files = new ArrayList<>();
        private boolean limitReached;
    }
}
