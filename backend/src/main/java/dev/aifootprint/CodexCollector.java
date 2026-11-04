package dev.aifootprint;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;

/** Reads only accounting metadata; prompt/response bodies are never persisted. */
@Component
public class CodexCollector {
    private final ObjectMapper mapper;
    public CodexCollector(ObjectMapper mapper) { this.mapper = mapper; }
    public record Result(List<Usage> usage, int files, int malformedLines, int unreadableFiles, int legacyFiles) {}
    private record Legacy(Usage usage, Usage.Counters cumulative) {}

    public Result collect(Path home) throws IOException {
        Map<String, Usage> unique = new LinkedHashMap<>();
        int files = 0, malformed = 0, unreadable = 0, legacyFiles = 0;
        for (String directory : List.of("sessions", "archived_sessions")) {
            Path root = home.resolve(directory);
            if (!Files.isDirectory(root)) continue;
            try (Stream<Path> paths = Files.walk(root)) {
                for (Path path : paths.filter(p -> Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)
                        && p.toString().endsWith(".jsonl")).sorted().toList()) {
                    try {
                        FileResult result = parse(path);
                        result.usage.forEach(u -> unique.putIfAbsent(u.id(), u));
                        malformed += result.malformed;
                        if (result.legacy) legacyFiles++;
                        files++;
                    } catch (IOException ex) { unreadable++; }
                }
            }
        }
        return new Result(List.copyOf(unique.values()), files, malformed, unreadable, legacyFiles);
    }

    private record FileResult(List<Usage> usage, int malformed, boolean legacy) {}
    private FileResult parse(Path file) throws IOException {
        String session = hash(file.getFileName().toString()), project = "未归属项目", model = "unknown";
        List<Usage> requests = new ArrayList<>();
        List<Legacy> legacy = new ArrayList<>();
        Set<Usage.Counters> coveredTotals = new HashSet<>();
        Set<String> coveredAt = new HashSet<>();
        Usage.Counters before = new Usage.Counters(0, 0, 0, 0);
        int malformed = 0, segment = 0;
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            for (String line; (line = reader.readLine()) != null;) {
                if (line.isBlank()) continue;
                JsonNode entry;
                try { entry = mapper.readTree(line); } catch (IOException ex) { malformed++; continue; }
                if (entry == null || !entry.isObject()) { malformed++; continue; }
                JsonNode p = entry.path("payload");
                String type = entry.path("type").asText();
                if (type.equals("session_meta")) {
                    session = p.path("id").asText(p.path("session_id").asText(session));
                    String cwd = p.path("cwd").asText("").replace('\\', '/').replaceAll("/+$", "");
                    if (!cwd.isBlank()) project = safeLabel(cwd.substring(cwd.lastIndexOf('/') + 1));
                    continue;
                }
                if (type.equals("turn_context")) { model = safeLabel(p.path("model").asText("unknown")); continue; }
                if (!type.equals("token_usage_record") && !(type.equals("event_msg") && p.path("type").asText().equals("token_count"))) continue;
                Instant at;
                try { at = Instant.parse(entry.path("timestamp").asText()); }
                catch (RuntimeException ex) { malformed++; continue; }
                if (type.equals("token_usage_record")) {
                    String response = p.path("response_id").asText("");
                    if (response.isBlank() || !p.path("usage").isObject()) { malformed++; continue; }
                    Usage.Counters c = Usage.Counters.read(p.path("usage"));
                    if (c.total() == 0) continue;
                    String requestModel = safeLabel(p.path("model").asText(model));
                    requests.add(usage(hash("codex:response:" + response), session, at, requestModel, project, c, "request"));
                    if (p.path("thread_token_usage").isObject()) coveredTotals.add(Usage.Counters.read(p.path("thread_token_usage")));
                    coveredAt.add(at + "|" + c);
                } else {
                    JsonNode info = p.path("info");
                    if (!info.path("total_token_usage").isObject()) continue;
                    Usage.Counters total = Usage.Counters.read(info.path("total_token_usage"));
                    if (total.input() < before.input() || total.output() < before.output()) segment++;
                    Usage.Counters delta = total.delta(before);
                    before = total;
                    if (delta.total() == 0) continue;
                    // Same session copied to another file still has a stable event identity.
                    String id = hash("codex:legacy:" + session + ":" + segment + ":" + total);
                    legacy.add(new Legacy(usage(id, session, at, model, project, delta, "cumulative-delta"), total));
                }
            }
        }
        boolean hasLegacy = false;
        for (Legacy old : legacy) {
            Usage u = old.usage;
            Usage.Counters delta = new Usage.Counters(u.inputTokens(), u.cachedInputTokens(), u.outputTokens(), u.reasoningOutputTokens());
            if (!coveredTotals.contains(old.cumulative) && !coveredAt.contains(u.at() + "|" + delta)) {
                requests.add(u); hasLegacy = true;
            }
        }
        return new FileResult(requests, malformed, hasLegacy);
    }
    private static Usage usage(String id, String session, Instant at, String model, String project, Usage.Counters c, String evidence) {
        return new Usage(id, session, at, model, project, c.input(), c.cached(), c.output(), c.reasoning(), evidence);
    }
    static String safeLabel(String value) { return value.replaceAll("[\\p{Cntrl}]", "").substring(0, Math.min(100, value.replaceAll("[\\p{Cntrl}]", "").length())); }
    static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception ex) { throw new IllegalStateException(ex); }
    }
}
