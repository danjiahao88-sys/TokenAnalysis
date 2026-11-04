package dev.aifootprint;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.nio.file.*;
import java.time.*;
import java.util.*;

@Service
public class SnapshotService {
    public record Row(String date, String tool, String model, String project, String sessionRef,
                      long inputTokens, long cachedInputTokens, long outputTokens, long reasoningOutputTokens, long totalTokens) {}
    public record Tool(String id, String name, String status, String description, String url) {}
    public record CollectionInfo(String collectedAt, int files, int malformedLines, int unreadableFiles, int legacyFiles) {}
    public record Snapshot(int schemaVersion, String mode, String generatedAt, String timezone,
                           CollectionInfo collection, List<Row> usage, List<Tool> tools,
                           List<SkillInventory.Skill> skills, List<String> rules) {}
    private final FootprintStore store;
    private final CodexCollector collector;
    private final SkillInventory inventory;
    private final Path home;
    private final ZoneId zone;
    public SnapshotService(FootprintStore store, CodexCollector collector, SkillInventory inventory,
                           @Value("${footprint.codex-home}") String home, @Value("${footprint.timezone}") String timezone) {
        this.store = store; this.collector = collector; this.inventory = inventory;
        this.home = Path.of(home); this.zone = ZoneId.of(timezone);
    }
    public synchronized CollectionInfo collect() throws IOException {
        if (!Files.isDirectory(home)) throw new IOException("Codex data directory is unavailable");
        CodexCollector.Result result = collector.collect(home);
        // Do not replace a complete previous snapshot with a partially unreadable scan.
        if (result.unreadableFiles() > 0) throw new IOException("Some session files could not be read");
        store.replace(result, inventory.scan(home));
        return collection();
    }
    private CollectionInfo collection() {
        Map<String,String> m = store.metadata();
        return new CollectionInfo(m.get("collectedAt"), integer(m,"files"), integer(m,"malformedLines"), integer(m,"unreadableFiles"), integer(m,"legacyFiles"));
    }
    private int integer(Map<String,String> m, String k) { return Integer.parseInt(m.getOrDefault(k,"0")); }

    public synchronized Snapshot snapshot(boolean publish) {
        List<Usage> events = store.usage();
        Map<String,String> sessionNames = new LinkedHashMap<>(), projectNames = new TreeMap<>();
        events.stream().map(Usage::project).distinct().sorted().forEach(p -> projectNames.put(p, "项目 " + String.format("%02d",projectNames.size()+1)));
        record Key(String date, String model, String project, String session) {}
        Map<Key,long[]> buckets = new LinkedHashMap<>();
        for (Usage u : events) {
            String ref = sessionNames.computeIfAbsent(u.session(), ignored -> "s" + (sessionNames.size()+1));
            String project = publish ? projectNames.get(u.project()) : u.project();
            Key key = new Key(u.at().atZone(zone).toLocalDate().toString(), u.model(), project, ref);
            long[] c = buckets.computeIfAbsent(key, ignored -> new long[4]);
            c[0]+=u.inputTokens(); c[1]+=u.cachedInputTokens(); c[2]+=u.outputTokens(); c[3]+=u.reasoningOutputTokens();
        }
        List<Row> rows = buckets.entrySet().stream().map(e -> {
            Key k=e.getKey(); long[] c=e.getValue();
            return new Row(k.date,"codex",k.model,k.project,k.session,c[0],c[1],c[2],c[3],c[0]+c[2]);
        }).toList();
        CollectionInfo info = collection();
        List<Tool> tools = List.of(
            new Tool("codex","Codex",info.files()>0 ? "connected" : "empty", "读取本机 Codex 会话的计数元数据，包含 sessions 与 archived_sessions。", "https://developers.openai.com/codex/"),
            new Tool("trae","Trae","planned","尚未接入使用量数据；不会将安装状态推算为 Token 消耗。","https://www.trae.cn/"),
            new Tool("claude-code","Claude Code","planned","采集适配器待扩展，当前没有纳入统计。","https://code.claude.com/docs/"));
        List<SkillInventory.Skill> skills = store.skills();
        if (publish) {
            List<SkillInventory.Skill> sanitized = new ArrayList<>(); int index=0;
            for (var s : skills) sanitized.add(new SkillInventory.Skill("skill-"+(++index),"Skill "+String.format("%02d",index),"已匿名化的本机 Skill。",s.category(),s.source(),"installed"));
            skills=sanitized;
        }
        return new Snapshot(1,publish ? "public" : "local",Instant.now().toString(),zone.getId(),info,rows,tools,skills,List.of(
            "总 Token = 输入 + 输出；缓存输入包含在输入内，推理输出包含在输出内。",
            "以日志记录为准，不代表全部账号或设备的用量，也不等同于实际账单。",
            "优先按 response_id 去重；旧格式使用累计差值。旧格式分叉历史可能重复，需结合覆盖提示解读。",
            "Skills 仅统计用户与系统 skills 目录中的已安装清单，不推断调用次数；不包含插件缓存。",
            "公开快照隐藏原会话 ID、项目名称和 Skill 名称，不包含对话、路径、密钥及账单。"));
    }
}
