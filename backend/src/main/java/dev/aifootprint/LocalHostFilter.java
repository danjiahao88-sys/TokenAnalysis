package dev.aifootprint;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.stereotype.Component;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.Set;

/** Defense against browser DNS rebinding to the loopback-only API. */
@Component
@Order(-100)
public class LocalHostFilter extends OncePerRequestFilter {
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!Set.of("127.0.0.1","localhost","::1","[::1]").contains(request.getServerName())) {
            response.sendError(403); return;
        }
        response.setHeader("X-Content-Type-Options","nosniff");
        response.setHeader("X-Frame-Options","DENY");
        response.setHeader("Referrer-Policy","no-referrer");
        if(request.getRequestURI().startsWith("/api/")) response.setHeader("Cache-Control","no-store");
        response.setHeader("Cross-Origin-Resource-Policy","same-origin");
        chain.doFilter(request,response);
    }
}
