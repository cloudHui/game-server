package manager.util;

import java.util.Locale;

public final class Platform {
    private Platform() {}

    public static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
    }

    public static String mavenExecutable() { return isWindows() ? "mvn.cmd" : "mvn"; }
    public static String executable(String name) { return name + (isWindows() ? ".exe" : ""); }
}
