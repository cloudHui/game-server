package utils.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 轻量本地高性能带 TTL 过期淘汰的内存缓存容器。
 * <p>
 * 封装 {@link ConcurrentHashMap} 实现读写无锁化，结合惰性淘汰与双重检查防击穿机制，
 * 用于高频只读配置、玩家临时属性与对局短效状态的缓存加速。
 * </p>
 *
 * @param <K> 键类型
 * @param <V> 值类型
 * @author cloud
 */
public class LocalExpiringCache<K, V> {

    /** 统一日志记录器 */
    private static final Logger LOGGER = LoggerFactory.getLogger(LocalExpiringCache.class);

    /** 内部存储节点 <键, 缓存实体> */
    private final Map<K, CacheEntry<V>> cacheMap = new ConcurrentHashMap<>();

    /** 默认缓存存活时间（毫秒） */
    private final long defaultTtlMs;

    /**
     * 构建本地缓存实例。
     *
     * @param defaultTtlMs 默认有效存活时间（毫秒）
     */
    public LocalExpiringCache(long defaultTtlMs) {
        this.defaultTtlMs = defaultTtlMs;
        LOGGER.debug("[init] LocalExpiringCache 初始化完成, 默认 TTL={}ms", defaultTtlMs);
    }

    /**
     * 写入缓存项（采用默认 TTL）。
     *
     * @param key   缓存键
     * @param value 缓存值
     */
    public void put(K key, V value) {
        put(key, value, defaultTtlMs);
    }

    /**
     * 写入具备指定存活周期的缓存项。
     *
     * @param key   缓存键
     * @param value 缓存值
     * @param ttlMs 存活毫秒数
     */
    public void put(K key, V value, long ttlMs) {
        if (key == null || value == null) {
            LOGGER.trace("[put] 忽略空键或空值写入: key={}, value={}", key, value);
            return;
        }
        long expireTime = System.currentTimeMillis() + ttlMs;
        cacheMap.put(key, new CacheEntry<>(value, expireTime));
        LOGGER.trace("[put] 写入缓存项: key={}, ttlMs={}, expireTime={}", key, ttlMs, expireTime);
    }

    /**
     * 读取缓存项（若已过期则自动惰性清除并返回 null）。
     *
     * @param key 缓存键
     * @return 缓存有效值，未命中或过期返回 null
     */
    public V get(K key) {
        if (key == null) {
            return null;
        }
        CacheEntry<V> entry = cacheMap.get(key);
        if (entry == null) {
            LOGGER.trace("[get] 缓存未命中: key={}", key);
            return null;
        }

        // 惰性过期检测：若已超期，主动从容器剔除，避免过期脏读
        long now = System.currentTimeMillis();
        if (entry.isExpired(now)) {
            LOGGER.trace("[get] 缓存项已过期惰性淘汰: key={}, now={}, expireTime={}", key, now, entry.expireTime);
            cacheMap.remove(key, entry);
            return null;
        }
        LOGGER.trace("[get] 缓存命中: key={}", key);
        return entry.getValue();
    }

    /**
     * 读取缓存项；若未命中或已过期，则通过 loader 计算新值并安全回填（带防穿透保护）。
     *
     * @param key    缓存键
     * @param loader 数据加载回调
     * @return 缓存或新加载的有效值
     * @throws Exception 加载回调抛出的异常
     */
    public V get(K key, Callable<V> loader) throws Exception {
        return get(key, defaultTtlMs, loader);
    }

    /**
     * 读取缓存项（自定义 TTL 加载）。
     *
     * @param key    缓存键
     * @param ttlMs  新加载项的存活毫秒数
     * @param loader 数据加载回调
     * @return 缓存或新加载的有效值
     * @throws Exception 加载回调抛出的异常
     */
    public V get(K key, long ttlMs, Callable<V> loader) throws Exception {
        V existing = get(key);
        if (existing != null) {
            return existing;
        }

        LOGGER.debug("[get+loader] 缓存未命中，触发原子加载器: key={}, ttlMs={}", key, ttlMs);
        // 使用 compute 保证单键加载的互斥性，杜绝高并发穿透打崩下游
        final long[] expireHolder = new long[]{System.currentTimeMillis() + ttlMs};
        final Exception[] exceptionHolder = new Exception[1];

        CacheEntry<V> updated = cacheMap.compute(key, (k, oldEntry) -> {
            if (oldEntry != null && !oldEntry.isExpired(System.currentTimeMillis())) {
                return oldEntry;
            }
            try {
                V loaded = loader.call();
                if (loaded == null) {
                    LOGGER.debug("[get+loader] 加载回调返回 null: key={}", key);
                    return null;
                }
                return new CacheEntry<>(loaded, expireHolder[0]);
            } catch (Exception e) {
                exceptionHolder[0] = e;
                return null;
            }
        });

        if (exceptionHolder[0] != null) {
            LOGGER.error("[get+loader] 加载数据发生异常: key={}", key, exceptionHolder[0]);
            throw exceptionHolder[0];
        }

        return updated != null ? updated.getValue() : null;
    }

    /**
     * 主动驱逐指定键。
     *
     * @param key 缓存键
     */
    public void invalidate(K key) {
        if (key != null) {
            CacheEntry<V> removed = cacheMap.remove(key);
            LOGGER.trace("[invalidate] 主动驱逐缓存项: key={}, removed={}", key, removed != null);
        }
    }

    /**
     * 清理所有已过期的缓存项。
     */
    public void cleanupExpired() {
        long now = System.currentTimeMillis();
        int beforeSize = cacheMap.size();
        cacheMap.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
        int removedCount = beforeSize - cacheMap.size();
        if (removedCount > 0) {
            LOGGER.debug("[cleanupExpired] 批量清理已过期缓存: removed={}, remaining={}", removedCount, cacheMap.size());
        }
    }

    /**
     * 当前缓存条目总数（包含未触发惰性清理的可能过期项）。
     *
     * @return 条目数
     */
    public int size() {
        return cacheMap.size();
    }

    /**
     * 清空缓存。
     */
    public void clear() {
        int count = cacheMap.size();
        cacheMap.clear();
        LOGGER.debug("[clear] 缓存已全部清空, 清除条目数={}", count);
    }

    /**
     * 内部缓存条目承载体。
     */
    private static final class CacheEntry<V> {
        private final V value;
        private final long expireTime;

        private CacheEntry(V value, long expireTime) {
            this.value = value;
            this.expireTime = expireTime;
        }

        private V getValue() {
            return value;
        }

        private boolean isExpired(long now) {
            return now > expireTime;
        }
    }
}
