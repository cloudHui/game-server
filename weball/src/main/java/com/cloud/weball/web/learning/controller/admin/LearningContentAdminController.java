package com.cloud.weball.web.learning.controller.admin;

import web.learning.controller.admin.LearningAdminAccess;
import web.learning.model.ContentItem;
import web.learning.model.WordItem;
import web.learning.model.WordProblemTemplate;
import web.learning.service.ContentService;
import web.learning.service.WordService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/learning/admin")
public class LearningContentAdminController {
    private final LearningAdminAccess access;
    private final WordService words;
    private final ContentService content;

    public LearningContentAdminController(LearningAdminAccess access, WordService words,
                                          ContentService content) {
        this.access = access;
        this.words = words;
        this.content = content;
    }

    @GetMapping("/words")
    public List<WordItem> words(@RequestHeader(value = "X-Session-Token", required = false) String token) throws Exception {
        access.require(token);
        return words.list(null);
    }

    @PostMapping("/words")
    public WordItem saveWord(@RequestHeader(value = "X-Session-Token", required = false) String token,
                             @RequestBody WordItem item) throws Exception {
        access.require(token);
        return words.save(item);
    }

    @DeleteMapping("/words/{id}")
    public Map<String, Object> deleteWord(@RequestHeader(value = "X-Session-Token", required = false) String token,
                                          @PathVariable String id) throws Exception {
        access.require(token);
        words.delete(id);
        return message("汉字已删除");
    }

    @GetMapping("/content")
    public List<ContentItem> content(@RequestHeader(value = "X-Session-Token", required = false) String token) throws Exception {
        access.require(token);
        return content.content(null);
    }

    @PostMapping("/content")
    public ContentItem saveContent(@RequestHeader(value = "X-Session-Token", required = false) String token,
                                   @RequestBody ContentItem item) throws Exception {
        access.require(token);
        return content.saveContent(item);
    }

    @DeleteMapping("/content/{id}")
    public Map<String, Object> deleteContent(@RequestHeader(value = "X-Session-Token", required = false) String token,
                                             @PathVariable String id) throws Exception {
        access.require(token);
        content.deleteContent(id);
        return message("内容已删除");
    }

    @GetMapping("/templates")
    public List<WordProblemTemplate> templates(@RequestHeader(value = "X-Session-Token", required = false) String token)
            throws Exception {
        access.require(token);
        return content.templates();
    }

    @PostMapping("/templates")
    public WordProblemTemplate saveTemplate(@RequestHeader(value = "X-Session-Token", required = false) String token,
                                            @RequestBody WordProblemTemplate item) throws Exception {
        access.require(token);
        return content.saveTemplate(item);
    }

    @DeleteMapping("/templates/{id}")
    public Map<String, Object> deleteTemplate(@RequestHeader(value = "X-Session-Token", required = false) String token,
                                              @PathVariable String id) throws Exception {
        access.require(token);
        content.deleteTemplate(id);
        return message("模板已删除");
    }

    private Map<String, Object> message(String value) {
        return java.util.Collections.<String, Object>singletonMap("message", value);
    }
}
