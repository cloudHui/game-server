package manager.service;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class LogReader {
    public String tail(Path file, int maxBytes) throws IOException {
        if (!Files.isRegularFile(file)) throw new IOException("日志不存在: " + file);
        try (RandomAccessFile input = new RandomAccessFile(file.toFile(), "r")) {
            long start = Math.max(0, input.length() - maxBytes);
            input.seek(start);
            byte[] bytes = new byte[(int) (input.length() - start)];
            input.readFully(bytes);
            String text = new String(bytes, StandardCharsets.UTF_8);
            if (start == 0) return text;
            int firstLine = text.indexOf('\n');
            return firstLine < 0 ? text : text.substring(firstLine + 1);
        }
    }
}
