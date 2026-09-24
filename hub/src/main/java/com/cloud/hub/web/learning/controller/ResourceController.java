package com.cloud.hub.web.learning.controller;

import com.cloud.hub.storage.DataPathResolver;
import com.cloud.hub.web.learning.service.AuthService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 学习中心多媒体静态资源文件上传、下载与浏览控制器。
 *
 * @author cloud
 */
@RestController
@RequestMapping("/api/learning/resources")
public class ResourceController {

    private static final List<String> ALLOWED = Arrays.asList(
            "pdf", "txt", "png", "jpg", "jpeg", "gif", "webp", "mp3", "wav", "ogg", "mp4");

    private final Path root;
    private final AuthService auth;

    public ResourceController(@Value("${family-learning.resource-dir}") String resourceDir,
                              AuthService auth,
                              DataPathResolver paths) {
        this.root = paths.resolve(resourceDir);
        this.auth = auth;
    }

    /**
     * 容器初始化确保学科资源目录结构存在。
     */
    @PostConstruct
    public void init() throws IOException {
        Files.createDirectories(root);
        for (String folder : Arrays.asList("chinese", "math", "english", "history", "chemistry",
                "picture-books", "poems", "worksheets")) {
            Files.createDirectories(root.resolve(folder));
        }
    }

    /**
     * 列出家庭上传的多媒体资源文件（排除预装资源包）。
     */
    @GetMapping
    public List<ResourceInfo> list(@RequestHeader(value = "X-Session-Token", required = false) String token,
                                   @RequestParam(required = false) String subject) throws Exception {
        requireReadAccess(token, subject == null ? "" : subject);
        Path base = subject == null || subject.isEmpty() ? root : safePath(subject);
        if (!Files.isDirectory(base)) {
            return new ArrayList<>();
        }
        List<ResourceInfo> result = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(base, 4)) {
            paths.filter(Files::isRegularFile)
                    .filter(this::allowed)
                    .filter(path -> !isLibraryPack(root.relativize(path).toString().replace('\\', '/')))
                    .forEach(path -> collectResourceInfo(path, result));
        }
        result.sort(Comparator.comparingLong((ResourceInfo item) -> item.modifiedAt).reversed());
        return result;
    }

    private void collectResourceInfo(Path path, List<ResourceInfo> result) {
        try {
            result.add(new ResourceInfo(
                    root.relativize(path).toString().replace('\\', '/'),
                    Files.size(path),
                    Files.getLastModifiedTime(path).toMillis()));
        } catch (IOException ignored) {
        }
    }

    /**
     * 获取或下载指定的资源文件流。
     */
    @GetMapping("/file")
    public ResponseEntity<FileSystemResource> file(
            @RequestHeader(value = "X-Session-Token", required = false) String token,
            @RequestParam String path,
            @RequestParam(defaultValue = "false") boolean download) throws Exception {
        requireReadAccess(token, path);
        Path file = safePath(path);
        if (!Files.isRegularFile(file) || !allowed(file)) {
            throw new IllegalArgumentException("找不到资源文件");
        }
        String type = Files.probeContentType(file);
        MediaType mediaType;
        try {
            mediaType = type == null ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(type);
        } catch (Exception ignored) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }
        ResponseEntity.BodyBuilder response = ResponseEntity.ok().contentType(mediaType);
        if (download) {
            String encoded = URLEncoder.encode(file.getFileName().toString(), StandardCharsets.UTF_8.name())
                    .replace("+", "%20");
            response.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded);
        }
        return response.body(new FileSystemResource(file));
    }

    /**
     * 管理员上传学科资源文件。
     */
    @PostMapping
    public ResourceInfo upload(@RequestHeader("X-Session-Token") String token,
                               @RequestParam String subject,
                               @RequestPart("file") MultipartFile upload) throws Exception {
        auth.requireAdmin(token);
        if (upload.isEmpty() || upload.getSize() > 50L * 1024 * 1024) {
            throw new IllegalArgumentException("文件为空或超过50MB");
        }
        String name = Paths.get(upload.getOriginalFilename() == null ? "resource" : upload.getOriginalFilename())
                .getFileName().toString();
        Path target = safePath(subject).resolve(name).normalize();
        if (!target.startsWith(root) || !allowed(target)) {
            throw new IllegalArgumentException("不支持的文件类型");
        }
        Files.createDirectories(target.getParent());
        upload.transferTo(target.toFile());
        return new ResourceInfo(root.relativize(target).toString().replace('\\', '/'), Files.size(target),
                Files.getLastModifiedTime(target).toMillis());
    }

    /**
     * 管理员删除指定的资源文件。
     */
    @DeleteMapping
    public Map<String, Object> delete(@RequestHeader("X-Session-Token") String token,
                                      @RequestParam String path) throws Exception {
        auth.requireAdmin(token);
        Path file = safePath(path);
        if (!Files.deleteIfExists(file)) {
            throw new IllegalArgumentException("找不到资源文件");
        }
        return Collections.singletonMap("message", "资源已删除");
    }

    private void requireReadAccess(String token, String path) throws Exception {
        String normalized = path.replace('\\', '/').toLowerCase();
        if (normalized.startsWith("english/kids") || normalized.startsWith("english/vocab")
                || normalized.startsWith("chinese") || normalized.startsWith("poems")) {
            return;
        }
        auth.requirePermission(token, "RESOURCES");
    }

    private boolean isLibraryPack(String relativePath) {
        String p = relativePath.toLowerCase();
        return p.startsWith("english/kids/") || p.startsWith("english/vocab/");
    }

    private Path safePath(String relative) {
        Path path = root.resolve(relative).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("非法资源路径");
        }
        return path;
    }

    private boolean allowed(Path file) {
        String name = file.getFileName().toString().toLowerCase();
        int dot = name.lastIndexOf('.');
        return dot > 0 && ALLOWED.contains(name.substring(dot + 1));
    }

    public static class ResourceInfo {
        public final String path;
        public final long size;
        public final long modifiedAt;

        public ResourceInfo(String path, long size, long modifiedAt) {
            this.path = path;
            this.size = size;
            this.modifiedAt = modifiedAt;
        }
    }
}
