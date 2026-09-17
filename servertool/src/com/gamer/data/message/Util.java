package com.gamer.data.message;

import java.io.File;

public class Util {
    public Util() {
    }

    public static boolean isNotBlank(CharSequence cs) {
        return !isBlank(cs);
    }

    public static boolean isBlank(CharSequence cs) {
        if (cs != null) {
            int strLen = length(cs);
            if (strLen != 0) {
                for (int i = 0; i < strLen; ++i) {
                    if (!Character.isWhitespace(cs.charAt(i))) {
                        return false;
                    }
                }

            }
        }
        return true;
    }

    public static int length(CharSequence cs) {
        return cs == null ? 0 : cs.length();
    }

    
    /**
     * 应用程序是否为虚拟机启动
     * 
     * @return 是否为虚拟机启动
     */
    public static boolean inVM() {
        File fromFile = new File(Util.class.getProtectionDomain().getCodeSource().getLocation().getPath());
        return fromFile.isFile() && fromFile.getName().endsWith(".jar");
    }
}
