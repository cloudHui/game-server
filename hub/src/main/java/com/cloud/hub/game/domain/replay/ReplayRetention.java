package com.cloud.hub.game.domain.replay;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** 每人最多保留 20 条回放：展示与落盘清理共用。 */
public final class ReplayRetention {
    public static final int PER_USER_LIMIT = 20;
    private static final Pattern USER_ID = Pattern.compile("userId=(-?\\d+),");

    private ReplayRetention() {
    }

    public static final class FileRef {
        public final Path path;
        public final long mtime;
        public final Set<Integer> userIds;

        FileRef(Path path, long mtime, Set<Integer> userIds) {
            this.path = path;
            this.mtime = mtime;
            this.userIds = userIds;
        }
    }

    public static List<FileRef> scan(Path root) throws IOException {
        if (root == null || !Files.isDirectory(root)) return Collections.emptyList();
        List<FileRef> files = new ArrayList<FileRef>();
        try (Stream<Path> days = Files.list(root)) {
            for (Path day : days.filter(Files::isDirectory).collect(Collectors.toList())) {
                try (Stream<Path> txts = Files.list(day)) {
                    for (Path file : txts.filter(path -> path.getFileName().toString().endsWith(".txt"))
                            .collect(Collectors.toList())) {
                        files.add(new FileRef(file, file.toFile().lastModified(), parseUserIds(file)));
                    }
                }
            }
        }
        return files;
    }

    public static List<FileRef> visibleForUser(List<FileRef> files, int userId) {
        List<FileRef> mine = new ArrayList<FileRef>();
        for (FileRef file : files) {
            if (file.userIds.contains(Integer.valueOf(userId))) mine.add(file);
        }
        mine.sort(Comparator.comparingLong((FileRef file) -> file.mtime).reversed());
        if (mine.size() <= PER_USER_LIMIT) return mine;
        return new ArrayList<FileRef>(mine.subList(0, PER_USER_LIMIT));
    }

    public static int prune(Path root) throws IOException {
        List<FileRef> files = scan(root);
        Set<Path> keep = keepPaths(files);
        int removed = 0;
        for (FileRef file : files) {
            if (keep.contains(file.path)) continue;
            if (Files.deleteIfExists(file.path)) removed++;
        }
        return removed;
    }

    static Set<Path> keepPaths(List<FileRef> files) {
        Map<Integer, List<FileRef>> byUser = new HashMap<Integer, List<FileRef>>();
        List<FileRef> noHuman = new ArrayList<FileRef>();
        for (FileRef file : files) {
            boolean human = false;
            for (Integer userId : file.userIds) {
                if (userId.intValue() > 0) {
                    human = true;
                    List<FileRef> bucket = byUser.get(userId);
                    if (bucket == null) {
                        bucket = new ArrayList<FileRef>();
                        byUser.put(userId, bucket);
                    }
                    bucket.add(file);
                }
            }
            if (!human) noHuman.add(file);
        }
        Set<Path> keep = new HashSet<Path>();
        for (List<FileRef> bucket : byUser.values()) {
            bucket.sort(Comparator.comparingLong((FileRef file) -> file.mtime).reversed());
            int limit = Math.min(PER_USER_LIMIT, bucket.size());
            for (int i = 0; i < limit; i++) keep.add(bucket.get(i).path);
        }
        noHuman.sort(Comparator.comparingLong((FileRef file) -> file.mtime).reversed());
        int robotLimit = Math.min(PER_USER_LIMIT, noHuman.size());
        for (int i = 0; i < robotLimit; i++) keep.add(noHuman.get(i).path);
        return keep;
    }

    static Set<Integer> parseUserIds(Path file) {
        Set<Integer> ids = new HashSet<Integer>();
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                Matcher matcher = USER_ID.matcher(line);
                while (matcher.find()) ids.add(Integer.valueOf(matcher.group(1)));
            }
        } catch (IOException ignored) {
        }
        return ids;
    }
}
