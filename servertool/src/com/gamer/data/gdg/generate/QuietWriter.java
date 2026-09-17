package com.gamer.data.gdg.generate;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;

public class QuietWriter {
    static final String LINE_SEP = System.lineSeparator();
    String fileName;
    String filePath;
    boolean isAppend;
    boolean isBackup;
    File outFile;
    Writer out;

    public QuietWriter(String filePath, String fileName, boolean isBackup, boolean isAppend) throws IOException {
        this.fileName = null;
        this.filePath = null;
        this.isAppend = true;
        this.isBackup = false;
        this.outFile = null;
        this.out = null;
        this.setOptions(filePath, fileName, isBackup, isAppend);
    }

    public synchronized void setOptions(String filePath, String fileName, boolean isBackup, boolean isAppend)
        throws IOException {
        this.resetOptions();
        this.filePath = filePath;
        this.fileName = fileName;
        this.isBackup = isBackup;
        this.isAppend = isAppend;
        if (!filePath.endsWith(File.separator)) {
            filePath = filePath + File.separator;
        }

        String file = filePath + fileName;
        File target = new File(file);
        if (target.exists()) {
            if (isBackup) {
                SimpleDateFormat dateFormat = new SimpleDateFormat("yyMMddHHmmss");
                String date = dateFormat.format(new Date(System.currentTimeMillis()));
                String bcFileName = filePath + fileName + ".bak" + date;
                File bcFile = new File(bcFileName);
                if (bcFile.exists()) {
                    bcFile.delete();
                }

                target.renameTo(bcFile);
            } else {
                target.delete();
            }
        }

        this.outFile = new File(file);
        if (!this.outFile.exists()) {
            String parentName = (new File(file)).getParent();
            if (parentName != null) {
                File parentDir = new File(parentName);
                if (!parentDir.exists()) {
                    parentDir.mkdirs();
                }
            }

            this.outFile.createNewFile();
        }

        this.out = new BufferedWriter(
            new OutputStreamWriter(new FileOutputStream(this.outFile, isAppend), StandardCharsets.UTF_8));
    }

    private void resetOptions() {
        this.out = null;
        this.outFile = null;
        this.fileName = null;
        this.filePath = null;
        this.isAppend = true;
        this.isBackup = false;
    }

    public void write(String str) {
        if (str != null && this.out != null) {
            try {
                this.out.write(str);
            } catch (Exception ignored) {
            }
        }

    }

    public void writeLineSep() {
        if (this.out != null) {
            try {
                this.out.write(LINE_SEP);
            } catch (Exception ignored) {
            }
        }

    }

    public void close() {
        try {
            if (this.out != null) {
                this.out.flush();
                this.out.close();
            }
        } catch (Exception ignored) {
        } finally {
            this.resetOptions();
        }

    }
}
