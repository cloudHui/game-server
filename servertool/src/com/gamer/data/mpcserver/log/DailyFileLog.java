package com.gamer.data.mpcserver.log;

import java.io.BufferedWriter;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.Date;

import com.gamer.data.log.Log;

/** 线程安全的每日文件日志。 */
public final class DailyFileLog implements Log {
    private static final long FLUSH_INTERVAL = 2000L;

    private final File directory;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
    private final SimpleDateFormat dayFormat = new SimpleDateFormat("yyyy-MM-dd");
    private String day;
    private BufferedWriter writer;
    private long lastFlush;

    public DailyFileLog(File directory) {
        this.directory = directory;
    }

    @Override
    public void logMessage(String message) {
        logMessage(message, false);
    }

    @Override
    public synchronized void logMessage(String message, boolean redShow) {
        String line = timeFormat.format(new Date()) + " " + (message == null ? "null" : message);
        System.err.println(line);
        try {
            open();
            writer.write(line);
            writer.newLine();
            long now = System.currentTimeMillis();
            if (now - lastFlush >= FLUSH_INTERVAL) {
                writer.flush();
                lastFlush = now;
            }
        } catch (Exception e) {
            System.err.println(timeFormat.format(new Date()) + " [LOG_WRITE_FAIL] " + e);
        }
    }

    public synchronized void close() {
        try {
            if (writer != null) {
                writer.close();
            }
        } catch (Exception ignored) {
        } finally {
            writer = null;
            day = null;
        }
    }

    private void open() throws Exception {
        String today = dayFormat.format(new Date());
        if (writer != null && today.equals(day)) {
            return;
        }
        close();
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IllegalStateException("无法创建日志目录: " + directory);
        }
        writer = Files.newBufferedWriter(new File(directory, today + ".log").toPath(), StandardCharsets.UTF_8,
            StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        day = today;
        lastFlush = System.currentTimeMillis();
    }
}
