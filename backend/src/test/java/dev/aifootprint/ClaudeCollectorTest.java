package dev.aifootprint;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.*;

class ClaudeCollectorTest {
    @TempDir Path home;
    @Test void normalizesCachesDeduplicatesStreamingAndCopiedSubagents() throws Exception {
        Path project = Files.createDirectories(home.resolve("projects/example"));
        String first = """
            {"type":"user","message":{"content":"PRIVATE_PROMPT"}}
            {"type":"assistant","sessionId":"same","requestId":"r1","timestamp":"2026-11-03T23:59:00Z","cwd":"/private/Project","message":{"id":"m1","model":"claude-test","content":[{"text":"PRIVATE_ANSWER"}],"usage":{"input_tokens":10,"cache_read_input_tokens":20,"cache_creation_input_tokens":30,"output_tokens":1}}}
            {"type":"assistant","sessionId":"same","requestId":"r1","timestamp":"2026-11-03T23:59:01Z","cwd":"/private/Project","message":{"id":"m1","model":"claude-test","usage":{"input_tokens":10,"cache_read_input_tokens":20,"cache_creation_input_tokens":30,"output_tokens":8}}}
            """;
        Files.writeString(project.resolve("main.jsonl"), first + "{broken");
        Files.writeString(Files.createDirectories(project.resolve("session/subagents")).resolve("agent.jsonl"), first);
        var result = new ClaudeCollector(new ObjectMapper()).collect(home);
        assertThat(result.files()).isEqualTo(2); assertThat(result.malformedLines()).isEqualTo(1);
        assertThat(result.usage()).hasSize(1);
        Usage u = result.usage().getFirst();
        assertThat(u.tool()).isEqualTo("claude-code"); assertThat(u.inputTokens()).isEqualTo(60);
        assertThat(u.cachedInputTokens()).isEqualTo(20); assertThat(u.outputTokens()).isEqualTo(8); assertThat(u.totalTokens()).isEqualTo(68);
        assertThat(u.toString()).doesNotContain("PRIVATE_PROMPT","PRIVATE_ANSWER","/private/");
    }
    @Test void nestedCacheWritesUnknownModelInvalidCountersAndMissingDirectory() throws Exception {
        var collector = new ClaudeCollector(new ObjectMapper());
        assertThat(collector.collect(home).files()).isZero();
        Path logs = Files.createDirectories(home.resolve("projects/p"));
        Files.writeString(logs.resolve("s.jsonl"), """
            {"type":"assistant","timestamp":"2026-11-04T00:00:00Z","message":{"id":"m2","usage":{"input_tokens":2,"cache_creation":{"ephemeral_5m_input_tokens":3,"ephemeral_1h_input_tokens":4},"output_tokens":5}}}
            {"type":"assistant","timestamp":"2026-11-04T00:00:00Z","message":{"id":"bad","usage":{"input_tokens":-1,"output_tokens":4}}}
            """);
        var result = collector.collect(home);
        assertThat(result.malformedLines()).isEqualTo(1); assertThat(result.usage()).hasSize(1);
        assertThat(result.usage().getFirst().inputTokens()).isEqualTo(9);
        assertThat(result.usage().getFirst().model()).isEqualTo("unknown");
    }
}
