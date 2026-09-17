package com.gamer.data.file.client.workday;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import com.gamer.data.file.client.holiday.ConfigStore;

/**
 * 东八区每日 10:00 工作日调度器：多任务共用一个定时线程，触发时逐任务写日志并执行。
 */
public final class Scheduler {

    /** 东八区 */
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    /** 调度日志时间格式 */
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final Object LOCK = new Object();
    private static final List<Job> JOBS = new ArrayList<>();
    private static boolean started;

    private Scheduler() {}

    /**
     * 注册工作日 10:00 任务。已存在同名则忽略。{@link #start()} 前后都可调用。
     *
     * @param label
     *            显示名（同时是开关键）
     * @param log
     *            调度日志
     * @param action
     *            触发动作
     */
    private static void register(String label, Consumer<String> log, Runnable action) {
        synchronized (LOCK) {
            if (indexOf(label) >= 0) {
                return;
            }
            JOBS.add(new Job(label, log, action));
        }
    }

    /**
     * 取消工作日 10:00 任务。
     *
     * @param label
     *            显示名
     */
    private static void unregister(String label) {
        synchronized (LOCK) {
            int i = indexOf(label);
            if (i >= 0) {
                JOBS.remove(i);
            }
        }
    }

    /**
     * 打开或关闭同名任务。打开时已存在则忽略。
     *
     * @param label
     *            显示名
     * @param on
     *            启用
     * @param log
     *            调度日志
     * @param action
     *            触发动作
     */
    public static void setEnabled(String label, boolean on, Consumer<String> log, Runnable action) {
        if (on) {
            register(label, log, action);
        } else {
            unregister(label);
        }
    }

    /**
     * @param label
     *            显示名
     * @return 下标，没有为 -1
     */
    private static int indexOf(String label) {
        for (int i = 0; i < JOBS.size(); i++) {
            if (JOBS.get(i).label.equals(label)) {
                return i;
            }
        }
        return -1;
    }

    /** 启动调度器（重复调用安全）。 */
    public static void start() {
        synchronized (LOCK) {
            if (started) {
                return;
            }
            started = true;
            boot();
        }
    }

    /**
     * ISO 星期几转中文（1=周一 … 7=周日）。
     */
    public static String formatWeekday(int dayOfWeek) {
        switch (dayOfWeek) {
            case 1:
                return "周一";
            case 2:
                return "周二";
            case 3:
                return "周三";
            case 4:
                return "周四";
            case 5:
                return "周五";
            case 6:
                return "周六";
            case 7:
                return "周日";
            default:
                return "未知(" + dayOfWeek + ")";
        }
    }

    private static void boot() {
        ZonedDateTime now = ZonedDateTime.now(ZONE);
        ZonedDateTime next10AM = resolveNext10AM(now);
        long initialDelay = Duration.between(now, next10AM).toMillis();
        long period = TimeUnit.DAYS.toMillis(1);

        List<Job> snapshot;
        synchronized (LOCK) {
            snapshot = new ArrayList<>(JOBS);
        }
        for (Job job : snapshot) {
            logStartup(job, now, next10AM, initialDelay);
        }

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "workday-10am-scheduler");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(Scheduler::fire, initialDelay, period, TimeUnit.MILLISECONDS);
    }

    private static void fire() {
        ZonedDateTime triggerTime = ZonedDateTime.now(ZONE);
        LocalDate today = triggerTime.toLocalDate();
        Decision decision = ConfigStore.resolveWorkday(today);

        List<Job> snapshot;
        synchronized (LOCK) {
            snapshot = new ArrayList<>(JOBS);
        }
        for (Job job : snapshot) {
            job.log.accept("定时触发 东八区: " + formatTime(triggerTime) + "，"
                + formatWeekday(today.getDayOfWeek().getValue()) + "，"
                + decision.source.label + "：" + decision.detail + "，"
                + (decision.workday ? "工作日，开始" + job.label : "非工作日，跳过"));
            if (decision.workday) {
                job.action.run();
            }
        }
    }

    private static void logStartup(Job job, ZonedDateTime now, ZonedDateTime next10AM, long initialDelay) {
        job.log.accept("调度器已启动");
        job.log.accept("东八区当前: " + formatTime(now));
        job.log.accept("下次触发: " + formatTime(next10AM) + "（东八区 10:00）");
        if (initialDelay > 0L) {
            job.log.accept("首次延迟: " + formatDelay(initialDelay) + "，周期: 24h，条件: 工作日");
        }
    }

    private static ZonedDateTime resolveNext10AM(ZonedDateTime now) {
        ZonedDateTime next10AM = now.toLocalDate().atTime(10, 0).atZone(ZONE);
        if (now.isAfter(next10AM)) {
            next10AM = next10AM.plusDays(1);
        }
        return next10AM;
    }

    private static String formatTime(ZonedDateTime dateTime) {
        return dateTime.format(TIME_FMT) + " (Asia/Shanghai)";
    }

    private static String formatDelay(long millis) {
        long hours = millis / TimeUnit.HOURS.toMillis(1);
        long minutes = (millis % TimeUnit.HOURS.toMillis(1)) / TimeUnit.MINUTES.toMillis(1);
        long seconds = (millis % TimeUnit.MINUTES.toMillis(1)) / 1000L;
        return hours + "h" + minutes + "m" + seconds + "s (" + millis + "ms)";
    }

    /** 已注册的调度任务。 */
    private static final class Job {
        private final String label;
        private final Consumer<String> log;
        private final Runnable action;

        private Job(String label, Consumer<String> log, Runnable action) {
            this.label = label;
            this.log = log;
            this.action = action;
        }
    }
}
