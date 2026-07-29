package web.identity;

import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;

@Component
public class SessionResolver {
    public String resolveCurrent(String explicitToken) {
        if (explicitToken != null && !explicitToken.trim().isEmpty()) return explicitToken.trim();
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes == null ? null : resolve(attributes.getRequest());
    }

    public String resolve(HttpServletRequest request) {
        String header = request.getHeader("X-Session-Token");
        if (header != null && !header.trim().isEmpty()) return header.trim();
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie cookie : cookies) {
            if ("sessionId".equals(cookie.getName())) return cookie.getValue();
        }
        return null;
    }
}
