package com.cloud.hub.web.photo.service;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.cloud.hub.web.photo.config.PhotoProperties;
import com.cloud.hub.web.photo.model.PhotoException;
import com.cloud.hub.web.photo.model.PhotoRecord;
import com.cloud.hub.web.photo.repository.PhotoRepository;
import com.cloud.hub.web.photo.storage.PhotoCache;
import com.cloud.hub.web.service.UserService;
import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;

/**
 * 家庭相册业务核心服务。
 * <p>
 * 提供图片上传预检、EXIF 拍摄时间与朝向自动纠正、高保真缩略图生成、
 * ZIP 归档存储以及 LRU 缓存热解压等全流程管理。
 *
 * @author cloud
 */
@Service
public class PhotoService {

    private static final Logger log = LoggerFactory.getLogger(PhotoService.class);
    private final PhotoProperties p;
    private final PhotoRepository repo;
    private final PhotoCache cache;
    private final Object archiveLock = new Object();
    private final ConcurrentHashMap<Long, Object> extractLocks = new ConcurrentHashMap<>();
    private final Path archives;
    private final Path thumbs;
    private final Path staging;

    public PhotoService(PhotoProperties p, PhotoRepository repo, PhotoCache cache) throws IOException {
        this.p = p;
        this.repo = repo;
        this.cache = cache;
        this.archives = dir(p.getArchiveDir());
        this.thumbs = dir(p.getThumbnailDir());
        this.staging = dir(p.getStagingDir());
        if (archives.equals(thumbs) || archives.equals(Paths.get(p.getCacheDir()).toAbsolutePath().normalize())) {
            throw new IllegalStateException("图片库目录不能相同");
        }
        if (p.getCacheMaxFiles() < 1 || p.getMaxFilesPerRequest() < 1) {
            throw new IllegalStateException("图片库数量配置必须为正数");
        }
    }

    private Path dir(String value) throws IOException {
        Path d = Paths.get(value).toAbsolutePath().normalize();
        Files.createDirectories(d);
        if (!Files.isWritable(d)) {
            throw new IOException("目录不可写: " + d);
        }
        return d;
    }

    /**
     * 上传单张图片（拆分子方法，单方法 <= 35 行）。
     *
     * @param upload 上传的文件对象
     * @param user   当前上传用户
     * @return 图片对外视图字典
     */
    public Map<String, Object> upload(MultipartFile upload, UserService.UserInfo user) {
        Path temp = null;
        Path thumb = null;
        try {
            validateUploadFile(upload);
            String original = safeOriginal(upload.getOriginalFilename());
            temp = saveToStaging(upload);
            ImageData image = readImage(temp);
            BufferedImage oriented = processOrientedImage(temp, image);
            Capture capture = capture(metadata(temp), safeLastModified(upload));

            String token = UUID.randomUUID().toString().replace("-", "");
            String thumbRelative = capture.path() + "/" + token + ".jpg";
            thumb = resolveUnder(thumbs, thumbRelative);
            Files.createDirectories(thumb.getParent());
            writeThumbnail(oriented, thumb);

            ArchiveRef ar = archive(temp, capture, token + "." + image.extension);
            PhotoRecord r = buildAndSaveRecord(temp, user, original, image, oriented, capture, ar, thumbRelative);
            log.info("图片上传成功 photoId={}, user={}, size={}", r.id, user.getUsername(), r.originalSize);
            return r.publicView();
        } catch (PhotoException e) {
            cleanup(thumb);
            throw e;
        } catch (Exception e) {
            cleanup(thumb);
            log.warn("图片上传处理失败: {}", e.getMessage());
            throw new PhotoException(500, "图片处理失败", e);
        } finally {
            cleanup(temp);
        }
    }

    private void validateUploadFile(MultipartFile upload) {
        if (upload == null || upload.isEmpty()) {
            throw new PhotoException(400, "空文件");
        }
        if (upload.getSize() > p.getMaxFileBytes()) {
            throw new PhotoException(413, "文件超过大小限制");
        }
    }

    private Path saveToStaging(MultipartFile upload) throws IOException {
        Path temp = Files.createTempFile(staging, "upload-", ".tmp");
        try (InputStream in = upload.getInputStream(); OutputStream out = Files.newOutputStream(temp)) {
            copyLimited(in, out, p.getMaxFileBytes());
        }
        return temp;
    }

    private BufferedImage processOrientedImage(Path temp, ImageData image) {
        Metadata metadata = metadata(temp);
        int orientation = orientation(metadata);
        BufferedImage oriented = orient(image.image, orientation);
        long pixels = (long) oriented.getWidth() * oriented.getHeight();
        if (pixels > p.getMaxPixels()) {
            throw new PhotoException(400, "图片像素超过限制");
        }
        return oriented;
    }

    private PhotoRecord buildAndSaveRecord(Path temp, UserService.UserInfo user, String original, ImageData image,
                                           BufferedImage oriented, Capture capture, ArchiveRef ar,
                                           String thumbRelative) throws Exception {
        PhotoRecord r = new PhotoRecord();
        r.displayName = displayName(original);
        r.originalName = original;
        r.ownerUserId = user.getUserId();
        r.ownerUsername = user.getUsername();
        r.capturedAt = capture.time;
        r.capturedAtSource = capture.source;
        r.capturedAtRaw = capture.raw;
        r.uploadedAt = System.currentTimeMillis();
        r.mediaType = image.mediaType;
        r.extension = image.extension;
        r.width = oriented.getWidth();
        r.height = oriented.getHeight();
        r.originalSize = Files.size(temp);
        r.checksum = sha256(temp);
        r.archivePath = ar.path;
        r.archiveEntry = ar.entry;
        r.thumbnailPath = thumbRelative;
        r.id = repo.insert(r);
        return r;
    }

    /**
     * 分页查询相册列表。
     */
    public Map<String, Object> list(UserService.UserInfo user, int page, int size, String owner) throws Exception {
        page = Math.max(1, page);
        size = Math.max(1, Math.min(100, size));
        Long filter = visibleOwner(user);
        String name = user.isAdmin() ? owner : null;
        List<PhotoRecord> rows = repo.list(filter, page, size, name);
        List<Map<String, Object>> items = new ArrayList<>();
        for (PhotoRecord r : rows) {
            items.add(r.publicView());
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("items", items);
        out.put("page", page);
        out.put("pageSize", size);
        out.put("total", repo.count(filter, name));
        out.put("visibilityMode", repo.visibility());
        return out;
    }

    private Long visibleOwner(UserService.UserInfo u) throws Exception {
        return !u.isAdmin() && "OWN".equals(repo.visibility()) ? (long) u.getUserId() : null;
    }

    public PhotoRecord requireVisible(long id, UserService.UserInfo u) throws Exception {
        PhotoRecord r = repo.find(id);
        if (r == null) {
            throw new PhotoException(404, "图片不存在");
        }
        if (!u.isAdmin() && "OWN".equals(repo.visibility()) && r.ownerUserId != u.getUserId()) {
            throw new PhotoException(403, "无权查看该图片");
        }
        return r;
    }

    public byte[] thumbnail(long id, UserService.UserInfo u) throws Exception {
        PhotoRecord r = requireVisible(id, u);
        Path f = resolveUnder(thumbs, r.thumbnailPath);
        if (!Files.isRegularFile(f)) {
            throw new PhotoException(404, "缩略图不存在");
        }
        return Files.readAllBytes(f);
    }

    public Original original(long id, UserService.UserInfo u) throws Exception {
        PhotoRecord r = requireVisible(id, u);
        Object lock = extractLocks.computeIfAbsent(id, k -> new Object());
        try {
            synchronized (lock) {
                Path f = cache.get(id);
                if (f == null) {
                    f = extractToCache(id, r);
                }
                return new Original(cache.acquire(id), r.mediaType, r.originalName);
            }
        } finally {
            extractLocks.remove(id, lock);
        }
    }

    private Path extractToCache(long id, PhotoRecord r) throws IOException {
        Path f = cache.target(id, r.extension);
        Path tmp = Files.createTempFile(Paths.get(p.getCacheDir()).toAbsolutePath().normalize(), id + "-", ".tmp");
        try {
            extract(r, tmp);
            try {
                Files.move(tmp, f, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING);
            }
            cache.commit(id, f);
            return f;
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    public void rename(long id, String name, UserService.UserInfo u) throws Exception {
        PhotoRecord r = repo.find(id);
        requireManage(r, u);
        repo.rename(id, cleanName(name));
    }

    public void delete(long id, UserService.UserInfo u) throws Exception {
        PhotoRecord r = repo.find(id);
        requireManage(r, u);
        repo.delete(id);
        Files.deleteIfExists(resolveUnder(thumbs, r.thumbnailPath));
        cache.remove(id);
        log.info("图片逻辑删除 photoId={}, by={}", id, u.getUsername());
    }

    private void requireManage(PhotoRecord r, UserService.UserInfo u) {
        if (r == null) {
            throw new PhotoException(404, "图片不存在");
        }
        if (!u.isAdmin() && r.ownerUserId != u.getUserId()) {
            throw new PhotoException(403, "只能管理自己上传的图片");
        }
    }

    public Map<String, Object> adminInfo() throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("visibilityMode", repo.visibility());
        m.put("archiveDir", archives.toString());
        m.put("thumbnailDir", thumbs.toString());
        m.put("cacheDir", Paths.get(p.getCacheDir()).toAbsolutePath().normalize().toString());
        m.put("cacheFiles", cache.size());
        m.put("cacheMaxFiles", p.getCacheMaxFiles());
        m.put("photoCount", repo.count(null, null));
        m.put("archiveBytes", treeSize(archives));
        m.put("thumbnailBytes", treeSize(thumbs));
        return m;
    }

    public void visibility(String mode, String by) throws Exception {
        if (!"ALL".equals(mode) && !"OWN".equals(mode)) {
            throw new PhotoException(400, "查看范围只能是 ALL 或 OWN");
        }
        repo.visibility(mode, by);
        log.info("图片查看范围变更 mode={}, by={}", mode, by);
    }

    public int clearCache() throws IOException {
        return cache.clear();
    }

    private long treeSize(Path root) throws IOException {
        long n = 0;
        try (java.util.stream.Stream<Path> s = Files.walk(root)) {
            Iterator<Path> i = s.filter(Files::isRegularFile).iterator();
            while (i.hasNext()) {
                n += Files.size(i.next());
            }
        }
        return n;
    }

    private void extract(PhotoRecord r, Path target) throws IOException {
        Path zip = resolveUnder(archives, r.archivePath);
        if (!Files.isRegularFile(zip)) {
            throw new PhotoException(404, "原图归档不存在");
        }
        Map<String, String> env = Collections.emptyMap();
        try (FileSystem fs = FileSystems.newFileSystem(URI.create("jar:" + zip.toUri()), env)) {
            Path e = fs.getPath("/" + r.archiveEntry).normalize();
            if (!e.startsWith("/images/") || !Files.isRegularFile(e)) {
                throw new PhotoException(404, "原图条目不存在");
            }
            Files.copy(e, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private ArchiveRef archive(Path source, Capture c, String entry) throws IOException {
        synchronized (archiveLock) {
            String folder = c.path();
            Path dir = resolveUnder(archives, folder);
            Files.createDirectories(dir);
            int index = 1;
            Path zip;
            while (true) {
                zip = dir.resolve(String.format("photos-%s-%03d.zip", folder.replace("/", ""), index));
                if (!Files.exists(zip) || Files.size(zip) + Files.size(source) <= p.getArchiveMaxBytes()) {
                    break;
                }
                index++;
            }
            Map<String, String> env = new HashMap<>();
            if (!Files.exists(zip)) {
                env.put("create", "true");
            }
            try (FileSystem fs = FileSystems.newFileSystem(URI.create("jar:" + zip.toUri()), env)) {
                Path target = fs.getPath("/images/" + entry).normalize();
                if (!target.startsWith("/images/")) {
                    throw new IOException("非法 ZIP 条目");
                }
                Files.createDirectories(target.getParent());
                Files.copy(source, target);
            }
            return new ArchiveRef(folder + "/" + zip.getFileName(), "images/" + entry);
        }
    }

    private ImageData readImage(Path f) throws IOException {
        try (ImageInputStream in = ImageIO.createImageInputStream(f.toFile())) {
            Iterator<ImageReader> it = ImageIO.getImageReaders(in);
            if (!it.hasNext()) {
                throw new PhotoException(400, "不是支持的图片格式");
            }
            ImageReader reader = it.next();
            try {
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                boolean jpeg = format.equals("jpeg") || format.equals("jpg");
                boolean png = format.equals("png");
                boolean webp = format.equals("webp");
                if (!jpeg && !png && !webp) {
                    throw new PhotoException(400, "当前仅支持 JPEG、PNG、WebP");
                }
                reader.setInput(in, true, true);
                int w = reader.getWidth(0);
                int h = reader.getHeight(0);
                if ((long) w * h > p.getMaxPixels()) {
                    throw new PhotoException(400, "图片像素超过限制");
                }
                BufferedImage image = reader.read(0);
                return new ImageData(image, jpeg ? "jpg" : png ? "png" : "webp",
                        jpeg ? "image/jpeg" : png ? "image/png" : "image/webp");
            } finally {
                reader.dispose();
            }
        }
    }

    private Metadata metadata(Path f) {
        try {
            return ImageMetadataReader.readMetadata(f.toFile());
        } catch (Exception e) {
            return new Metadata();
        }
    }

    private long safeLastModified(MultipartFile upload) {
        try {
            return upload.getResource().lastModified();
        } catch (Exception ignored) {
            return 0;
        }
    }

    private int orientation(Metadata m) {
        ExifIFD0Directory d = m.getFirstDirectoryOfType(ExifIFD0Directory.class);
        return d == null ? 1 : d.getInteger(ExifIFD0Directory.TAG_ORIENTATION) == null ? 1
                : d.getInteger(ExifIFD0Directory.TAG_ORIENTATION);
    }

    private Capture capture(Metadata m, long fileTime) {
        ExifSubIFDDirectory d = m.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
        Date date = null;
        String source = null;
        String raw = null;
        if (d != null) {
            date = d.getDateOriginal(TimeZone.getTimeZone(p.getDefaultZone()));
            if (date != null) {
                source = "EXIF_ORIGINAL";
                raw = d.getString(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL);
            } else {
                date = d.getDateDigitized(TimeZone.getTimeZone(p.getDefaultZone()));
                if (date != null) {
                    source = "EXIF_DIGITIZED";
                    raw = d.getString(ExifSubIFDDirectory.TAG_DATETIME_DIGITIZED);
                }
            }
        }
        if (date == null) {
            ExifIFD0Directory z = m.getFirstDirectoryOfType(ExifIFD0Directory.class);
            if (z != null) {
                date = z.getDate(ExifIFD0Directory.TAG_DATETIME, TimeZone.getTimeZone(p.getDefaultZone()));
                if (date != null) {
                    source = "EXIF_MODIFIED";
                    raw = z.getString(ExifIFD0Directory.TAG_DATETIME);
                }
            }
        }
        if (date == null && fileTime > 0) {
            date = new Date(fileTime);
            source = "FILE_TIME";
        }
        if (date == null) {
            date = new Date();
            source = "UPLOAD_TIME";
        }
        return new Capture(date.getTime(), source, raw);
    }

    private BufferedImage orient(BufferedImage src, int o) {
        if (o < 2 || o > 8) {
            return src;
        }
        int w = src.getWidth();
        int h = src.getHeight();
        boolean swap = o >= 5 && o <= 8;
        BufferedImage out = new BufferedImage(swap ? h : w, swap ? w : h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        AffineTransform t = buildTransform(o, w, h);
        g.drawImage(src, t, null);
        g.dispose();
        return out;
    }

    private AffineTransform buildTransform(int o, int w, int h) {
        AffineTransform t = new AffineTransform();
        switch (o) {
            case 2:
                t.translate(w, 0);
                t.scale(-1, 1);
                break;
            case 3:
                t.translate(w, h);
                t.rotate(Math.PI);
                break;
            case 4:
                t.translate(0, h);
                t.scale(1, -1);
                break;
            case 5:
                t.rotate(Math.PI / 2);
                t.scale(1, -1);
                break;
            case 6:
                t.translate(h, 0);
                t.rotate(Math.PI / 2);
                break;
            case 7:
                t.translate(h, w);
                t.rotate(Math.PI / 2);
                t.scale(-1, 1);
                break;
            case 8:
                t.translate(0, w);
                t.rotate(-Math.PI / 2);
                break;
        }
        return t;
    }

    private void writeThumbnail(BufferedImage src, Path target) throws IOException {
        double scale = Math.min(1d, Math.min((double) p.getThumbnailMaxWidth() / src.getWidth(),
                (double) p.getThumbnailMaxHeight() / src.getHeight()));
        int w = Math.max(1, (int) Math.round(src.getWidth() * scale));
        int h = Math.max(1, (int) Math.round(src.getHeight() * scale));
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        if (!ImageIO.write(out, "jpg", target.toFile())) {
            throw new IOException("无法生成缩略图");
        }
    }

    private String safeOriginal(String n) {
        n = n == null ? "photo" : n.replace('\\', '/');
        n = n.substring(n.lastIndexOf('/') + 1).trim();
        return n.isEmpty() ? "photo" : n.length() > 255 ? n.substring(n.length() - 255) : n;
    }

    private String displayName(String n) {
        int dot = n.lastIndexOf('.');
        return cleanName(dot > 0 ? n.substring(0, dot) : n);
    }

    private String cleanName(String n) {
        if (n == null) {
            throw new PhotoException(400, "名称不能为空");
        }
        String v = n.replaceAll("[\\p{Cntrl}]", "").trim();
        if (v.isEmpty() || v.length() > 100) {
            throw new PhotoException(400, "名称长度需为 1-100 个字符");
        }
        return v;
    }

    private Path resolveUnder(Path root, String relative) {
        Path x = root.resolve(relative).normalize();
        if (!x.startsWith(root)) {
            throw new PhotoException(400, "非法存储路径");
        }
        return x;
    }

    private void copyLimited(InputStream in, OutputStream out, long max) throws IOException {
        byte[] b = new byte[8192];
        long n = 0;
        for (int r; (r = in.read(b)) >= 0;) {
            n += r;
            if (n > max) {
                throw new PhotoException(413, "文件超过大小限制");
            }
            out.write(b, 0, r);
        }
    }

    private String sha256(Path f) throws Exception {
        MessageDigest d = MessageDigest.getInstance("SHA-256");
        try (InputStream in = Files.newInputStream(f)) {
            byte[] b = new byte[8192];
            for (int n; (n = in.read(b)) >= 0;) {
                d.update(b, 0, n);
            }
        }
        StringBuilder s = new StringBuilder();
        for (byte b : d.digest()) {
            s.append(String.format("%02x", b));
        }
        return s.toString();
    }

    private void cleanup(Path p) {
        if (p != null) {
            try {
                Files.deleteIfExists(p);
            } catch (IOException ignored) {
            }
        }
    }

    private static class ImageData {
        BufferedImage image;
        String extension;
        String mediaType;

        ImageData(BufferedImage i, String e, String m) {
            this.image = i;
            this.extension = e;
            this.mediaType = m;
        }
    }

    private static class ArchiveRef {
        String path;
        String entry;

        ArchiveRef(String p, String e) {
            this.path = p;
            this.entry = e;
        }
    }

    private class Capture {
        long time;
        String source;
        String raw;

        Capture(long t, String s, String r) {
            this.time = t;
            this.source = s;
            this.raw = r;
        }

        String path() {
            return DateTimeFormatter.ofPattern("yyyy/MM")
                    .format(Instant.ofEpochMilli(time).atZone(ZoneId.of(p.getDefaultZone())));
        }
    }

    public static class Original {
        public final PhotoCache.Lease lease;
        public final String mediaType;
        public final String name;

        Original(PhotoCache.Lease l, String m, String n) {
            this.lease = l;
            this.mediaType = m;
            this.name = n;
        }
    }
}
