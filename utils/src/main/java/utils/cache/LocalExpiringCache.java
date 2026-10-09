package utils.cache;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 轻量本地高性能带 TTL 过期淘汰的内存缓存容器。
 * <p>
 * 封装 {@link ConcurrentHashMap} 实现读写无锁化，结合惰性淘汰与双重检查防击穿机制，
 * 用于高频只读配置、玩家临时属性与对局短效状态的缓存加速。
 *
 * @param <K> 键类型
 * @param <V> 值类型
 * @author cloud
 */
public class LocalExpiringCache<K, V> {

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
            return;
        }
        long expireTime = System.currentTimeMillis() + ttlMs;
        cacheMap.put(key, new CacheEntry<>(value, expireTime));
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
            return null;
        }

        // 惰性过期检测：若已超期，主动从容器剔除，避免过期脏读
        if (entry.isExpired(System.currentTimeMillis())) {
            cacheMap.remove(key, entry);
            return null;
        }
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
                    return null;
                }
                return new CacheEntry<>(loaded, expireHolder[0]);
            } catch (Exception e) {
                exceptionHolder[0] = e;
                return null;
            }
        });

        if (exceptionHolder[0] != null) {
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
            cacheMap.remove(key);
        }
    }

    /**
     * 清理所有已过期的缓存项。
     */
    public void cleanupExpired() {
        long now = System.currentTimeMillis();
        cacheMap.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
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
        cacheMap.clear();
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
