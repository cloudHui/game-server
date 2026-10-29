package tool.config;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import model.tablemodel.RobotRoomTemplates;
import model.tablemodel.TableModel;

/**
 * 房间模板配置管理器。
 * <p>
 * 负责加载、缓存和热更新房间模板配置，并在配置热重载时保留运行时自定义房与内置官方模板。
 */
public class TableConfigManager {

    /** 日志记录器 */
    private static final Logger logger = LoggerFactory.getLogger(TableConfigManager.class);

    /** 本地配表文件加载器 */
    private final ConfigLoader<TableModel> configLoader;
    /** 房间模板内存映射表 <房间模板ID, 房间模板模型> */
    private final Map<Integer, TableModel> tableModelMap = new ConcurrentHashMap<>();
    /** 配置热重载完成后的外部通知回调 */
    private Consumer<Map<Integer, TableModel>> onChangeCallback;

    /**
     * 构造房间模板配置管理器并绑定配置文件变更回调。
     */
    public TableConfigManager() {
        this.configLoader = new ConfigLoader<>(resolveConfigDir(), TableModel.class);
        this.configLoader.onChange(this::onConfigChange);
    }

    private static String resolveConfigDir() {
        String prop = System.getProperty("tableConfigDir", System.getProperty("table.config.dir"));
        if (prop != null && !prop.isEmpty()) {
            return prop;
        }
        return Files.isDirectory(Paths.get("../config")) ? "../config" : "config";
    }

    /**
     * 设置配置变更回调。
     *
     * @param callback 配置热更新完成后的消费函数
     */
    public void onChange(Consumer<Map<Integer, TableModel>> callback) {
        this.onChangeCallback = callback;
    }

    /**
     * 加载初始配表数据。
     *
     * @return true 表示加载失败，false 表示加载成功
     */
    public boolean loadFail() {
        List<TableModel> configs = configLoader.load();
        if (configs == null) {
            return true;
        }

        tableModelMap.clear();
        for (TableModel model : configs) {
            tableModelMap.put(model.getId(), model);
        }

        logger.info("房间模板加载完成, 数量: {}", tableModelMap.size());
        return false;
    }

    /**
     * 启动配置文件热更新监控。
     */
    public void startWatch() {
        configLoader.startWatch();
    }

    /**
     * 停止配置文件热更新监控。
     */
    public void stopWatch() {
        configLoader.stopWatch();
    }

    /**
     * 按模板 ID 获取房间模板。
     *
     * @param modelId 房间模板 ID
     * @return 对应房间模板，不存在时返回 null
     */
    public TableModel getTableModel(int modelId) {
        return tableModelMap.get(modelId);
    }

    /**
     * 运行时注册/覆盖模板（内置房与自定义创房），不写入 Excel，热更文件时会与文件合并保留。
     *
     * @param model 运行时房间模板实例
     */
    public void putRuntimeModel(TableModel model) {
        if (model == null) {
            return;
        }
        tableModelMap.put(model.getId(), model);
        logger.info("注册运行时房间模板, id: {}, type: {}", model.getId(), model.getType());
    }

    /**
     * 获取所有已注册的房间模板映射表。
     *
     * @return 房间模板映射表
     */
    public Map<Integer, TableModel> getAllTableModels() {
        return tableModelMap;
    }

    /**
     * 配置变更回调：Excel 热更会清空内存表，需保留自定义房间（>=10000）和内置快速机器人房，
     * 防止大厅房间页在热更新后丢失快速房卡片。
     */
    private void onConfigChange(List<TableModel> newConfigs) {
        logger.info("检测到配置变更，重新加载房间模板...");
        Map<Integer, TableModel> runtimeKeep = new ConcurrentHashMap<>();
        for (Map.Entry<Integer, TableModel> e : tableModelMap.entrySet()) {
            int id = e.getKey();
            if (id >= 10000 || RobotRoomTemplates.isRobotRoom(id)) {
                runtimeKeep.put(id, e.getValue());
            }
        }
        tableModelMap.clear();
        for (TableModel model : newConfigs) {
            tableModelMap.put(model.getId(), model);
        }
        tableModelMap.putAll(runtimeKeep);
        // 再次强制注册内置快速房模板，避免热更瞬间 map 中尚无内置模板时被漏掉
        RobotRoomTemplates.register(this::putRuntimeModel);
        logger.info("房间模板更新完成, 数量: {}", tableModelMap.size());

        if (onChangeCallback != null) {
            onChangeCallback.accept(tableModelMap);
        }
    }
}
