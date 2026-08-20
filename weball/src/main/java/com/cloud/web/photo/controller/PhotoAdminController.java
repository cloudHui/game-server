package com.cloud.web.photo.controller;

import web.identity.SessionResolver;
import web.photo.controller.PhotoController;
import web.photo.model.PhotoException;
import web.photo.service.PhotoService;
import web.service.UserService;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/photos")
public class PhotoAdminController {
    private final PhotoService photos;private final UserService users;private final SessionResolver sessions;
    public PhotoAdminController(PhotoService p,UserService u,SessionResolver s){photos=p;users=u;sessions=s;}
    private UserService.UserInfo admin(HttpServletRequest r){UserService.UserInfo u=users.getSession(sessions.resolve(r));if(u==null)throw new PhotoException(401,"请先登录");if(!u.isAdmin())throw new PhotoException(403,"需要管理员权限");return u;}
    @GetMapping("/status")public Map<String,Object> status(HttpServletRequest r)throws Exception{admin(r);return photos.adminInfo();}
    @PutMapping("/visibility")public Map<String,Object> visibility(@RequestBody Map<String,String>b,HttpServletRequest r)throws Exception{UserService.UserInfo u=admin(r);photos.visibility(b.get("mode"),u.getUsername());return PhotoController.ok("查看范围已更新");}
    @DeleteMapping("/cache")public Map<String,Object> clear(HttpServletRequest r)throws Exception{admin(r);int n=photos.clearCache();Map<String,Object>m=PhotoController.ok("高清缓存已清空");m.put("removed",n);return m;}
}
