package com.cloud.hub.web.photo.controller;

import com.cloud.hub.common.annotation.Log;
import com.cloud.hub.common.annotation.RequiresAdmin;
import com.cloud.hub.common.enums.BusinessType;
import com.cloud.hub.framework.security.LoginUser;
import com.cloud.hub.framework.security.SecurityUtils;
import com.cloud.hub.web.photo.service.PhotoService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 家庭相册管理后台控制器。
 * <p>
 * 提供相册存储状态概览、可见性范围切换与图片缓存清理功能。
 * 统一依托 {@link RequiresAdmin} 注解实施权限控制，消除重复鉴权样板。
 * </p>
 *
 * @author cloud
 */
@RestController
@RequestMapping("/api/admin/photos")
@RequiresAdmin
public class PhotoAdminController {

    /** 相册业务服务 */
    private final PhotoService photos;

    public PhotoAdminController(PhotoService photos) {
        this.photos = photos;
    }

    /**
     * 查询相册存储状态与容量统计。
     *
     * @return 存储状态与容量统计信息
     */
    @GetMapping("/status")
    public Map<String, Object> status() throws Exception {
        return photos.adminInfo();
    }

    /**
     * 更新相册全局可见性模式（全部可见/仅自己可见）。
     *
     * @param body 请求载荷，包含 mode
     * @return 操作结果
     */
    @PutMapping("/visibility")
    @Log(title = "相册管理", businessType = BusinessType.UPDATE)
    public Map<String, Object> visibility(@RequestBody Map<String, String> body) throws Exception {
        LoginUser adminUser = SecurityUtils.getRequiredUser();
        photos.visibility(body.get("mode"), adminUser.getUsername());
        return PhotoController.ok("查看范围已更新");
    }

    /**
     * 清理相册缩略图与高清渲染缓存。
     *
     * @return 移除的文件数量
     */
    @DeleteMapping("/cache")
    @Log(title = "相册管理", businessType = BusinessType.CLEAN)
    public Map<String, Object> clear() throws Exception {
        int removedCount = photos.clearCache();
        Map<String, Object> result = PhotoController.ok("高清缓存已清空");
        result.put("removed", removedCount);
        return result;
    }
}
