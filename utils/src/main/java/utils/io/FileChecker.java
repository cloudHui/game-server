package utils.io;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

public class FileChecker {

    private static final Logger LOGGER = LoggerFactory.getLogger(FileChecker.class);

    public static long lastModifiedTime = System.currentTimeMillis();

    public static boolean hasNewFiles(String directoryPath) {
        long start = System.currentTimeMillis();
        File dir = new File(directoryPath);

        if (!dir.exists() || !dir.isDirectory()) {
            return false;
        }

        File[] files = dir.listFiles();
        if (files == null) {
            return true;
        }
        for (File file : files) {
            if (file.lastModified() > lastModifiedTime) {
                return true;
            }
        }
        LOGGER.info("hasNewFiles:{} cost:{}ms", directoryPath, System.currentTimeMillis() - start);
        return false;
    }
}
