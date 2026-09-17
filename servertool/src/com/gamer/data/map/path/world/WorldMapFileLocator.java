package com.gamer.data.map.path.world;

import java.io.File;
import java.io.IOException;

/**
 * World 地图文件路径工具。
 */
public final class WorldMapFileLocator {

    private WorldMapFileLocator() {}

    /**
     * 规范化文件路径，当规范化被阻止时，不失败 UI。
     *
     * @param file
     *            文件路径
     * @return 规范化或绝对文件
     */
    public static File normalize(File file) {
        if (file == null) {
            return null;
        }
        try {
            return file.getCanonicalFile();
        } catch (IOException e) {
            return file.getAbsoluteFile();
        }
    }
}
