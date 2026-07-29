package web.learning.controller.admin;

import org.springframework.web.bind.annotation.*;
import web.learning.model.Invite;
import web.learning.model.Student;
import web.learning.service.InviteService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/learning/admin/invites")
public class LearningInviteAdminController {
    private final LearningAdminAccess access;
    private final InviteService invites;

    public LearningInviteAdminController(LearningAdminAccess access, InviteService invites) {
        this.access = access;
        this.invites = invites;
    }

    @GetMapping
    public List<Invite> list(@RequestHeader(value = "X-Session-Token", required = false) String token) throws Exception {
        access.require(token);
        return invites.list();
    }

    @PostMapping
    public Invite create(@RequestHeader(value = "X-Session-Token", required = false) String token,
                         @RequestBody InviteRequest request) throws Exception {
        Student operator = access.require(token);
        return invites.create(operator.username, request == null ? null : request.note,
                request == null || request.maxUses <= 0 ? 1 : request.maxUses,
                request == null || request.validDays <= 0 ? 7 : request.validDays);
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> revoke(@RequestHeader(value = "X-Session-Token", required = false) String token,
                                      @PathVariable String id) throws Exception {
        access.require(token);
        invites.revoke(id);
        return java.util.Collections.<String, Object>singletonMap("message", "邀请已作废");
    }

    public static class InviteRequest {
        public String note;
        public int maxUses = 1;
        public int validDays = 7;
    }
}
