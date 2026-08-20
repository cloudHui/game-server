package com.cloud.web.controller;

import org.springframework.http.ResponseEntity;
import web.arena.ArenaJourneyService;
import web.service.UserService;

import java.util.HashMap;
import java.util.Map;
@RestController @RequestMapping("/api/arena/journey") public class ArenaJourneyController{private final ArenaJourneyService s;private final UserService users;public ArenaJourneyController(ArenaJourneyService s,UserService users){this.s=s;this.users=users;}@GetMapping public ResponseEntity<?> state(@RequestHeader(value="Authorization",required=false)String h){UserService.UserInfo u=user(h);if(u==null)return ResponseEntity.status(401).body(err("请先登录"));try{return ResponseEntity.ok(s.state(u.getUserId()));}catch(Exception e){return ResponseEntity.status(500).body(err(e.getMessage()));}}@PostMapping public ResponseEntity<?> run(@RequestHeader(value="Authorization",required=false)String h,@RequestBody Map<String,Object>b){UserService.UserInfo u=user(h);if(u==null)return ResponseEntity.status(401).body(err("请先登录"));try{return ResponseEntity.ok(s.explore(u.getUserId(),((Number)b.get("map")).intValue(),((Number)b.get("runs")).intValue()));}catch(IllegalArgumentException e){return ResponseEntity.badRequest().body(err(e.getMessage()));}catch(Exception e){return ResponseEntity.status(500).body(err(e.getMessage()));}}private UserService.UserInfo user(String h){return h!=null&&h.startsWith("Bearer ")?users.validateToken(h.substring(7).trim()):null;}private Map<String,Object>err(String x){Map<String,Object>m=new HashMap<>();m.put("error",x);return m;}}
