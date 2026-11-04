package dev.aifootprint;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

public record Usage(String id, String session, Instant at, String model, String project,
                    long inputTokens, long cachedInputTokens, long outputTokens, long reasoningOutputTokens,
                    String evidence, String tool) {
    public Usage(String id, String session, Instant at, String model, String project, long inputTokens,
                 long cachedInputTokens, long outputTokens, long reasoningOutputTokens, String evidence) {
        this(id, session, at, model, project, inputTokens, cachedInputTokens, outputTokens, reasoningOutputTokens, evidence, "codex");
    }
    public long totalTokens() { return inputTokens + outputTokens; }

    public record Counters(long input, long cached, long output, long reasoning) {
        static Counters read(JsonNode node) {
            long input = positive(node.path("input_tokens").asLong());
            long output = positive(node.path("output_tokens").asLong());
            return new Counters(input, Math.min(input, positive(node.path("cached_input_tokens").asLong())),
                    output, Math.min(output, positive(node.path("reasoning_output_tokens").asLong())));
        }
        private static long positive(long value) { return Math.max(0, value); }
        long total() { return input + output; }
        Counters delta(Counters before) {
            // A reset starts a new accounting segment; cached/reasoning remain subsets.
            if (input < before.input || output < before.output) return this;
            long di = input - before.input, dout = output - before.output;
            return new Counters(di, Math.min(di, Math.max(0, cached - before.cached)), dout,
                    Math.min(dout, Math.max(0, reasoning - before.reasoning)));
        }
    }
}
