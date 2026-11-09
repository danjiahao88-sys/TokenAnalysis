package dev.aifootprint;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/assistant")
public class AssistantLocalController {
    private final AssistantAccess access;private final LiveCollection live;
    public AssistantLocalController(AssistantAccess access,LiveCollection live){this.access=access;this.live=live;}
    @PostMapping("/code") public AssistantAccess.PairingCode code(HttpServletRequest request){LocalRequest.require(request);return access.newCode();}
    @PostMapping("/revoke") public Map<String,Boolean> revoke(HttpServletRequest request){LocalRequest.require(request);access.revokeAll();return Map.of("revoked",true);}
    @GetMapping("/status") public LiveCollection.Status status(){return live.status();}
    @PostMapping("/startup") public Map<String,Boolean> startup(@RequestBody Map<String,Boolean> body,HttpServletRequest request) throws Exception {
        LocalRequest.require(request);boolean enabled=Boolean.TRUE.equals(body.get("enabled"));DesktopAssistant.startup(enabled);return Map.of("enabled",enabled);
    }
}
