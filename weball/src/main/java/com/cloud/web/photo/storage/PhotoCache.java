package com.cloud.web.photo.storage;

import org.springframework.stereotype.Component;
import web.photo.config.PhotoProperties;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class PhotoCache {
    private final PhotoProperties properties;
    private final LinkedHashMap<Long, Path> lru = new LinkedHashMap<>(32, .75f, true);
    private final Map<Long, Integer> leases = new HashMap<>();
    private final Set<Long> pendingDeletes = new HashSet<>();
    private Path root;

    public PhotoCache(PhotoProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public synchronized void init() throws IOException {
        root = Paths.get(properties.getCacheDir()).toAbsolutePath().normalize();
        Files.createDirectories(root);
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> directory = Files.newDirectoryStream(root)) {
            for (Path file : directory) {
                String name = file.getFileName().toString();
                if (name.endsWith(".tmp")) Files.deleteIfExists(file);
                else if (name.matches("[0-9]+\\.[a-z0-9]+")) files.add(file);
                else Files.deleteIfExists(file);
            }
        }
        files.sort(Comparator.comparing(this::modified));
        for (Path file : files) lru.put(idOf(file), file);
        trim();
    }

    public synchronized Path get(long id) {
        Path file = lru.get(id);
        if (file == null || !Files.exists(file)) {
            lru.remove(id);
            return null;
        }
        try { Files.setLastModifiedTime(file, FileTime.fromMillis(System.currentTimeMillis())); }
        catch (IOException ignored) { }
        return file;
    }

    public synchronized Path target(long id, String extension) {
        return root.resolve(id + "." + extension);
    }

    public synchronized void commit(long id, Path file) throws IOException {
        lru.put(id, file);
        trim();
    }

    public synchronized Lease acquire(long id) throws IOException {
        Path file = get(id);
        if (file == null) throw new NoSuchFileException("高清缓存不存在: " + id);
        leases.put(id, leases.getOrDefault(id, 0) + 1);
        return new Lease(id, file, Files.size(file), this);
    }

    public synchronized void remove(long id) throws IOException {
        if (leases.containsKey(id)) {
            pendingDeletes.add(id);
            return;
        }
        Path file = lru.remove(id);
        if (file != null) Files.deleteIfExists(file);
    }

    public synchronized int clear() throws IOException {
        int removed = 0;
        Iterator<Map.Entry<Long, Path>> iterator = lru.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Long, Path> entry = iterator.next();
            if (leases.containsKey(entry.getKey())) continue;
            Files.deleteIfExists(entry.getValue());
            iterator.remove();
            removed++;
        }
        return removed;
    }

    public synchronized int size() {
        return lru.size();
    }

    private synchronized void release(long id) {
        Integer count = leases.get(id);
        if (count == null) return;
        if (count <= 1) leases.remove(id); else leases.put(id, count - 1);
        if (!leases.containsKey(id) && pendingDeletes.remove(id)) {
            Path file = lru.remove(id);
            if (file != null) try { Files.deleteIfExists(file); } catch (IOException ignored) { }
        }
        try { trim(); } catch (IOException ignored) { }
    }

    private void trim() throws IOException {
        while (lru.size() > properties.getCacheMaxFiles()) {
            boolean removed = false;
            Iterator<Map.Entry<Long, Path>> iterator = lru.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<Long, Path> entry = iterator.next();
                if (leases.containsKey(entry.getKey())) continue;
                Files.deleteIfExists(entry.getValue());
                iterator.remove();
                removed = true;
                break;
            }
            if (!removed) throw new IOException("高清缓存正在使用，请稍后重试");
        }
    }

    private FileTime modified(Path path) {
        try { return Files.getLastModifiedTime(path); }
        catch (IOException e) { return FileTime.fromMillis(0); }
    }

    private long idOf(Path path) {
        String name = path.getFileName().toString();
        return Long.parseLong(name.substring(0, name.indexOf('.')));
    }

    public static final class Lease implements AutoCloseable {
        private final long id;
        private final Path path;
        private final long size;
        private PhotoCache owner;

        private Lease(long id, Path path, long size, PhotoCache owner) {
            this.id = id; this.path = path; this.size = size; this.owner = owner;
        }
        public Path getPath() { return path; }
        public long getSize() { return size; }
        @Override public void close() {
            PhotoCache current = owner;
            owner = null;
            if (current != null) current.release(id);
        }
    }
}
