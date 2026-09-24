package com.cloud.hub.web.learning.service;

import com.cloud.hub.web.learning.model.ContentItem;
import com.cloud.hub.web.learning.model.WordProblemTemplate;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 学习内容资源与应用题模板管理服务。
 * <p>
 * 提供预设应用题模板自动建表初始化、按学科过滤学习资料、新增/编辑/删除自定义内容及模板。
 *
 * @author cloud
 */
@Service
public class ContentService {

    private final JsonFileStore store;

    public ContentService(JsonFileStore store) {
        this.store = store;
    }

    /**
     * 容器初始化时装配默认应用题模板。
     *
     * @throws Exception 初始化异常
     */
    @PostConstruct
    public synchronized void init() throws Exception {
        if (!store.exists(store.path("content", "word-problems"))) {
            List<WordProblemTemplate> defaults = new ArrayList<>();
            defaults.add(template("幼小衔接", "add", "小明有{a}个苹果，妈妈又给他{b}个，一共有多少个苹果？", 10));
            defaults.add(template("幼小衔接", "sub", "树上有{a}只小鸟，飞走了{b}只，还剩多少只？", 10));
            defaults.add(template("一年级", "add", "书架上有{a}本故事书，又放上{b}本，现在有多少本？", 20));
            defaults.add(template("一年级", "sub", "盒子里原有{a}支铅笔，用掉了{b}支，还剩多少支？", 20));
            store.write(store.path("content", "word-problems"), defaults);
        }
    }

    /**
     * 按学科查询学习内容条目列表。
     *
     * @param subject 学科名称 (为空则拉取全部)
     * @return 内容列表
     * @throws Exception 存储异常
     */
    public synchronized List<ContentItem> content(String subject) throws Exception {
        List<ContentItem> all = store.readList(store.path("content", "items"), new TypeReference<List<ContentItem>>() {
        });
        if (subject == null || subject.isEmpty()) {
            return all;
        }
        return all.stream().filter(item -> subject.equals(item.subject)).collect(Collectors.toList());
    }

    /**
     * 新增或更新学习内容条目。
     *
     * @param item 内容实体
     * @return 保存后的实体
     * @throws Exception 存储异常
     */
    public synchronized ContentItem saveContent(ContentItem item) throws Exception {
        List<ContentItem> all = content(null);
        LocalDateTime now = LocalDateTime.now();
        if (item.title == null || item.title.trim().isEmpty()) {
            throw new IllegalArgumentException("标题不能为空");
        }
        if (item.id == null || item.id.isEmpty()) {
            item.id = UUID.randomUUID().toString();
            item.createdAt = now;
            all.add(item);
        } else {
            replace(all, item.id, item);
        }
        item.updatedAt = now;
        if (item.subject == null) {
            item.subject = "语文";
        }
        if (item.stage == null) {
            item.stage = "幼小衔接";
        }
        store.write(store.path("content", "items"), all);
        return item;
    }

    /**
     * 删除指定学习内容条目。
     *
     * @param id 内容 ID
     * @throws Exception 存储异常
     */
    public synchronized void deleteContent(String id) throws Exception {
        List<ContentItem> all = content(null);
        if (!all.removeIf(item -> id.equals(item.id))) {
            throw new IllegalArgumentException("找不到内容");
        }
        store.write(store.path("content", "items"), all);
    }

    /**
     * 查询所有应用题生成模板。
     *
     * @return 模板列表
     * @throws Exception 存储异常
     */
    public synchronized List<WordProblemTemplate> templates() throws Exception {
        return store.readList(store.path("content", "word-problems"), new TypeReference<List<WordProblemTemplate>>() {
        });
    }

    /**
     * 保存或更新应用题模板。
     *
     * @param item 模板数据
     * @return 保存后的模板
     * @throws Exception 存储异常
     */
    public synchronized WordProblemTemplate saveTemplate(WordProblemTemplate item) throws Exception {
        if (item.template == null || !item.template.contains("{a}") || !item.template.contains("{b}")) {
            throw new IllegalArgumentException("文字题模板必须包含{a}和{b}");
        }
        List<WordProblemTemplate> all = templates();
        if (item.id == null || item.id.isEmpty()) {
            item.id = UUID.randomUUID().toString();
            item.createdAt = LocalDateTime.now();
            all.add(item);
        } else {
            replace(all, item.id, item);
        }
        item.maxNumber = Math.max(5, Math.min(1000, item.maxNumber));
        store.write(store.path("content", "word-problems"), all);
        return item;
    }

    /**
     * 删除指定应用题模板。
     *
     * @param id 模板 ID
     * @throws Exception 存储异常
     */
    public synchronized void deleteTemplate(String id) throws Exception {
        List<WordProblemTemplate> all = templates();
        if (!all.removeIf(item -> id.equals(item.id))) {
            throw new IllegalArgumentException("找不到模板");
        }
        store.write(store.path("content", "word-problems"), all);
    }

    private WordProblemTemplate template(String stage, String operation, String text, int max) {
        WordProblemTemplate item = new WordProblemTemplate();
        item.id = UUID.randomUUID().toString();
        item.stage = stage;
        item.operation = operation;
        item.template = text;
        item.maxNumber = max;
        item.createdAt = LocalDateTime.now();
        return item;
    }

    private <T> void replace(List<T> list, String id, T item) {
        for (int i = 0; i < list.size(); i++) {
            Object current = list.get(i);
            String currentId = current instanceof ContentItem ? ((ContentItem) current).id
                    : ((WordProblemTemplate) current).id;
            if (id.equals(currentId)) {
                list.set(i, item);
                return;
            }
        }
        list.add(item);
    }
}
