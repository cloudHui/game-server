package com.gamer.data.gdg.util;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.math.BigInteger;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

import com.gamer.data.gdg.generate.QuietWriter;
public class GdMD5Util {

    public static void buildGdMD5() {
        File md5File = new File("GD_MD5.txt");

        try {
            File dir = new File(".");
            if (!dir.exists() || !dir.isDirectory()) {
                return;
            }

            FileFilter gdFilter = file -> file.getAbsolutePath().endsWith(".gd");
            File[] gdFiles = dir.listFiles(gdFilter);
            if (gdFiles == null || gdFiles.length == 0) {
                return;
            }

            System.out.println("md5File delete:" + md5File.delete());
            StringBuilder gdSb = new StringBuilder();
            StringBuilder md5Sb = new StringBuilder();
            List<String> gdInfos = new ArrayList<>();
            int i = 0;

            for (int len = gdFiles.length; i < len; ++i) {
                File gdF = gdFiles[i];
                String gdName = gdF.getName();
                if (gdName.endsWith(".gd")) {
                    gdName = gdName.substring(0, gdName.lastIndexOf("."));
                }

                String md5 = getFileMd5(gdF);
                String info = gdName + "=" + md5;
                gdSb.append(gdName);
                if (i < len - 1) {
                    gdSb.append(",");
                }

                md5Sb.append(info).append(";");
                gdInfos.add(info);
            }

            QuietWriter writer = new QuietWriter(dir.getPath(), "GD_MD5.txt", false, false);
            writer.write("Gd:");
            writer.writeLineSep();
            writer.write(gdSb.toString());
            writer.writeLineSep();
            writer.writeLineSep();
            writer.write("Md5:");
            writer.writeLineSep();
            writer.write(md5Sb.toString());
            writer.writeLineSep();
            writer.writeLineSep();
            writer.write("Info:");
            writer.writeLineSep();

            for (String info : gdInfos) {
                writer.write(info);
                writer.writeLineSep();
            }

            writer.writeLineSep();
            writer.writeLineSep();
            writer.writeLineSep();
            writer.writeLineSep();
            writer.writeLineSep();
            writer.close();
        } catch (Exception e) {
            System.out.println("error md5File delete:" + md5File.delete());
            e.printStackTrace();
        }

    }

    public static String getFileMd5(File destFile) {
        if (destFile == null || !destFile.exists() || !destFile.isFile()) {
            return "";
        }

        String md5;
        try (FileInputStream fins = new FileInputStream(destFile)) {
            MappedByteBuffer byteBuffer =
                    fins.getChannel().map(FileChannel.MapMode.READ_ONLY, 0L, destFile.length());
            MessageDigest md5Digest = MessageDigest.getInstance("MD5");
            md5Digest.update(byteBuffer);
            BigInteger bigInt = new BigInteger(1, md5Digest.digest());
            md5 = bigInt.toString(16);
        } catch (Exception e) {
            md5 = "";
            e.printStackTrace();
        }
        return md5;
    }
}
