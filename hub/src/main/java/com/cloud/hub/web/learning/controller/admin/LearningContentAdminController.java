package com.cloud.hub.web.learning.controller.admin;

import com.cloud.hub.web.learning.model.ContentItem;
import com.cloud.hub.web.learning.model.WordItem;
import com.cloud.hub.web.learning.model.WordProblemTemplate;
import com.cloud.hub.web.learning.service.ContentService;
import com.cloud.hub.web.learning.service.WordService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 学习中心教学内容与题库管理员维护控制器。
 *
 * @author cloud
 */
@RestController
@RequestMapping("/api/learning/admin")
public class LearningContentAdminController {

    private final LearningAdminAccess access;
    private final WordService words;
    private final ContentService content;

    public LearningContentAdminController(LearningAdminAccess access,
                                          WordService words,
                                          ContentService content) {
        this.access = access;
        this.words = words;
        this.content = content;
    }

    /**
     * 查询全量字词库列表。
     */
    @GetMapping("/words")
    public List<WordItem> words(@RequestHeader(value = "X-Session-Token", required = false) String token) throws Exception {
        access.require(token);
        return words.list(null);
    }

    /**
     * 保存或更新字词库条目。
     */
    @PostMapping("/words")
    public WordItem saveWord(@RequestHeader(value = "X-Session-Token", required = false) String token,
                             @RequestBody WordItem item) throws Exception {
        access.require(token);
        return words.save(item);
    }

    /**
     * 删除指定字词条目。
     */
    @DeleteMapping("/words/{id}")
    public Map<String, Object> deleteWord(@RequestHeader(value = "X-Session-Token", required = false) String token,
                                          @PathVariable String id) throws Exception {
        access.require(token);
        words.delete(id);
        return message("汉字已删除");
    }

    /**
     * 查询所有学科教学内容条目。
     */
    @GetMapping("/content")
    public List<ContentItem> content(@RequestHeader(value = "X-Session-Token", required = false) String token) throws Exception {
        access.require(token);
        return content.content(null);
    }

    /**
     * 保存或更新教学内容。
     */
    @PostMapping("/content")
    public ContentItem saveContent(@RequestHeader(value = "X-Session-Token", required = false) String token,
                                   @RequestBody ContentItem item) throws Exception {
        access.require(token);
        return content.saveContent(item);
    }

    /**
     * 删除指定教学内容。
     */
    @DeleteMapping("/content/{id}")
    public Map<String, Object> deleteContent(@RequestHeader(value = "X-Session-Token", required = false) String token,
                                             @PathVariable String id) throws Exception {
        access.require(token);
        content.deleteContent(id);
        return message("内容已删除");
    }

    /**
     * 获取全部应用题出题模板。
     */
    @GetMapping("/templates")
    public List<WordProblemTemplate> templates(@RequestHeader(value = "X-Session-Token", required = false) String token) throws Exception {
        access.require(token);
        return content.templates();
    }

    /**
     * 保存或更新应用题出题模板。
     */
    @PostMapping("/templates")
    public WordProblemTemplate saveTemplate(@RequestHeader(value = "X-Session-Token", required = false) String token,
                                            @RequestBody WordProblemTemplate item) throws Exception {
        access.require(token);
        return content.saveTemplate(item);
    }

    /**
     * 删除指定应用题出题模板。
     */
    @DeleteMapping("/templates/{id}")
    public Map<String, Object> deleteTemplate(@RequestHeader(value = "X-Session-Token", required = false) String token,
                                              @PathVariable String id) throws Exception {
        access.require(token);
        content.deleteTemplate(id);
        return message("模板已删除");
    }

    private Map<String, Object> message(String value) {
        return Collections.singletonMap("message", value);
    }
}
