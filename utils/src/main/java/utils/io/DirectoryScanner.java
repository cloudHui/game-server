package utils.io;

import java.io.File;
import java.util.Map;

public class DirectoryScanner {

    public static void listFilesInDirectory(String directoryPath, Map<String, Integer> path) {
        File directory = new File(directoryPath);
        if (directory.exists() && directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile()) {
                        path.put(file.getAbsolutePath(), 1);
                    } else if (file.isDirectory()) {
                        listFilesInDirectory(file.getAbsolutePath(), path);
                    }
                }
            }
        } else {
            System.out.println("The provided path is not a directory or does not exist.");
        }
    }

    public static void listFiles(String directoryPath, Map<String, Long> fileMap) {
        File directory = new File(directoryPath);
        if (directory.exists() && directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile()) {
                        fileMap.put(file.getName(), file.lastModified());
                    } else if (file.isDirectory()) {
                        listFiles(file.getAbsolutePath(), fileMap);
                    }
                }
            }
        } else {
            throw new RuntimeException("no file");
        }
    }
}
