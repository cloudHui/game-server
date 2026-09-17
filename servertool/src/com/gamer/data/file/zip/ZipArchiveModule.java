package com.gamer.data.file.zip;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * 解压 / 压缩 / 递归删除：进度按文件数回传，供部署与客户端 GD 落地共用。
 */
public final class ZipArchiveModule {

    /** 默认 zip 条目编码（与现有客户端包一致）。 */
    public static final Charset ZIP_CHARSET = Charset.forName("GBK");

    private ZipArchiveModule() {}

    /**
     * 解压 zip 到目标目录：目录已存在则先删空再建。
     *
     * @param zipFile
     *            zip 文件
     * @param destDir
     *            解压目标目录
     * @param charset
     *            条目名编码
     * @param listener
     *            进度与日志，可为 null
     * @return 写出的文件数
     * @throws IOException
     *             解压失败
     */
    public static int extract(File zipFile, File destDir, Charset charset, Listener listener) throws IOException {
        if (zipFile == null || !zipFile.isFile()) {
            throw new IOException("zip 不存在: " + (zipFile != null ? zipFile.getAbsolutePath() : "null"));
        }
        // 部署场景要干净目录；多包解到同一 log 请用 {@link #into}。
        if (destDir.exists() && !deleteRecursively(destDir, listener)) {
            throw new IOException("无法清空解压目录: " + destDir.getAbsolutePath());
        }
        return into(zipFile, destDir, charset, listener);
    }

    /**
     * 按扩展名解压 zip / tar.gz 到已有目录，不清空。
     *
     * @param archive  压缩包
     * @param destDir  解压目标
     * @param listener 进度与日志，可为 null
     * @throws IOException 不支持的格式或解压失败
     */
    public static void unpack(File archive, File destDir, Listener listener) throws IOException {
        if (archive == null || !archive.isFile()) {
            throw new IOException("压缩包不存在: " + (archive != null ? archive.getAbsolutePath() : "null"));
        }
        String name = archive.getName().toLowerCase();
        if (name.endsWith(".zip")) {
            into(archive, destDir, StandardCharsets.UTF_8, listener);
            return;
        }
        if (name.endsWith(".tar.gz")) {
            extractTarGz(archive, destDir, listener);
            return;
        }
        throw new IOException("不支持的压缩包: " + archive.getName());
    }

    /**
     * 解压 tar.gz 到目标目录，不清空已有内容。拒绝跳出目标目录的条目。
     *
     * @param archive  tar.gz 文件
     * @param destDir  解压目标目录
     * @param listener 进度与日志，可为 null
     * @throws IOException 解压失败
     */
    public static void extractTarGz(File archive, File destDir, Listener listener) throws IOException {
        if (archive == null || !archive.isFile()) {
            throw new IOException("tar.gz 不存在: " + (archive != null ? archive.getAbsolutePath() : "null"));
        }
        ensureDir(destDir);
        int count;
        try (InputStream gzip = new GZIPInputStream(Files.newInputStream(archive.toPath()))) {
            count = readTar(gzip, destDir, listener);
        }
        log(listener, "解压完成，共 " + count + " 个文件");
    }

    /**
     * 解压 zip 到已有目录，不清空。同名文件覆盖。
     *
     * @param zipFile
     *            zip 文件
     * @param destDir
     *            解压目标目录
     * @param charset
     *            条目名编码
     * @param listener
     *            进度与日志，可为 null
     * @return 写出的文件数
     * @throws IOException
     *             解压失败
     */
    public static int into(File zipFile, File destDir, Charset charset, Listener listener) throws IOException {
        if (zipFile == null || !zipFile.isFile()) {
            throw new IOException("zip 不存在: " + (zipFile != null ? zipFile.getAbsolutePath() : "null"));
        }
        ensureDir(destDir);
        Charset zipCharset = charset != null ? charset : ZIP_CHARSET;
        try (ZipFile zf = new ZipFile(zipFile, zipCharset)) {
            int totalFiles = countFileEntries(zf);
            log(listener, "压缩包共 " + totalFiles + " 个文件");
            progress(listener, "解压中", 0, totalFiles);
            Enumeration<? extends ZipEntry> entries = zf.entries();
            int count = 0;
            while (entries.hasMoreElements()) {
                checkpoint(listener);
                ZipEntry entry = entries.nextElement();
                File outFile = resolveSafeOutFile(destDir, entry.getName());
                if (entry.isDirectory()) {
                    if (!outFile.exists() && !outFile.mkdirs()) {
                        log(listener, "[警告] 无法创建目录: " + outFile.getAbsolutePath());
                    }
                    continue;
                }
                File parent = outFile.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) {
                    log(listener, "[警告] 无法创建目录: " + parent.getAbsolutePath());
                }
                try (InputStream is = zf.getInputStream(entry)) {
                    Files.copy(is, outFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
                count++;
                progress(listener, "解压中", count, totalFiles);
            }
            log(listener, "解压完成，共 " + count + " 个文件");
            return count;
        }
    }
    /**
     * 将目录内文件压成 zip（不含根目录名，相对路径入包）。
     *
     * @param sourceDir
     *            源目录
     * @param zipFile
     *            目标 zip（已存在则覆盖）
     * @param charset
     *            条目名编码
     * @param listener
     *            进度与日志，可为 null
     * @return 写入的文件数
     * @throws IOException
     *             压缩失败；源目录无文件时抛错且不覆盖已有 zip
     */
    public static int compressDirectory(File sourceDir, File zipFile, Charset charset, Listener listener)
        throws IOException {
        if (sourceDir == null || !sourceDir.isDirectory()) {
            throw new IOException("压缩源目录不存在: " + (sourceDir != null ? sourceDir.getAbsolutePath() : "null"));
        }
        File parent = zipFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("无法创建 zip 目录: " + parent.getAbsolutePath());
        }
        List<File> files = new ArrayList<>();
        collectFiles(sourceDir, files);
        // 空包仍会原子替换目标 zip，客户端配置会被掏空
        if (files.isEmpty()) {
            throw new IOException("压缩源目录为空，拒绝覆盖: " + sourceDir.getAbsolutePath());
        }
        File tmpZip = new File(zipFile.getAbsolutePath() + ".tmp");
        int count = writeTempZip(sourceDir, files, tmpZip, charset != null ? charset : ZIP_CHARSET, listener);
        replaceZipAtomically(tmpZip, zipFile, listener);
        log(listener, "压缩完成，共 " + count + " 个文件");
        return count;
    }

    /**
     * 把目录内文件写入临时 zip。
     *
     * @param sourceDir
     *            源目录
     * @param files
     *            文件列表
     * @param tmpZip
     *            临时 zip
     * @param charset
     *            条目编码
     * @param listener
     *            进度
     * @return 写入文件数
     */
    private static int writeTempZip(File sourceDir, List<File> files, File tmpZip, Charset charset, Listener listener)
        throws IOException {
        if (tmpZip.exists() && !tmpZip.delete()) {
            throw new IOException("无法删除旧临时 zip: " + tmpZip.getAbsolutePath());
        }
        int totalFiles = files.size();
        log(listener, "待压缩 " + totalFiles + " 个文件 -> " + tmpZip.getName());
        progress(listener, "压缩中", 0, totalFiles);
        int count = 0;
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(tmpZip.toPath()), charset)) {
            byte[] buffer = new byte[8192];
            for (File file : files) {
                writeZipEntry(zos, sourceDir, file, buffer);
                count++;
                progress(listener, "压缩中", count, totalFiles);
            }
        } catch (IOException e) {
            if (tmpZip.exists() && !tmpZip.delete()) {
                log(listener, "[警告] 压缩失败且无法删除临时 zip: " + tmpZip.getAbsolutePath());
            }
            throw e;
        }
        return count;
    }

    /**
     * 写入单个 zip 条目。
     *
     * @param zos
     *            输出流
     * @param sourceDir
     *            源根目录
     * @param file
     *            文件
     * @param buffer
     *            缓冲
     */
    private static void writeZipEntry(ZipOutputStream zos, File sourceDir, File file, byte[] buffer)
        throws IOException {
        zos.putNextEntry(new ZipEntry(toZipEntryName(sourceDir, file)));
        try (FileInputStream in = new FileInputStream(file)) {
            int read;
            while ((read = in.read(buffer)) >= 0) {
                zos.write(buffer, 0, read);
            }
        }
        zos.closeEntry();
    }

    /**
     * 临时 zip 覆盖正式文件。
     *
     * @param tmpZip
     *            临时文件
     * @param zipFile
     *            目标 zip
     * @param listener
     *            日志
     */
    private static void replaceZipAtomically(File tmpZip, File zipFile, Listener listener) throws IOException {
        try {
            Files.move(tmpZip.toPath(), zipFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log(listener, "无法覆盖目标 zip（可能被占用）: " + zipFile.getAbsolutePath());
            if (tmpZip.exists() && !tmpZip.delete()) {
                log(listener, "[警告] 无法删除临时 zip: " + tmpZip.getAbsolutePath());
            }
            throw new IOException("无法覆盖目标 zip（可能被占用）: " + zipFile.getAbsolutePath(), e);
        }
    }

    /**
     * 递归删除文件或目录。删除失败只打日志并返回 false。
     *
     * @param file
     *            目标
     * @param listener
     *            日志，可为 null
     * @return 全部删除成功
     */
    public static boolean deleteRecursively(File file, Listener listener) {
        if (file == null || !file.exists()) {
            return true;
        }
        boolean success = true;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    success = deleteRecursively(child, listener) && success;
                }
            }
        }
        if (!file.delete()) {
            log(listener, "[警告] 无法删除: " + file.getAbsolutePath());
            return false;
        }
        return success;
    }

    /**
     * 解析 tar 流写入 destDir。跳过硬链/符号链接，拒绝跳出目标目录。
     *
     * @param in
     *            tar 字节流
     * @param destDir
     *            解压根
     * @param listener
     *            进度
     * @return 文件数
     * @throws IOException
     *             截断或非法路径
     */
    private static int readTar(InputStream in, File destDir, Listener listener) throws IOException {
        byte[] header = new byte[512];
        String pendingName = null;
        int count = 0;
        while (readTarBlock(in, header)) {
            if (isZeroBlock(header)) {
                break;
            }
            checkpoint(listener);
            char type = (char) (header[156] & 0xff);
            long size = parseTarOctal(header);
            String name = pendingName != null ? pendingName : tarEntryName(header);
            pendingName = null;
            if (type == 'L') {
                pendingName = readTarString(in, size);
                skipTarPadding(in, size);
                continue;
            }
            if (type == 'x' || type == 'g' || name.isEmpty() || ".".equals(name) || "./".equals(name)) {
                skipTarEntry(in, size);
                continue;
            }
            if (name.startsWith("./")) {
                name = name.substring(2);
            }
            File outFile = resolveSafeOutFile(destDir, name);
            if (type == '5') {
                if (!outFile.exists() && !outFile.mkdirs()) {
                    log(listener, "[警告] 无法创建目录: " + outFile.getAbsolutePath());
                }
                skipTarEntry(in, size);
                continue;
            }
            if (type == '1' || type == '2') {
                log(listener, "跳过链接: " + name);
                skipTarEntry(in, size);
                continue;
            }
            File parent = outFile.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                throw new IOException("无法创建目录: " + parent.getAbsolutePath());
            }
            copyTarFile(in, outFile, size, listener);
            skipTarPadding(in, size);
            count++;
            progress(listener, "解压中", count, count);
        }
        return count;
    }

    /** @param in tar 流 @param block 512 字节 @return 读到块；流结束且未读到字节为 false */
    private static boolean readTarBlock(InputStream in, byte[] block) throws IOException {
        int n = 0;
        while (n < 512) {
            int r = in.read(block, n, 512 - n);
            if (r < 0) {
                if (n == 0) {
                    return false;
                }
                throw new IOException("tar 头截断");
            }
            n += r;
        }
        return true;
    }

    /** @param header 512 字节头 @return 全 0 */
    private static boolean isZeroBlock(byte[] header) {
        for (byte b : header) {
            if (b != 0) {
                return false;
            }
        }
        return true;
    }

    /** @param header tar 头 @return 条目名（name + prefix） */
    private static String tarEntryName(byte[] header) {
        String name = tarCString(header, 0, 100);
        String prefix = tarCString(header, 345, 155);
        if (prefix.isEmpty()) {
            return name;
        }
        return prefix + "/" + name;
    }

    /** @param data 块 @param off 起点 @param len 长度 @return 去掉 NUL 的 UTF-8 串 */
    private static String tarCString(byte[] data, int off, int len) {
        int end = off;
        int limit = off + len;
        while (end < limit && data[end] != 0) {
            end++;
        }
        return new String(data, off, end - off, StandardCharsets.UTF_8);
    }

    /**
     * @param header 头 @param off 起点 @param len 长度 @return 八进制或 GNU 二进制数值
     */
    private static long parseTarOctal(byte[] header) {
        if ((header[124] & 0x80) != 0) {
            long value = 0;
            for (int i = 124 + 1; i < 124 + 12; i++) {
                value = (value << 8) | (header[i] & 0xff);
            }
            return value;
        }
        long value = 0;
        int end = 124 + 12;
        int i = 124;
        while (i < end && (header[i] == 0 || header[i] == ' ')) {
            i++;
        }
        while (i < end && header[i] >= '0' && header[i] <= '7') {
            value = (value << 3) + (header[i] - '0');
            i++;
        }
        return value;
    }

    /** @param in tar 流 @param size 字节数 @return UTF-8 文本，去掉尾 NUL */
    private static String readTarString(InputStream in, long size) throws IOException {
        if (size < 0 || size > 4096) {
            throw new IOException("tar 长文件名过长: " + size);
        }
        byte[] raw = new byte[(int) size];
        readTarFully(in, raw);
        int end = raw.length;
        while (end > 0 && raw[end - 1] == 0) {
            end--;
        }
        return new String(raw, 0, end, StandardCharsets.UTF_8);
    }

    /** @param in tar 流 @param out 目标 @param size 字节数 @param listener 可空，用于暂停/结束 */
    private static void copyTarFile(InputStream in, File out, long size, Listener listener) throws IOException {
        byte[] buf = new byte[8192];
        try (OutputStream os = Files.newOutputStream(out.toPath())) {
            long remain = size;
            while (remain > 0) {
                checkpoint(listener);
                int n = in.read(buf, 0, (int) Math.min(buf.length, remain));
                if (n < 0) {
                    throw new IOException("tar 文件截断: " + out.getName());
                }
                os.write(buf, 0, n);
                remain -= n;
            }
        }
    }

    /** @param destDir 解压根 */
    private static void ensureDir(File destDir) throws IOException {
        if (!destDir.exists() && !destDir.mkdirs()) {
            throw new IOException("无法创建解压目录: " + destDir.getAbsolutePath());
        }
    }

    /** @param in tar 流 @param size 条目内容长度 */
    private static void skipTarEntry(InputStream in, long size) throws IOException {
        skipTarBytes(in, size);
        skipTarPadding(in, size);
    }

    /** @param in tar 流 @param size 已读内容长度 */
    private static void skipTarPadding(InputStream in, long size) throws IOException {
        long pad = (512 - (size % 512)) % 512;
        skipTarBytes(in, pad);
    }

    /** @param in tar 流 @param size 跳过字节 */
    private static void skipTarBytes(InputStream in, long size) throws IOException {
        byte[] buf = new byte[8192];
        long remain = size;
        while (remain > 0) {
            int n = in.read(buf, 0, (int) Math.min(buf.length, remain));
            if (n < 0) {
                throw new IOException("tar 内容截断");
            }
            remain -= n;
        }
    }

    /** @param in 流 @param buf 缓冲 */
    private static void readTarFully(InputStream in, byte[] buf) throws IOException {
        int n = 0;
        while (n < buf.length) {
            int r = in.read(buf, n, buf.length - n);
            if (r < 0) {
                throw new IOException("tar 内容截断");
            }
            n += r;
        }
    }

    /**
     * 统计 zip 内非目录条目数。
     *
     * @param zf
     *            zip
     * @return 文件数
     */
    private static int countFileEntries(ZipFile zf) {
        int totalFiles = 0;
        Enumeration<? extends ZipEntry> allEntries = zf.entries();
        while (allEntries.hasMoreElements()) {
            if (!allEntries.nextElement().isDirectory()) {
                totalFiles++;
            }
        }
        return totalFiles;
    }

    /**
     * 解析条目输出路径，拒绝跳出目标目录。
     *
     * @param destDir
     *            解压根
     * @param entryName
     *            条目名
     * @return 安全输出文件
     */
    private static File resolveSafeOutFile(File destDir, String entryName) throws IOException {
        File outFile = new File(destDir, entryName);
        String destPath = destDir.getCanonicalPath();
        String outPath = outFile.getCanonicalPath();
        if (!outPath.equals(destPath) && !outPath.startsWith(destPath + File.separator)) {
            throw new IOException("非法 zip 条目路径: " + entryName);
        }
        return outFile;
    }

    /**
     * 递归收集目录下文件。
     *
     * @param dir
     *            目录
     * @param files
     *            输出列表
     */
    private static void collectFiles(File dir, List<File> files) {
        File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (child.isDirectory()) {
                collectFiles(child, files);
            } else if (child.isFile()) {
                files.add(child);
            }
        }
    }

    /**
     * 生成 zip 内相对路径（正斜杠）。
     *
     * @param sourceDir
     *            源根
     * @param file
     *            文件
     * @return 条目名
     */
    private static String toZipEntryName(File sourceDir, File file) throws IOException {
        String root = sourceDir.getCanonicalPath();
        String path = file.getCanonicalPath();
        if (!path.startsWith(root)) {
            throw new IOException("压缩文件不在源目录内: " + path);
        }
        String relative = path.substring(root.length());
        if (relative.startsWith(File.separator)) {
            relative = relative.substring(File.separator.length());
        }
        return relative.replace('\\', '/');
    }

    /**
     * 写一行日志。
     *
     * @param listener
     *            可为 null
     * @param line
     *            文案
     */
    private static void log(Listener listener, String line) {
        if (listener != null) {
            listener.log(line);
        }
    }

    /**
     * 回传进度。
     *
     * @param listener
     *            可为 null
     * @param action
     *            阶段名
     * @param current
     *            当前
     * @param total
     *            总数
     */
    private static void progress(Listener listener, String action, int current, int total) {
        if (listener != null) {
            listener.progress(action, current, total);
        }
    }

    /** @param listener 可空 @throws IOException 用户结束解压 */
    private static void checkpoint(Listener listener) throws IOException {
        if (listener != null) {
            listener.checkpoint();
        }
    }

    /**
     * 解压/压缩进度与日志。
     */
    public interface Listener {
        /**
         * 追加一行日志。
         *
         * @param line
         *            日志文案
         */
        void log(String line);

        /**
         * 刷新进度（调用方决定是否节流展示）。
         *
         * @param action
         *            阶段名，如「解压中」
         * @param current
         *            已处理文件数
         * @param total
         *            总文件数
         */
        void progress(String action, int current, int total);

        /**
         * 每个条目或写块前调用。默认空操作。
         *
         * @throws IOException
         *             用户结束解压
         */
        default void checkpoint() throws IOException {}
    }

    /**
     * 节流：首尾、每 25 条或间隔超过 100ms 才刷新。
     */
    public static final class ProgressThrottle {
        /** 上次刷新时间。 */
        private long lastUpdateMillis;
        /** 上次刷新进度。 */
        private int lastProgress = -1;

        /**
         * @param current
         *            当前计数
         * @param total
         *            总数
         * @return 是否应刷新 UI
         */
        public boolean shouldUpdate(int current, int total) {
            long now = System.currentTimeMillis();
            boolean refresh = current <= 0 || current >= total || current % 25 == 0 || now - lastUpdateMillis >= 100L;
            if (refresh && current != lastProgress) {
                lastUpdateMillis = now;
                lastProgress = current;
                return true;
            }
            return false;
        }
    }
}
