package com.cloud.hub.web.learning.service;

import com.cloud.hub.web.learning.model.WordItem;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 常用生字生词学习与字库管理服务。
 * <p>
 * 提供默认生字库自动填充、按年级学段筛选生字列表，以及汉字条目的录入、修改与删除。
 *
 * @author cloud
 */
@Service
public class WordService {

    private final ObjectMapper mapper;
    private final JsonFileStore store;

    public WordService(ObjectMapper mapper, JsonFileStore store) {
        this.mapper = mapper;
        this.store = store;
    }

    /**
     * 容器启动时如检测未初始化字词库，自动装载 classpath 预置数据。
     *
     * @throws Exception 初始化异常
     */
    @PostConstruct
    public synchronized void init() throws Exception {
        if (!store.exists(store.path("content", "words"))) {
            try (InputStream input = getClass().getResourceAsStream("/content/words.json")) {
                if (input == null) {
                    throw new IllegalStateException("缺少字词数据预设文件");
                }
                store.write(store.path("content", "words"),
                        mapper.readValue(input, new TypeReference<List<WordItem>>() {
                        }));
            }
        }
    }

    /**
     * 按学段过滤查询生字列表。
     *
     * @param stage 学段 (如 "幼小衔接", "一年级")，为空则返回全量
     * @return 匹配的生字条目列表
     * @throws Exception 存储异常
     */
    public synchronized List<WordItem> list(String stage) throws Exception {
        List<WordItem> words = all();
        if (stage == null || stage.trim().isEmpty()) {
            return words;
        }
        return words.stream().filter(word -> stage.equals(word.stage)).collect(Collectors.toList());
    }

    /**
     * 新增或更新生字词条目。
     *
     * @param item 生字词数据
     * @return 保存后的条目
     * @throws Exception 存储异常
     */
    public synchronized WordItem save(WordItem item) throws Exception {
        List<WordItem> words = all();
        if (item.character == null || item.character.trim().isEmpty()) {
            throw new IllegalArgumentException("汉字不能为空");
        }
        if (item.id == null || item.id.trim().isEmpty()) {
            item.id = "w-" + UUID.randomUUID().toString().substring(0, 8);
            words.add(item);
        } else {
            upsertWord(words, item);
        }
        if (item.stage == null) {
            item.stage = "幼小衔接";
        }
        store.write(store.path("content", "words"), words);
        return item;
    }

    private void upsertWord(List<WordItem> words, WordItem item) {
        int index = -1;
        for (int i = 0; i < words.size(); i++) {
            if (item.id.equals(words.get(i).id)) {
                index = i;
                break;
            }
        }
        if (index >= 0) {
            words.set(index, item);
        } else {
            words.add(item);
        }
    }

    /**
     * 根据 ID 删除指定的生字条目。
     *
     * @param id 生字 ID
     * @throws Exception 存储异常
     */
    public synchronized void delete(String id) throws Exception {
        List<WordItem> words = all();
        if (!words.removeIf(word -> id.equals(word.id))) {
            throw new IllegalArgumentException("找不到指定汉字条目");
        }
        store.write(store.path("content", "words"), words);
    }

    private List<WordItem> all() throws Exception {
        return store.readList(store.path("content", "words"), new TypeReference<List<WordItem>>() {
        });
    }
}
