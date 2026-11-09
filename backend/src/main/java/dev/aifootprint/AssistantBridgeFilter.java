package dev.aifootprint;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.*;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.List;

@Component
@Order(-90)
public class AssistantBridgeFilter extends OncePerRequestFilter {
    private final AssistantAccess access;
    private final CorsConfiguration cors=new CorsConfiguration();
    private final DefaultCorsProcessor processor=new DefaultCorsProcessor();
    public AssistantBridgeFilter(AssistantAccess access) {
        this.access=access;
        cors.setAllowedOrigins(List.copyOf(access.origins()));
        cors.setAllowedMethods(List.of("GET","POST"));
        cors.setAllowedHeaders(List.of("Authorization","Content-Type"));
        cors.setAllowCredentials(false);cors.setAllowPrivateNetwork(true);cors.setMaxAge(300L);
    }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        if(!request.getRequestURI().startsWith("/bridge/")){chain.doFilter(request,response);return;}
        response.setHeader("Cache-Control","no-store");
        String origin=request.getHeader("Origin");
        if(!access.allows(origin)){response.setStatus(403);return;}
        if(!processor.processRequest(cors,request,response)) return;
        if(CorsUtils.isPreFlightRequest(request)){response.setStatus(204);return;}
        String path=request.getRequestURI();
        if(!path.equals("/bridge/health") && !path.equals("/bridge/pair") && !access.authorized(origin,request.getHeader("Authorization"))) {
            response.setStatus(401);return;
        }
        chain.doFilter(request,response);
    }
}
