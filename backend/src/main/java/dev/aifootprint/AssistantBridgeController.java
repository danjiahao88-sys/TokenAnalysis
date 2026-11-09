package dev.aifootprint;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/bridge")
public class AssistantBridgeController {
    private final AssistantAccess access; private final SnapshotService snapshots;
    private final LiveCollection live; private final McpInventory mcp; private final CodexHistoryInventory history;
    public AssistantBridgeController(AssistantAccess access,SnapshotService snapshots,LiveCollection live,McpInventory mcp,CodexHistoryInventory history) {
        this.access=access;this.snapshots=snapshots;this.live=live;this.mcp=mcp;this.history=history;
    }
    public record PairRequest(String code) {}
    @GetMapping("/health") public Map<String,Object> health(){return Map.of("product","ai-footprint-assistant","protocol",1,"version","0.4.0");}
    @PostMapping("/pair") public AssistantAccess.Grant pair(@RequestBody PairRequest body,HttpServletRequest request){return access.pair(request.getHeader("Origin"),body.code());}
    @GetMapping("/status") public LiveCollection.Status status(){return live.status();}
    @GetMapping("/snapshot") public SnapshotService.Snapshot snapshot(){return snapshots.snapshot(false);}
    @GetMapping("/export") public SnapshotService.Snapshot export(){return snapshots.snapshot(true);}
    @GetMapping("/inventory") public McpInventory.Inventory inventory(){return mcp.scan();}
    @GetMapping("/codex-history") public CodexHistoryInventory.Inventory history(){return history.scan();}
    @PostMapping("/collect") public LiveCollection.Status collect(){live.scan(true);return live.status();}
    @PostMapping("/disconnect") public Map<String,Boolean> disconnect(HttpServletRequest request){access.revoke(request.getHeader("Authorization"));return Map.of("disconnected",true);}
}
