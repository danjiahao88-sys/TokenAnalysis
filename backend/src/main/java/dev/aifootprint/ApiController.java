package dev.aifootprint;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.io.IOException;
import java.net.URI;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ApiController {
    private final SnapshotService service;
    public ApiController(SnapshotService service) { this.service = service; }
    @GetMapping("/health") public Map<String,String> health() { return Map.of("status","ok","version","0.2.0"); }
    @GetMapping("/snapshot") public ResponseEntity<SnapshotService.Snapshot> snapshot() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.snapshot(false));
    }
    @PostMapping("/collect") public SnapshotService.CollectionInfo collect(HttpServletRequest request) {
        if (!"local".equals(request.getHeader("X-AI-Footprint"))) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Local request header required");
        String origin=request.getHeader("Origin");
        if (origin!=null) {
            try {
                URI uri=URI.create(origin);
                if (!"http".equals(uri.getScheme()) || !java.util.Set.of("localhost","127.0.0.1").contains(uri.getHost())) throw new IllegalArgumentException();
                // Requests through Vite retain its Origin. Never permit arbitrary local ports.
                if (!java.util.Set.of(5173,4173,request.getLocalPort()).contains(uri.getPort())) throw new IllegalArgumentException();
            } catch (IllegalArgumentException ex) { throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Origin rejected"); }
        }
        try { return service.collect(); }
        catch (IOException ex) { throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"采集未完成，请检查本机日志目录与读取权限；原有数据已保留。"); }
    }
    @GetMapping("/export") public ResponseEntity<SnapshotService.Snapshot> export() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=ai-footprint-public.json")
                .body(service.snapshot(true));
    }
}
