package dev.aifootprint;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;

/** Accounting metadata only. No credentials, prompts or response text are retained. */
@Component
public class ClaudeCollector {
    private final ObjectMapper mapper;
    public ClaudeCollector(ObjectMapper mapper) { this.mapper = mapper; }
    public CodexCollector.Result collect(Path home) throws IOException {
        Path root = home.resolve("projects");
        if (!Files.isDirectory(root)) return new CodexCollector.Result(List.of(), 0, 0, 0, 0);
        Map<String,Usage> unique = new LinkedHashMap<>();
        int files = 0, malformed = 0, unreadable = 0;
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.filter(p -> Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS) && p.toString().endsWith(".jsonl")).sorted().toList()) {
                try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                    files++;
                    for (String line; (line = reader.readLine()) != null;) {
                        if (line.isBlank()) continue;
                        try {
                            JsonNode entry = mapper.readTree(line);
                            if (entry == null || !entry.isObject()) { malformed++; continue; }
                            if (!entry.path("type").asText().equals("assistant")) continue;
                            JsonNode message = entry.path("message"), usage = message.path("usage");
                            if (!usage.isObject()) continue;
                            String messageId = message.path("id").asText("");
                            if (messageId.isBlank()) { malformed++; continue; }
                            Instant at = Instant.parse(entry.path("timestamp").asText());
                            long read = count(usage, "cache_read_input_tokens");
                            long write = usage.hasNonNull("cache_creation_input_tokens") ? count(usage, "cache_creation_input_tokens")
                                : count(usage.path("cache_creation"), "ephemeral_5m_input_tokens") + count(usage.path("cache_creation"), "ephemeral_1h_input_tokens");
                            // Claude excludes cache read/write from input_tokens; our contract includes both.
                            long input = count(usage, "input_tokens") + read + write, output = count(usage, "output_tokens");
                            if (input == 0 && output == 0) continue;
                            String id = "claude:" + CodexCollector.hash(messageId + "|" + entry.path("requestId").asText(""));
                            String session = entry.path("sessionId").asText(path.getFileName().toString());
                            String cwd = entry.path("cwd").asText("").replace('\\','/').replaceAll("/+$", "");
                            String project = cwd.isBlank() ? "未归属项目" : CodexCollector.safeLabel(cwd.substring(cwd.lastIndexOf('/') + 1));
                            String model = message.path("model").asText("unknown");
                            if (model.isBlank()) model = "unknown";
                            Usage value = new Usage(id, session, at, CodexCollector.safeLabel(model), project, input, read, output, 0, "claude-message-usage", "claude-code");
                            unique.merge(id, value, ClaudeCollector::merge);
                        } catch (IOException | IllegalArgumentException | java.time.DateTimeException ex) { malformed++; }
                    }
                } catch (IOException ex) { unreadable++; }
            }
        }
        return new CodexCollector.Result(List.copyOf(unique.values()), files, malformed, unreadable, 0);
    }
    private static long count(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) return 0;
        if (!value.isIntegralNumber() || !value.canConvertToLong() || value.asLong() < 0 || value.asLong() > 1_000_000_000_000L) throw new IllegalArgumentException("Invalid usage counter");
        return value.asLong();
    }
    private static Usage merge(Usage a, Usage b) {
        // Streaming entries repeat cumulative message usage; never add those fragments together.
        long input = Math.max(a.inputTokens(), b.inputTokens()), cached = Math.max(a.cachedInputTokens(), b.cachedInputTokens());
        return new Usage(a.id(), a.session(), a.at().isBefore(b.at()) ? a.at() : b.at(), a.model().equals("unknown") ? b.model() : a.model(), a.project(),
            input, Math.min(input,cached), Math.max(a.outputTokens(),b.outputTokens()), 0, a.evidence(), "claude-code");
    }
}
