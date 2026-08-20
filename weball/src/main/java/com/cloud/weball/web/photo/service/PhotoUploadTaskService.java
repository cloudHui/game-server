package com.cloud.weball.web.photo.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import web.photo.model.PhotoException;
import web.service.UserService;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

@Service
public class PhotoUploadTaskService {
    private static final int MAX_TASKS = 20;
    private static final long RETAIN_MILLIS = 60 * 60 * 1000L;
    private final Semaphore workers = new Semaphore(3, true);
    private final ConcurrentHashMap<String, Task> tasks = new ConcurrentHashMap<>();

    public Map<String, Object> execute(String requestedId, String filename, UserService.UserInfo user,
                                       Supplier<Map<String, Object>> work) {
        cleanupExpired();
        String id = validId(requestedId) ? requestedId : UUID.randomUUID().toString();
        Task task = new Task(id, filename, user.getUserId());
        synchronized (tasks) {
            if (activeCount() >= MAX_TASKS) throw new PhotoException(429, "上传任务已满，请稍后重试");
            if (tasks.putIfAbsent(id, task) != null) throw new PhotoException(409, "上传任务编号重复");
        }
        boolean acquired = false;
        try {
            workers.acquire();
            acquired = true;
            task.status = "PROCESSING";
            task.updatedAt = System.currentTimeMillis();
            Map<String, Object> photo = work.get();
            task.status = "SUCCESS";
            task.photo = photo;
            return photo;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            task.status = "FAILED";
            task.error = "任务已中断";
            throw new PhotoException(503, task.error, e);
        } catch (RuntimeException e) {
            task.status = "FAILED";
            task.error = e.getMessage() == null ? "图片处理失败" : e.getMessage();
            throw e;
        } finally {
            task.updatedAt = System.currentTimeMillis();
            if (acquired) workers.release();
        }
    }

    public List<Map<String, Object>> list(UserService.UserInfo user) {
        cleanupExpired();
        List<Task> own = new ArrayList<>();
        for (Task task : tasks.values()) if (task.userId == user.getUserId()) own.add(task);
        own.sort(Comparator.comparingLong((Task task) -> task.createdAt).reversed());
        List<Map<String, Object>> result = new ArrayList<>();
        for (Task task : own) result.add(task.view());
        return result;
    }

    private int activeCount() {
        int count = 0;
        for (Task task : tasks.values()) if ("WAITING".equals(task.status) || "PROCESSING".equals(task.status)) count++;
        return count;
    }

    private boolean validId(String id) { return id != null && id.matches("[A-Za-z0-9-]{8,64}"); }

    @Scheduled(fixedDelay = 300000)
    public void cleanupExpired() {
        long cutoff = System.currentTimeMillis() - RETAIN_MILLIS;
        tasks.entrySet().removeIf(entry -> !entry.getValue().active() && entry.getValue().updatedAt < cutoff);
    }

    private static class Task {
        final String id, filename;
        final int userId;
        final long createdAt = System.currentTimeMillis();
        volatile long updatedAt = createdAt;
        volatile String status = "WAITING", error;
        volatile Map<String, Object> photo;

        Task(String id, String filename, int userId) { this.id = id; this.filename = filename; this.userId = userId; }
        boolean active() { return "WAITING".equals(status) || "PROCESSING".equals(status); }
        Map<String, Object> view() {
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("id", id); view.put("filename", filename); view.put("status", status);
            view.put("error", error); view.put("createdAt", createdAt); view.put("updatedAt", updatedAt);
            if (photo != null) view.put("photo", photo);
            return view;
        }
    }
}
