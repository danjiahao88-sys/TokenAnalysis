package dev.aifootprint;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.net.URI;
import java.util.Set;

final class LocalRequest {
    static void require(HttpServletRequest request) {
        if(!"local".equals(request.getHeader("X-AI-Footprint"))) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        String origin=request.getHeader("Origin");
        if(origin==null) return;
        try {
            URI uri=URI.create(origin);
            if(!"http".equals(uri.getScheme()) || !Set.of("127.0.0.1","localhost").contains(uri.getHost())
                || !Set.of(5173,4173,request.getLocalPort()).contains(uri.getPort())
                || uri.getRawUserInfo()!=null || uri.getRawQuery()!=null || uri.getRawFragment()!=null
                || (uri.getRawPath()!=null && !uri.getRawPath().isEmpty())) throw new IllegalArgumentException();
        }catch(IllegalArgumentException ex){throw new ResponseStatusException(HttpStatus.FORBIDDEN);}
    }
}
