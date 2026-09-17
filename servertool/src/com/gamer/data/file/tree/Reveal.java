package com.gamer.data.file.tree;

import java.io.File;

/**
 * 在「根目录」树中展开目录。单击打开文件、双击打开目录仍走树监听。
 */
public interface Reveal {

    /**
     * 切到根目录页并展开目标。传入文件时展开其父目录。
     *
     * @param dir
     *            目录或文件
     */
    void open(File dir);
}
