package utils.concurrent.pool;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 高性能零 GC 并发对象池（面向 30/60 FPS 实时竞技与高频并发吞吐优化）。
 * <p>
 * <b>核心设计：</b>
 * <ul>
 * <li><b>无锁高并发：</b> 内部基于 {@link ConcurrentLinkedQueue} 存放空闲对象，出池与入池零锁竞争；</li>
 * <li><b>自愈与重置：</b> 若对象实现 {@link Recyclable} 接口，在借出与归还时自动调用 {@link Recyclable#reset()}，杜绝数据残留；</li>
 * <li><b>容量上限保护：</b> 超过设定的最大容量时自动丢弃超额对象并交给 GC 回收，杜绝瞬时洪峰导致内存无限滞留；</li>
 * <li><b>AutoCloseable 安全句柄：</b> 提供 {@link #acquire()} 方法，支持 {@code try-with-resources} 语法糖，用完自动归还入池；</li>
 * <li><b>运行指标统计：</b> 内置借出、归还、创建计数与命中率监控，方便运行时巡检与排查。</li>
 * </ul>
 *
 * @param <T> 池化管理的目标对象类型
 * @author cloud
 */
public class ObjectPool<T> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ObjectPool.class);

    /** 默认最大空闲对象容量 */
    public static final int DEFAULT_MAX_CAPACITY = 2048;

    /** 对象创建工厂 */
    private final Supplier<T> creator;

    /** 池允许保留的最大空闲对象数量 */
    private final int maxCapacity;

    /** 空闲对象无锁队列 */
    private final Queue<T> idleQueue;

    /** 当前空闲对象计数（原子计数器，避免 queue.size() 的 O(N) 遍历开销） */
    private final AtomicInteger currentIdleCount;

    /** 历史总借出次数 */
    private final AtomicLong totalBorrows = new AtomicLong(0);

    /** 历史总归还入池次数 */
    private final AtomicLong totalRecycles = new AtomicLong(0);

    /** 历史总创建新对象次数（未命中池时触发） */
    private final AtomicLong totalCreations = new AtomicLong(0);

    /**
     * 构造函数：采用默认容量 2048。
     *
     * @param creator 对象创建工厂
     */
    public ObjectPool(Supplier<T> creator) {
        this(creator, DEFAULT_MAX_CAPACITY);
    }

    /**
     * 构造函数：指定对象工厂与最大容量。
     *
     * @param creator     对象创建工厂
     * @param maxCapacity 最大空闲容量
     */
    public ObjectPool(Supplier<T> creator, int maxCapacity) {
        if (creator == null) {
            throw new IllegalArgumentException("对象创建工厂 creator 不能为空");
        }
        this.creator = creator;
        this.maxCapacity = Math.max(16, maxCapacity);
        this.idleQueue = new ConcurrentLinkedQueue<>();
        this.currentIdleCount = new AtomicInteger(0);
    }

    /**
     * 从池中借出一个可用对象。若池为空则自动触发创建工厂实例化。
     *
     * @return 可用对象实例
     */
    public T borrow() {
        totalBorrows.incrementAndGet();
        T obj = idleQueue.poll();
        if (obj != null) {
            currentIdleCount.decrementAndGet();
            if (obj instanceof Recyclable) {
                ((Recyclable) obj).reset();
            }
            return obj;
        }

        // 池为空，触发工厂创建
        totalCreations.incrementAndGet();
        T created = creator.get();
        if (created instanceof Recyclable) {
            ((Recyclable) created).reset();
        }
        return created;
    }

    /**
     * 将使用完毕的对象安全归还至池中。
     *
     * @param obj 待归还对象（若为 null 则静默忽略）
     * @return true 表示成功入池；false 表示超出最大容量已丢弃交给 GC
     */
    public boolean recycle(T obj) {
        if (obj == null) {
            return false;
        }

        if (obj instanceof Recyclable) {
            try {
                ((Recyclable) obj).reset();
            } catch (Throwable t) {
                LOGGER.error("[ObjectPool] 对象 reset() 异常, 放弃入池", t);
                return false;
            }
        }

        // 容量门禁检查：防止池无限膨胀
        if (currentIdleCount.get() >= maxCapacity) {
            return false;
        }

        idleQueue.offer(obj);
        currentIdleCount.incrementAndGet();
        totalRecycles.incrementAndGet();
        return true;
    }

    /**
     * 借出对象并包装为 {@link PooledHandle}，支持 try-with-resources 自动归还。
     *
     * @return 支持 AutoCloseable 的池化对象句柄
     */
    public PooledHandle<T> acquire() {
        return new PooledHandle<>(borrow(), this);
    }

    /**
     * 获取当前池中空闲对象数量。
     */
    public int getIdleCount() {
        return currentIdleCount.get();
    }

    /**
     * 获取对象池最大容量。
     */
    public int getMaxCapacity() {
        return maxCapacity;
    }

    /**
     * 获取累计创建对象次数。
     */
    public long getTotalCreations() {
        return totalCreations.get();
    }

    /**
     * 获取累计借出次数。
     */
    public long getTotalBorrows() {
        return totalBorrows.get();
    }

    /**
     * 获取累计归还次数。
     */
    public long getTotalRecycles() {
        return totalRecycles.get();
    }

    /**
     * 获取当前池命中率（0.0 ~ 1.0）。
     */
    public double getHitRate() {
        long borrows = totalBorrows.get();
        if (borrows == 0) {
            return 1.0;
        }
        long creates = totalCreations.get();
        return Math.max(0.0, (double) (borrows - creates) / borrows);
    }

    /**
     * 清空池中所有缓存对象。
     */
    public void clear() {
        idleQueue.clear();
        currentIdleCount.set(0);
    }
}
