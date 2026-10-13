package utils.io;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * 项目与运行期文件路径解析工具类。
 * <p>
 * 提供跨平台标准路径拼接、配置与持久化存储路径定位、以及目标目录自动创建能力。
 * </p>
 *
 * @author cloud
 */
public class ConfigPathUtils {

    /** 统一日志记录器 */
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigPathUtils.class);

    private static final String RESOURCE_PATH = getSystemSeparator() + "resources" + getSystemSeparator();
    private static final String LOGCONFIG_PATH = "log" + getSystemSeparator();
    private static final String SERVERCONFIG_PATH = "server" + getSystemSeparator();
    private static final String XML_PATH = "xml" + getSystemSeparator();
    private static final String CONFIG_PATH = "config" + getSystemSeparator();
    private static final String PERSIST_PATH = "persist" + getSystemSeparator();
    private static final String REDIS_PATH = "redis" + getSystemSeparator();
    private static final String MYBATIS_PATH = "mybatis" + getSystemSeparator();
    private static final String JAVASCRIPT_PATH = "javascript" + getSystemSeparator();
    private static final String PYTHON_PATH = "python" + getSystemSeparator();
    private static final String JYTHON_PATH = "jython" + getSystemSeparator();
    private static final String LOG_PATH = getSystemSeparator() + "logs" + getSystemSeparator();

    public ConfigPathUtils() {
    }

    public static String getProjectPath() {
        String dir = System.getProperty("user.dir");
        LOGGER.trace("[getProjectPath] 获取当前工程工作目录: {}", dir);
        return dir;
    }

    public static String getSystemSeparator() {
        return System.getProperty("file.separator");
    }

    public static String getResourceFilePath() {
        return getProjectPath() + RESOURCE_PATH;
    }

    public static String getLogFilePath() {
        return getResourceFilePath() + LOGCONFIG_PATH;
    }

    public static String getResourceXMLConfigFilePath() {
        return getResourceFilePath() + XML_PATH;
    }

    public static String getConfigFilePath() {
        return getProjectPath() + CONFIG_PATH;
    }

    public static String getNetworkFilePath() {
        return getResourceFilePath() + SERVERCONFIG_PATH;
    }

    public static String getServerConfigFilePath() {
        return getResourceFilePath() + SERVERCONFIG_PATH;
    }

    public static String getPersistFilePath() {
        return getResourceFilePath() + PERSIST_PATH;
    }

    public static String getRedisFilePath() {
        return getPersistFilePath() + REDIS_PATH;
    }

    public static String getMyBatisFilePath() {
        return getPersistFilePath() + MYBATIS_PATH;
    }

    public static String getJavaScriptPath() {
        return getResourceFilePath() + JAVASCRIPT_PATH;
    }

    public static String getPythonPath() {
        return getResourceFilePath() + PYTHON_PATH;
    }

    public static String getJythonPath() {
        return getResourceFilePath() + JYTHON_PATH;
    }

    public static String getLogPath() {
        return LOG_PATH;
    }

    public static String getSubPath(String pathName) {
        return getSystemSeparator() + pathName + getSystemSeparator();
    }

    /**
     * 创建目标目录（若不存在则递归创建多级目录）。
     *
     * @param destDirName 目录绝对路径
     * @return true 创建成功，false 已存在或创建失败
     */
    public static boolean createDir(String destDirName) {
        if (destDirName == null || destDirName.trim().isEmpty()) {
            LOGGER.warn("[createDir] 目标目录路径为空，无法创建");
            return false;
        }
        File dir = new File(destDirName);
        if (dir.exists()) {
            LOGGER.trace("[createDir] 目录已存在，无需重复创建: dir='{}'", destDirName);
            return false;
        }
        if (!destDirName.endsWith(getSystemSeparator())) {
            destDirName = destDirName + getSystemSeparator();
        }
        boolean created = dir.mkdirs();
        LOGGER.info("[createDir] 创建目标文件夹: path='{}', result={}", destDirName, created);
        return created;
    }
}
