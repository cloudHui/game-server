package com.cloud.hub.web.learning.controller;

import com.cloud.hub.web.learning.service.AuthService;
import com.cloud.hub.web.learning.service.LibraryService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 开放学习库接口控制器。
 * <p>
 * 图卡、词汇、词典、诗词、汉字统一支持分页与分类标签检索；汉字笔顺动画轨迹按需单独按字读取。
 *
 * @author cloud
 */
@RestController
@RequestMapping("/api/learning/library")
public class LibraryController {

    private final AuthService auth;
    private final LibraryService library;

    public LibraryController(AuthService auth, LibraryService library) {
        this.auth = auth;
        this.library = library;
    }

    /**
     * 查询本地各项开源数据集资源就绪情况。
     */
    @GetMapping("/status")
    public Map<String, Object> status(@RequestHeader(value = "X-Session-Token", required = false) String token) throws Exception {
        auth.requirePermission(token, "RESOURCES");
        return library.status();
    }

    /**
     * 汉字列表翻页浏览，或指定 value 获取单字笔顺矢量轨迹。
     */
    @GetMapping("/character")
    public Object character(@RequestHeader(value = "X-Session-Token", required = false) String token,
                            @RequestParam(required = false) String value,
                            @RequestParam(defaultValue = "") String query,
                            @RequestParam(defaultValue = "") String tag,
                            @RequestParam(defaultValue = "1") int page,
                            @RequestParam(defaultValue = "48") int size) throws Exception {
        auth.requirePermission(token, "CHINESE");
        if (value != null && !value.trim().isEmpty()) {
            return library.character(value.trim());
        }
        return library.characterPage(query, tag, page, size);
    }

    /**
     * 英汉词典综合检索翻页。
     */
    @GetMapping("/dictionary")
    public Map<String, Object> dictionary(@RequestHeader(value = "X-Session-Token", required = false) String token,
                                          @RequestParam(defaultValue = "") String query,
                                          @RequestParam(defaultValue = "") String tag,
                                          @RequestParam(defaultValue = "1") int page,
                                          @RequestParam(defaultValue = "30") int size) throws Exception {
        auth.requirePermission(token, "ENGLISH");
        return library.dictionaryPage(query, tag, page, size);
    }

    /**
     * 古诗文全库与精选翻页检索。
     */
    @GetMapping("/poetry")
    public Map<String, Object> poetry(@RequestHeader(value = "X-Session-Token", required = false) String token,
                                      @RequestParam(defaultValue = "") String query,
                                      @RequestParam(defaultValue = "") String dynasty,
                                      @RequestParam(defaultValue = "") String tag,
                                      @RequestParam(defaultValue = "1") int page,
                                      @RequestParam(defaultValue = "20") int size) throws Exception {
        auth.requirePermission(token, "CHINESE");
        return library.poetryPage(query, dynasty, tag, page, size);
    }

    /**
     * 查询教材目录列表（无前缀分级）。
     */
    @GetMapping("/textbooks")
    public List<JsonNode> textbooks(@RequestHeader(value = "X-Session-Token", required = false) String token,
                                    @RequestParam(defaultValue = "") String query) throws Exception {
        auth.requirePermission(token, "RESOURCES");
        return library.textbooks(query);
    }

    /**
     * 教材目录树层级浏览与搜索。
     */
    @GetMapping("/textbooks/tree")
    public Map<String, Object> textbooksTree(@RequestHeader(value = "X-Session-Token", required = false) String token,
                                             @RequestParam(defaultValue = "") String prefix,
                                             @RequestParam(defaultValue = "") String query) throws Exception {
        auth.requirePermission(token, "RESOURCES");
        return library.textbooksTree(prefix, query);
    }

    /**
     * 儿童英语图卡分页浏览。
     */
    @GetMapping("/english")
    public Map<String, Object> english(@RequestHeader(value = "X-Session-Token", required = false) String token,
                                       @RequestParam(defaultValue = "") String query,
                                       @RequestParam(defaultValue = "") String tag,
                                       @RequestParam(defaultValue = "1") int page,
                                       @RequestParam(defaultValue = "24") int size) throws Exception {
        auth.requirePermission(token, "ENGLISH");
        return library.englishKidsPage(query, tag, page, size);
    }

    /**
     * 常用英语高频词汇翻页。
     */
    @GetMapping("/vocab")
    public Map<String, Object> vocab(@RequestHeader(value = "X-Session-Token", required = false) String token,
                                     @RequestParam(defaultValue = "") String query,
                                     @RequestParam(defaultValue = "") String tag,
                                     @RequestParam(defaultValue = "1") int page,
                                     @RequestParam(defaultValue = "30") int size) throws Exception {
        auth.requirePermission(token, "ENGLISH");
        return library.englishVocabPage(query, tag, page, size);
    }
}
