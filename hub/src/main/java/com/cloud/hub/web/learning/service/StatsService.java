package com.cloud.hub.web.learning.service;

import com.cloud.hub.web.learning.model.DailyUsage;
import com.cloud.hub.web.learning.model.LearningRecord;
import com.cloud.hub.web.learning.model.Mistake;
import com.cloud.hub.web.learning.model.Student;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 学习数据统计、多维分析及仪表盘报告服务。
 * <p>
 * 提供学生个人每日学情分析、语文识字掌握度、数学口算用时统计、连续打卡天数及管理员全站活跃分析。
 *
 * @author cloud
 */
@Service
public class StatsService {

    private static final Logger log = LoggerFactory.getLogger(StatsService.class);
    private final RecordService records;
    private final MistakeService mistakes;
    private final StudentService students;
    private final UsageService usage;
    private final WordService words;

    public StatsService(RecordService records,
                        MistakeService mistakes,
                        StudentService students,
                        UsageService usage,
                        WordService words) {
        this.records = records;
        this.mistakes = mistakes;
        this.students = students;
        this.usage = usage;
        this.words = words;
    }

    /**
     * 汇聚计算个人学情全景看板。
     *
     * @param userId 学生用户 ID
     * @return 个人学情指标集
     * @throws Exception 聚合异常
     */
    public Map<String, Object> personal(String userId) throws Exception {
        List<LearningRecord> allRecords = records.list(userId);
        List<Mistake> allMistakes = mistakes.list(userId, null, null);
        Student student = students.get(userId);
        String currentStage = student == null ? "幼小衔接" : safe(student.stage, "幼小衔接");

        PersonalContext ctx = new PersonalContext(userId, currentStage);
        processRecords(allRecords, ctx);
        countNewMistakes(allMistakes, ctx);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("today", buildTodayOverview(ctx, allMistakes));

        List<Map<String, Object>> trends30 = trends(userId, ctx.dailyRecords, 30);
        result.put("trends7", new ArrayList<>(trends30.subList(Math.max(0, trends30.size() - 7), trends30.size())));
        result.put("trends30", trends30);

        result.put("chinese", buildChineseStats(ctx, allRecords, allMistakes));
        result.put("math", buildMathStats(ctx, allMistakes));
        result.put("mistakes", buildMistakeView(allMistakes));
        result.put("subjects", subjectViews(ctx.subjects));
        result.put("stageProgress", buildStageProgress(ctx));
        result.put("habits", buildHabits(ctx));
        return result;
    }

    private void processRecords(List<LearningRecord> allRecords, PersonalContext ctx) {
        LocalDate today = LocalDate.now();
        for (LearningRecord record : allRecords) {
            if (record.createdAt != null) {
                LocalDate recordDate = record.createdAt.toLocalDate();
                ctx.studyDays.add(recordDate);
                long[] day = ctx.dailyRecords.computeIfAbsent(recordDate, key -> new long[3]);
                day[0] += record.total;
                day[1] += record.correct;
                day[2] += record.durationSeconds;
            }
            if (record.createdAt != null && today.equals(record.createdAt.toLocalDate())) {
                ctx.todaySessions++;
                ctx.todayTotal += record.total;
                ctx.todayCorrect += record.correct;
                ctx.recordedTodaySeconds += record.durationSeconds;
            }
            accumulateSubject(record, ctx);
        }
    }

    private void accumulateSubject(LearningRecord record, PersonalContext ctx) {
        String subject = safe(record.subject, "其他");
        long[] subjectData = ctx.subjects.computeIfAbsent(subject, key -> new long[4]);
        subjectData[0]++;
        subjectData[1] += record.durationSeconds;
        subjectData[2] += record.total;
        subjectData[3] += record.correct;

        if ("数学".equals(record.subject)) {
            ctx.mathTotal += record.total;
            ctx.mathCorrect += record.correct;
            ctx.mathSeconds += record.durationSeconds;
            int[] range = ctx.mathRanges.computeIfAbsent(safe(record.module, "其他"), key -> new int[2]);
            range[0] += record.total;
            range[1] += record.correct;
        }
        if ("语文".equals(record.subject) && record.details != null) {
            Object character = record.details.get("character");
            if (character != null) {
                String text = String.valueOf(character);
                ctx.practicedWords.add(text);
                if (ctx.currentStage.equals(safe(record.stage, "幼小衔接"))) {
                    ctx.stagePracticedWords.add(text);
                }
                if (!ctx.latestWords.containsKey(text)) {
                    ctx.latestWords.put(text, record.correct >= record.total && record.total > 0);
                }
            }
        }
    }

    private void countNewMistakes(List<Mistake> allMistakes, PersonalContext ctx) {
        LocalDate today = LocalDate.now();
        for (Mistake item : allMistakes) {
            if (item.firstWrongAt != null && today.equals(item.firstWrongAt.toLocalDate())) {
                ctx.newMistakes++;
            }
        }
    }

    private Map<String, Object> buildTodayOverview(PersonalContext ctx, List<Mistake> allMistakes) throws Exception {
        long todaySeconds = usage.today().userSeconds.getOrDefault(ctx.userId, 0L);
        if (todaySeconds == 0) {
            todaySeconds = ctx.recordedTodaySeconds;
        }
        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("studySeconds", todaySeconds);
        overview.put("sessions", ctx.todaySessions);
        overview.put("completed", ctx.todayTotal);
        overview.put("accuracy", percent(ctx.todayCorrect, ctx.todayTotal));
        overview.put("newMistakes", ctx.newMistakes);
        overview.put("pendingMistakes", allMistakes.stream().filter(item -> !"已掌握".equals(item.status)).count());
        overview.put("streakDays", streak(ctx.studyDays));
        return overview;
    }

    private Map<String, Object> buildChineseStats(PersonalContext ctx, List<LearningRecord> allRecords, List<Mistake> allMistakes) throws Exception {
        Map<String, Object> chinese = new LinkedHashMap<>();
        chinese.put("learned", ctx.practicedWords.size());
        chinese.put("known", ctx.latestWords.values().stream().filter(Boolean::booleanValue).count());
        chinese.put("fuzzy", ctx.latestWords.values().stream().filter(value -> !value).count());
        chinese.put("unknown", Math.max(0, words.list(ctx.currentStage).size() - ctx.stagePracticedWords.size()));
        chinese.put("dictationAccuracy", moduleAccuracy(allRecords, "语文", "听写"));
        chinese.put("frequentWrong", frequent(allMistakes, "语文", 8));
        return chinese;
    }

    private Map<String, Object> buildMathStats(PersonalContext ctx, List<Mistake> allMistakes) {
        Map<String, Object> math = new LinkedHashMap<>();
        math.put("total", ctx.mathTotal);
        math.put("accuracy", percent(ctx.mathCorrect, ctx.mathTotal));
        math.put("averageSeconds", ctx.mathTotal == 0 ? 0 : Math.round(ctx.mathSeconds * 10.0 / ctx.mathTotal) / 10.0);
        math.put("ranges", rangeViews(ctx.mathRanges));
        math.put("frequentWrong", frequent(allMistakes, "数学", 8));
        return math;
    }

    private Map<String, Object> buildMistakeView(List<Mistake> allMistakes) {
        Map<String, Integer> mistakeStatus = new LinkedHashMap<>();
        for (String status : new String[]{"待复习", "复习中", "基本掌握", "已掌握"}) {
            mistakeStatus.put(status, 0);
        }
        Map<String, Integer> mistakeSubjects = new LinkedHashMap<>();
        for (Mistake item : allMistakes) {
            mistakeStatus.put(item.status, mistakeStatus.getOrDefault(item.status, 0) + 1);
            mistakeSubjects.put(item.subject, mistakeSubjects.getOrDefault(item.subject, 0) + 1);
        }
        Map<String, Object> mistakeView = new LinkedHashMap<>();
        mistakeView.put("status", mistakeStatus);
        mistakeView.put("subjects", mistakeSubjects);
        mistakeView.put("repeated", frequent(allMistakes, null, 10));
        return mistakeView;
    }

    private Map<String, Object> buildStageProgress(PersonalContext ctx) throws Exception {
        Map<String, Object> progress = new LinkedHashMap<>();
        int totalWords = words.list(ctx.currentStage).size();
        progress.put("stage", ctx.currentStage);
        progress.put("completed", ctx.stagePracticedWords.size());
        progress.put("total", totalWords);
        progress.put("percent", percent(ctx.stagePracticedWords.size(), totalWords));
        return progress;
    }

    private Map<String, Object> buildHabits(PersonalContext ctx) {
        LocalDate today = LocalDate.now();
        Map<String, Object> habits = new LinkedHashMap<>();
        habits.put("streakDays", streak(ctx.studyDays));
        habits.put("lastStudyDate", ctx.studyDays.stream().max(LocalDate::compareTo).orElse(null));
        habits.put("daysThisWeek", ctx.studyDays.stream().filter(day -> !day.isBefore(today.minusDays(6))).count());
        habits.put("goalDays", 5);
        return habits;
    }

    /**
     * 管理员全站系统运行指标汇总。
     */
    public Map<String, Object> admin() throws Exception {
        DailyUsage today = usage.today();
        List<Student> users = students.list();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalUsers", users.size());
        result.put("activeUsers", today.activeUserIds.size());
        result.put("newUsers", today.newUsers);
        result.put("loginCount", today.loginCount);
        result.put("online", usage.onlineUsers().size());
        result.put("peakOnline", today.peakOnline);
        result.put("totalSeconds", today.totalSeconds);
        result.put("pageViews", sortMap(today.pageViews));
        result.put("pageSeconds", sortMap(today.pageSeconds));
        result.put("featureStarts", sortMap(today.featureStarts));
        result.put("featureSeconds", sortMap(today.featureSeconds));
        result.put("devices", today.devices);
        result.put("frontendErrors", today.frontendErrors);
        result.put("backendErrors", today.backendErrors);

        accumulateAdminLearning(users, result);
        result.put("onlineUsers", usage.onlineUsers());
        return result;
    }

    private void accumulateAdminLearning(List<Student> users, Map<String, Object> result) {
        int completed = 0;
        int correct = 0;
        int newMistakes = 0;
        List<Mistake> combined = new ArrayList<>();
        LocalDate today = LocalDate.now();

        for (Student user : users) {
            try {
                for (LearningRecord record : records.list(user.id)) {
                    if (record.createdAt != null && today.equals(record.createdAt.toLocalDate())) {
                        completed += record.total;
                        correct += record.correct;
                    }
                }
                for (Mistake item : mistakes.list(user.id, null, null)) {
                    combined.add(item);
                    if (item.firstWrongAt != null && today.equals(item.firstWrongAt.toLocalDate())) {
                        newMistakes++;
                    }
                }
            } catch (Exception exception) {
                log.error("管理员统计跳过异常用户: {}", user.id, exception);
            }
        }
        result.put("completed", completed);
        result.put("accuracy", percent(correct, completed));
        result.put("newMistakes", newMistakes);
        result.put("frequentErrors", frequent(combined, null, 15));
    }

    private List<Map<String, Object>> trends(String userId, Map<LocalDate, long[]> dailyRecords, int days)
            throws Exception {
        List<Map<String, Object>> result = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (int offset = days - 1; offset >= 0; offset--) {
            LocalDate date = today.minusDays(offset);
            long[] recorded = dailyRecords.getOrDefault(date, new long[3]);
            int total = (int) recorded[0];
            int correct = (int) recorded[1];
            long seconds = usage.day(date).userSeconds.getOrDefault(userId, 0L);
            if (seconds == 0) {
                seconds = recorded[2];
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", date);
            item.put("seconds", seconds);
            item.put("completed", total);
            item.put("accuracy", percent(correct, total));
            result.add(item);
        }
        return result;
    }

    private double moduleAccuracy(List<LearningRecord> records, String subject, String module) {
        int total = 0;
        int correct = 0;
        for (LearningRecord item : records) {
            if (subject.equals(item.subject) && module.equals(item.module)) {
                total += item.total;
                correct += item.correct;
            }
        }
        return percent(correct, total);
    }

    private List<Map<String, Object>> frequent(List<Mistake> items, String subject, int limit) {
        Map<String, int[]> counts = new HashMap<>();
        for (Mistake item : items) {
            if (subject == null || subject.equals(item.subject)) {
                int[] value = counts.computeIfAbsent(safe(item.question, "未知问题"), key -> new int[2]);
                value[0] += Math.max(1, item.errorCount);
                value[1]++;
            }
        }
        List<Map<String, Object>> result = new ArrayList<>();
        counts.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue()[0], a.getValue()[0]))
                .limit(limit)
                .forEach(entry -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("question", entry.getKey());
                    row.put("errors", entry.getValue()[0]);
                    row.put("users", entry.getValue()[1]);
                    result.add(row);
                });
        return result;
    }

    private List<Map<String, Object>> rangeViews(Map<String, int[]> ranges) {
        List<Map<String, Object>> result = new ArrayList<>();
        ranges.forEach((name, data) -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", name);
            item.put("total", data[0]);
            item.put("accuracy", percent(data[1], data[0]));
            result.add(item);
        });
        return result;
    }

    private List<Map<String, Object>> subjectViews(Map<String, long[]> subjects) {
        List<Map<String, Object>> result = new ArrayList<>();
        subjects.forEach((name, data) -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("subject", name);
            item.put("sessions", data[0]);
            item.put("seconds", data[1]);
            item.put("completed", data[2]);
            item.put("accuracy", percent((int) data[3], (int) data[2]));
            result.add(item);
        });
        return result;
    }

    private Map<String, ? extends Number> sortMap(Map<String, ? extends Number> source) {
        Map<String, Number> result = new LinkedHashMap<>();
        source.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue().doubleValue(), a.getValue().doubleValue()))
                .forEach(entry -> result.put(entry.getKey(), entry.getValue()));
        return result;
    }

    private int streak(Set<LocalDate> days) {
        int count = 0;
        LocalDate day = LocalDate.now();
        if (!days.contains(day)) {
            day = day.minusDays(1);
        }
        while (days.contains(day)) {
            count++;
            day = day.minusDays(1);
        }
        return count;
    }

    private double percent(int correct, int total) {
        return total == 0 ? 0 : Math.round(correct * 1000.0 / total) / 10.0;
    }

    private String safe(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    private static class PersonalContext {
        final String userId;
        final String currentStage;
        int todaySessions = 0;
        int todayTotal = 0;
        int todayCorrect = 0;
        int newMistakes = 0;
        long recordedTodaySeconds = 0;
        final Set<LocalDate> studyDays = new HashSet<>();
        final Map<String, long[]> subjects = new LinkedHashMap<>();
        final Map<LocalDate, long[]> dailyRecords = new HashMap<>();
        int mathTotal = 0;
        int mathCorrect = 0;
        long mathSeconds = 0;
        final Map<String, int[]> mathRanges = new LinkedHashMap<>();
        final Map<String, Boolean> latestWords = new LinkedHashMap<>();
        final Set<String> practicedWords = new HashSet<>();
        final Set<String> stagePracticedWords = new HashSet<>();

        PersonalContext(String userId, String currentStage) {
            this.userId = userId;
            this.currentStage = currentStage;
        }
    }
}
