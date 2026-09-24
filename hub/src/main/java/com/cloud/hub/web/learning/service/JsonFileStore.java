package com.cloud.hub.web.learning.service;

import com.cloud.hub.storage.DataPathResolver;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * 基于 SQLite documents 表的通用 JSON 文档存储层。
 * <p>
 * 保持兼容原有文件系统接口签名，实际将数据全部持久化存储在 SQLite 统一库中，开启 WAL 模式保证读写并发安全。
 *
 * @author cloud
 */
@Component
public class JsonFileStore {

    private static final Logger log = LoggerFactory.getLogger(JsonFileStore.class);
    private final ObjectMapper mapper;
    private final Path root;
    private final Path database;

    public JsonFileStore(ObjectMapper mapper,
                         @Value("${family-learning.data-dir}") String dataDir,
                         DataPathResolver paths) {
        this.mapper = mapper;
        this.root = paths.resolve(dataDir);
        this.database = root.resolve("family-learning.sqlite");
    }

    /**
     * 容器初始化建表并开启 WAL 预写日志模式。
     */
    @PostConstruct
    public void init() throws IOException {
        Files.createDirectories(root);
        try (Connection connection = connection();
             Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("PRAGMA synchronous=NORMAL");
            statement.execute("CREATE TABLE IF NOT EXISTS documents (" +
                    "folder TEXT NOT NULL, item_key TEXT NOT NULL, payload TEXT NOT NULL, " +
                    "updated_at TEXT NOT NULL, PRIMARY KEY(folder, item_key))");
        } catch (SQLException exception) {
            throw new IOException("无法初始化 SQLite 数据库: " + database, exception);
        }
    }

    public Path root() {
        return root;
    }

    public boolean exists(Path path) throws IOException {
        String[] key = key(path);
        return documentExists(key[0], key[1]);
    }

    public boolean documentExists(String folder, String itemKey) throws IOException {
        String sql = "SELECT 1 FROM documents WHERE folder = ? AND item_key = ?";
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, folder);
            statement.setString(2, itemKey);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next();
            }
        } catch (SQLException exception) {
            throw new IOException("SQLite 查询失败: " + folder + "/" + itemKey, exception);
        }
    }

    public Path path(String folder, String safeName) {
        if (!safeName.matches("[a-zA-Z0-9_.-]+")) {
            throw new IllegalArgumentException("非法文件标识");
        }
        return root.resolve(folder).resolve(safeName + ".json");
    }

    public synchronized <T> T read(Path path, Class<T> type) throws IOException {
        String payload = find(path);
        return payload == null ? null : mapper.readValue(payload, type);
    }

    public synchronized <T> List<T> readList(Path path, TypeReference<List<T>> type) throws IOException {
        String payload = find(path);
        return payload == null ? new ArrayList<>() : mapper.readValue(payload, type);
    }

    public synchronized void write(Path path, Object value) throws IOException {
        String[] key = key(path);
        writeDocument(key[0], key[1], value);
    }

    public synchronized <T> List<T> readFolder(String folder, Class<T> type) throws IOException {
        List<T> result = new ArrayList<>();
        for (Entry<T> entry : readFolderEntries(folder, type)) {
            result.add(entry.value);
        }
        return result;
    }

    public synchronized <T> List<Entry<T>> readFolderEntries(String folder, Class<T> type) throws IOException {
        List<Entry<T>> result = new ArrayList<>();
        String sql = "SELECT item_key, payload FROM documents WHERE folder = ? ORDER BY item_key";
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, folder);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    try {
                        result.add(new Entry<>(rows.getString(1), mapper.readValue(rows.getString(2), type)));
                    } catch (IOException invalidDocument) {
                        log.error("跳过损坏的 SQLite 数据文档，目录={} key={}", folder, rows.getString(1), invalidDocument);
                    }
                }
            }
            return result;
        } catch (SQLException exception) {
            throw new IOException("SQLite 查询失败: " + folder, exception);
        }
    }

    public synchronized void delete(Path path) throws IOException {
        String[] key = key(path);
        deleteDocument(key[0], key[1]);
    }

    public synchronized void deleteDocument(String folder, String itemKey) throws IOException {
        String sql = "DELETE FROM documents WHERE folder = ? AND item_key = ?";
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, folder);
            statement.setString(2, itemKey);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IOException("SQLite 删除失败: " + folder + "/" + itemKey, exception);
        }
    }

    public synchronized void writeDocument(String folder, String itemKey, Object value) throws IOException {
        String payload = mapper.writeValueAsString(value);
        String sql = "INSERT INTO documents(folder, item_key, payload, updated_at) VALUES(?,?,?,datetime('now')) " +
                "ON CONFLICT(folder, item_key) DO UPDATE SET payload = excluded.payload, updated_at = excluded.updated_at";
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, folder);
            statement.setString(2, itemKey);
            statement.setString(3, payload);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IOException("SQLite 写入失败: " + folder + "/" + itemKey, exception);
        }
    }

    public synchronized void moveDocument(String folder, String fromKey, String toKey) throws IOException {
        String sql = "UPDATE documents SET item_key = ? WHERE folder = ? AND item_key = ?";
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, toKey);
            statement.setString(2, folder);
            statement.setString(3, fromKey);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IOException("SQLite 移动文档失败: " + folder + "/" + fromKey + " -> " + toKey, exception);
        }
    }

    private String find(Path path) throws IOException {
        String[] key = key(path);
        String sql = "SELECT payload FROM documents WHERE folder = ? AND item_key = ?";
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, key[0]);
            statement.setString(2, key[1]);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? rows.getString(1) : null;
            }
        } catch (SQLException exception) {
            throw new IOException("SQLite 查询失败: " + path, exception);
        }
    }

    private String[] key(Path path) {
        Path relative = root.relativize(path.toAbsolutePath().normalize());
        if (relative.getNameCount() != 2) {
            throw new IllegalArgumentException("路径格式不符合 folder/key.json: " + path);
        }
        String folder = relative.getName(0).toString();
        String file = relative.getName(1).toString();
        if (!file.endsWith(".json")) {
            throw new IllegalArgumentException("非 json 文件: " + path);
        }
        return new String[]{folder, file.substring(0, file.length() - 5)};
    }

    private Connection connection() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + database);
    }

    public static final class Entry<T> {
        public final String key;
        public final T value;

        public Entry(String key, T value) {
            this.key = key;
            this.value = value;
        }
    }
}
