package web.arena;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ArenaTaskCatalog {
    static final class Task {
        final String id;
        final int target;
        final int liquid;
        final int coins;
        final int fate;
        final int stones;
        final int activity;

        Task(String id, int target, int liquid, int coins, int fate, int stones, int activity) {
            this.id = id;
            this.target = target;
            this.liquid = liquid;
            this.coins = coins;
            this.fate = fate;
            this.stones = stones;
            this.activity = activity;
        }
    }

    static final class Chest {
        final int target;
        final int liquid;
        final int fate;
        final int stones;

        Chest(int target, int liquid, int fate, int stones) {
            this.target = target;
            this.liquid = liquid;
            this.fate = fate;
            this.stones = stones;
        }
    }

    private static final Map<String, Task> TASKS;
    private static final Map<Integer, Chest> CHESTS;

    static {
        Map<String, Task> tasks = new LinkedHashMap<>();
        add(tasks, new Task("login", 1, 200, 0, 0, 0, 10));
        add(tasks, new Task("dungeon", 3, 500, 0, 0, 0, 20));
        add(tasks, new Task("rank", 1, 0, 300, 0, 0, 15));
        add(tasks, new Task("skill", 2, 0, 450, 0, 0, 10));
        add(tasks, new Task("recruit", 1, 0, 0, 1, 0, 15));
        add(tasks, new Task("formation", 1, 0, 0, 0, 120, 10));
        add(tasks, new Task("grotto", 1, 300, 0, 0, 0, 10));
        add(tasks, new Task("arena", 1, 0, 400, 0, 0, 20));
        TASKS = Collections.unmodifiableMap(tasks);

        Map<Integer, Chest> chests = new LinkedHashMap<>();
        chests.put(20, new Chest(20, 0, 1, 0));
        chests.put(45, new Chest(45, 0, 0, 200));
        chests.put(70, new Chest(70, 1000, 0, 0));
        chests.put(100, new Chest(100, 0, 3, 0));
        CHESTS = Collections.unmodifiableMap(chests);
    }

    private ArenaTaskCatalog() {}

    static Map<String, Task> tasks() { return TASKS; }
    static Task task(String id) { return TASKS.get(id); }
    static Chest chest(int target) { return CHESTS.get(target); }
    static List<Integer> chestTargets() { return Arrays.asList(20, 45, 70, 100); }

    private static void add(Map<String, Task> tasks, Task task) { tasks.put(task.id, task); }
}
