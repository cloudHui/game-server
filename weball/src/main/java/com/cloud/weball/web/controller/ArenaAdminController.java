package com.cloud.weball.web.controller;

import com.cloud.weball.web.arena.ArenaAdminService;
import com.cloud.weball.web.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/arena")
public class ArenaAdminController {
    private final UserService users;
    private final ArenaAdminService service;

    public ArenaAdminController(UserService users, ArenaAdminService service) {
        this.users = users;
        this.service = service;
    }

    @GetMapping("/players")
    public ResponseEntity<?> players(@RequestParam String sessionId) {
        if (notAdmin(sessionId)) return denied();
        try {
            return ResponseEntity.ok(ok("players", service.players()));
        } catch (Exception e) {
            return fail(e);
        }
    }

    @GetMapping("/state")
    public ResponseEntity<?> state(@RequestParam String sessionId, @RequestParam long userId) {
        if (notAdmin(sessionId)) return denied();
        try {
            return ResponseEntity.ok(ok("state", service.detail(userId)));
        } catch (Exception e) {
            return fail(e);
        }
    }

    @PostMapping("/resource")
    public ResponseEntity<?> resource(@RequestBody Map<String, Object> b) {
        if (notAdmin(str(b.get("sessionId")))) return denied();
        try {
            return ResponseEntity.ok(ok("state", service.adjust(num(b.get("userId")), str(b.get("resource")), num(b.get("delta")))));
        } catch (Exception e) {
            return fail(e);
        }
    }

    @PostMapping("/hero")
    public ResponseEntity<?> hero(@RequestBody Map<String, Object> b) {
        if (notAdmin(str(b.get("sessionId")))) return denied();
        try {
            return ResponseEntity.ok(ok("state", service.hero(num(b.get("userId")), str(b.get("heroId")), integer(b.get("rank")), integer(b.get("stars")), integer(b.get("skill")), integer(b.get("shards")))));
        } catch (Exception e) {
            return fail(e);
        }
    }

    @PostMapping("/item")
    public ResponseEntity<?> item(@RequestBody Map<String, Object> b) {
        if (notAdmin(str(b.get("sessionId")))) return denied();
        try {
            return ResponseEntity.ok(ok("state", service.adjustItem(num(b.get("userId")), str(b.get("itemId")), integer(b.get("delta")))));
        } catch (Exception e) {
            return fail(e);
        }
    }

    private boolean notAdmin(String sid) {
        UserService.UserInfo u = users.getSession(sid);
        return u == null || !u.isAdmin();
    }

    private ResponseEntity<?> denied() {
        return ResponseEntity.ok(error(403, "需要管理员账号"));
    }

    private ResponseEntity<?> fail(Exception e) {
        return ResponseEntity.ok(error(500, e.getMessage()));
    }

    private Map<String, Object> ok(String k, Object v) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", 0);
        m.put(k, v);
        return m;
    }

    private Map<String, Object> error(int c, String s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", c);
        m.put("msg", s);
        return m;
    }

    private String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private long num(Object o) {
        return o instanceof Number ? ((Number) o).longValue() : Long.parseLong(str(o));
    }

    private int integer(Object o) {
        return o instanceof Number ? ((Number) o).intValue() : Integer.parseInt(str(o));
    }
}
