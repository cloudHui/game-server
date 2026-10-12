package com.cloud.hub.web.learning.controller.admin;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cloud.hub.web.learning.model.ContentItem;
import com.cloud.hub.web.learning.model.WordItem;
import com.cloud.hub.web.learning.model.WordProblemTemplate;
import com.cloud.hub.web.learning.service.ContentService;
import com.cloud.hub.web.learning.service.WordService;

/**
 * 学习中心教学内容与题库管理员维护控制器。
 * <p>
 * 提供汉字字词库维护、各学科知识条目管理以及应用题出题模板的增删改查支持。
 * 包含严格的管理员 Token 鉴权防护。
 *
 * @author cloud
 */
@RestController
@RequestMapping("/api/learning/admin")
public class ContentAdminController {

    /** 学习中心鉴权守卫 */
    private final AdminAccess access;
    /** 汉字字词库维护服务 */
    private final WordService words;
    /** 教学知识条目与应用题模板维护服务 */
    private final ContentService content;

    public ContentAdminController(AdminAccess access,
                                  WordService words,
                                  ContentService content) {
        this.access = access;
        this.words = words;
        this.content = content;
    }

    /**
     * 查询全量字词库列表。
     *
     * @param token 会话凭证 Header
     * @return 字词条目实体列表
     * @throws Exception 鉴权未通过或查询异常
     */
    @GetMapping("/words")
    public List<WordItem> words(@RequestHeader(value = "X-Session-Token", required = false) String token) throws Exception {
        access.require(token);
        return words.list(null);
    }

    /**
     * 保存或更新字词库条目。
     *
     * @param token 会话凭证 Header
     * @param item  待保存的字词实体
     * @return 保存完成后的字词实体
     * @throws Exception 鉴权未通过或持久化异常
     */
    @PostMapping("/words")
    public WordItem saveWord(@RequestHeader(value = "X-Session-Token", required = false) String token,
                             @RequestBody WordItem item) throws Exception {
        access.require(token);
        return words.save(item);
    }

    /**
     * 删除指定字词条目。
     *
     * @param token 会话凭证 Header
     * @param id    字词唯一标识
     * @return 操作成功提示
     * @throws Exception 鉴权未通过或删除异常
     */
    @DeleteMapping("/words/{id}")
    public Map<String, Object> deleteWord(@RequestHeader(value = "X-Session-Token", required = false) String token,
                                          @PathVariable String id) throws Exception {
        access.require(token);
        words.delete(id);
        return AdminAccess.message("汉字已删除");
    }

    /**
     * 查询所有学科教学内容条目。
     *
     * @param token 会话凭证 Header
     * @return 教学内容条目列表
     * @throws Exception 鉴权未通过或查询异常
     */
    @GetMapping("/content")
    public List<ContentItem> content(@RequestHeader(value = "X-Session-Token", required = false) String token) throws Exception {
        access.require(token);
        return content.content(null);
    }

    /**
     * 保存或更新教学内容。
     *
     * @param token 会话凭证 Header
     * @param item  待保存的内容实体
     * @return 保存完成后的内容实体
     * @throws Exception 鉴权未通过或保存异常
     */
    @PostMapping("/content")
    public ContentItem saveContent(@RequestHeader(value = "X-Session-Token", required = false) String token,
                                   @RequestBody ContentItem item) throws Exception {
        access.require(token);
        return content.saveContent(item);
    }

    /**
     * 删除指定教学内容。
     *
     * @param token 会话凭证 Header
     * @param id    内容条目唯一标识
     * @return 操作成功提示
     * @throws Exception 鉴权未通过或删除异常
     */
    @DeleteMapping("/content/{id}")
    public Map<String, Object> deleteContent(@RequestHeader(value = "X-Session-Token", required = false) String token,
                                             @PathVariable String id) throws Exception {
        access.require(token);
        content.deleteContent(id);
        return AdminAccess.message("内容已删除");
    }

    /**
     * 获取全部应用题出题模板。
     *
     * @param token 会话凭证 Header
     * @return 应用题模板列表
     * @throws Exception 鉴权未通过或查询异常
     */
    @GetMapping("/templates")
    public List<WordProblemTemplate> templates(@RequestHeader(value = "X-Session-Token", required = false) String token) throws Exception {
        access.require(token);
        return content.templates();
    }

    /**
     * 保存或更新应用题出题模板。
     *
     * @param token 会话凭证 Header
     * @param item  待保存的题库模板实体
     * @return 保存完成后的题库模板实体
     * @throws Exception 鉴权未通过或保存异常
     */
    @PostMapping("/templates")
    public WordProblemTemplate saveTemplate(@RequestHeader(value = "X-Session-Token", required = false) String token,
                                            @RequestBody WordProblemTemplate item) throws Exception {
        access.require(token);
        return content.saveTemplate(item);
    }

    /**
     * 删除指定应用题出题模板。
     *
     * @param token 会话凭证 Header
     * @param id    模板唯一标识
     * @return 操作成功提示
     * @throws Exception 鉴权未通过或删除异常
     */
    @DeleteMapping("/templates/{id}")
    public Map<String, Object> deleteTemplate(@RequestHeader(value = "X-Session-Token", required = false) String token,
                                              @PathVariable String id) throws Exception {
        access.require(token);
        content.deleteTemplate(id);
        return AdminAccess.message("模板已删除");
    }
}
