package com.cloud.hub.web.controller;

import org.springframework.http.ResponseEntity;import org.springframework.web.bind.annotation.*;import com.cloud.hub.web.arena.ArenaCraftService;import com.cloud.hub.web.service.UserService;import java.util.*;

import org.springframework.http.ResponseEntity;
import com.cloud.hub.web.arena.ArenaCraftService;
import com.cloud.hub.web.service.UserService;

import java.util.LinkedHashMap;
import java.util.Map;
@RestController @RequestMapping("/api/arena/library") public class ArenaCraftController{
 private final ArenaCraftService service;private final UserService users;public ArenaCraftController(ArenaCraftService service,UserService users){this.service=service;this.users=users;}
 @GetMapping public ResponseEntity<?> view(@RequestHeader(value="Authorization",required=false)String auth){UserService.UserInfo u=user(auth);if(u==null)return denied();try{return ResponseEntity.ok(service.view(u.getUserId()));}catch(Exception e){return fail(e);}}
 @PostMapping("/craft") public ResponseEntity<?> craft(@RequestHeader(value="Authorization",required=false)String auth,@RequestBody Map<String,String>b){UserService.UserInfo u=user(auth);if(u==null)return denied();try{return ResponseEntity.ok(service.craft(u.getUserId(),b.get("recipeId")));}catch(IllegalArgumentException e){return ResponseEntity.badRequest().body(error(e.getMessage()));}catch(Exception e){return fail(e);}}
 private UserService.UserInfo user(String h){return h!=null&&h.startsWith("Bearer ")?users.validateToken(h.substring(7).trim()):null;}private ResponseEntity<?> denied(){return ResponseEntity.status(401).body(error("请先登录"));}private ResponseEntity<?> fail(Exception e){return ResponseEntity.status(500).body(error(e.getMessage()));}private Map<String,Object> error(String s){Map<String,Object>m=new LinkedHashMap<>();m.put("error",s);return m;}
}
