package com.gamer.data.map.grid;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 地图文件目录 Module：一次扫描后分类地图文件与关卡文件。
 */
public final class MapFileCatalogModule {

    /** 分类后的文件名快照。 */
    public static final class Catalog {
        public final List<String> mapFiles;
        public final List<String> levelFiles;

        private Catalog(List<String> mapFiles, List<String> levelFiles) {
            this.mapFiles = Collections.unmodifiableList(mapFiles);
            this.levelFiles = Collections.unmodifiableList(levelFiles);
        }
    }

    private MapFileCatalogModule() {}

    /**
     * 扫描目录并按命名约定分类。
     *
     * @param directory
     *            地图目录
     * @return 文件目录快照
     */
    public static Catalog scan(File directory) {
        List<String> mapFiles = new ArrayList<>();
        List<String> levelFiles = new ArrayList<>();
        if (directory != null && directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (!file.isFile()) {
                        continue;
                    }
                    String name = file.getName();
                    if (name.endsWith("_Level.txt")) {
                        levelFiles.add(name);
                    } else if (name.toLowerCase().endsWith(".txt")) {
                        mapFiles.add(name);
                    }
                }
            }
        }
        Comparator<String> comparator = String::compareToIgnoreCase;
        mapFiles.sort(comparator);
        levelFiles.sort(comparator);
        return new Catalog(mapFiles, levelFiles);
    }
}
