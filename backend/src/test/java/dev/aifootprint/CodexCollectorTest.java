package dev.aifootprint;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.*;

class CodexCollectorTest {
    @TempDir Path home;
    final CodexCollector collector=new CodexCollector(new ObjectMapper());
    String record(String type,String payload) { return "{\"timestamp\":\"2026-11-03T23:59:00Z\",\"type\":\""+type+"\",\"payload\":"+payload+"}\n"; }
    String counts(long i,long c,long o,long r) { return "{\"input_tokens\":"+i+",\"cached_input_tokens\":"+c+",\"output_tokens\":"+o+",\"reasoning_output_tokens\":"+r+"}"; }
    String meta() { return record("session_meta","{\"id\":\"private-session\",\"cwd\":\"E:\\\\private\\\\my-project\"}")+record("turn_context","{\"model\":\"model-a\"}"); }
    String old(long i,long c,long o,long r) { return record("event_msg","{\"type\":\"token_count\",\"info\":{\"total_token_usage\":"+counts(i,c,o,r)+"}}"); }
    String modern(String id,long i,long c,long o,long r,String total) { return record("token_usage_record","{\"response_id\":\""+id+"\",\"usage\":"+counts(i,c,o,r)+",\"thread_token_usage\":"+total+"}"); }
    void write(String name,String text) throws Exception { Path root=home.resolve("sessions"); Files.createDirectories(root); Files.writeString(root.resolve(name+".jsonl"),text); }

    @Test void modernAndLegacyAreNotAddedTogetherAndResponseCopiesAreDeduped() throws Exception {
        String a=meta()+modern("response-1",100,40,20,5,counts(100,40,20,5))+old(100,40,20,5)+old(100,40,20,5);
        write("a",a); write("copy",a);
        var result=collector.collect(home);
        assertThat(result.usage()).hasSize(1);
        assertThat(result.usage().getFirst().totalTokens()).isEqualTo(120);
        assertThat(result.usage().getFirst().cachedInputTokens()).isEqualTo(40);
        assertThat(result.usage().getFirst().project()).isEqualTo("my-project");
        assertThat(collector.collect(home).usage()).isEqualTo(result.usage());
    }
    @Test void cumulativeDeltasHandleRepeatedSnapshotsResetAndMalformedTail() throws Exception {
        write("legacy",meta()+old(100,20,10,4)+old(100,20,10,4)+old(150,25,15,5)+old(20,5,3,1)+"{partial");
        var result=collector.collect(home);
        assertThat(result.usage()).hasSize(3);
        assertThat(result.usage().stream().mapToLong(Usage::totalTokens).sum()).isEqualTo(188);
        assertThat(result.malformedLines()).isEqualTo(1);
        assertThat(result.legacyFiles()).isEqualTo(1);
    }
    @Test void mixedHistoricalFormatPreservesOlderUnmatchedUsageAndModelChanges() throws Exception {
        write("mixed",meta()+old(100,20,10,4)+record("turn_context","{\"model\":\"model-b\"}")
                +modern("response-2",50,5,5,1,counts(150,25,15,5))+old(150,25,15,5));
        var result=collector.collect(home);
        assertThat(result.usage()).hasSize(2);
        assertThat(result.usage().stream().mapToLong(Usage::totalTokens).sum()).isEqualTo(165);
        assertThat(result.usage()).extracting(Usage::model).containsExactlyInAnyOrder("model-a","model-b");
    }
    @Test void archivedLogsAreIncludedButPromptBodiesAreIgnored() throws Exception {
        Path root=home.resolve("archived_sessions"); Files.createDirectories(root);
        Files.writeString(root.resolve("archived.jsonl"),meta()+record("response_item","{\"content\":\"SECRET_DO_NOT_STORE\"}")+old(10,50,2,99));
        var rows=collector.collect(home).usage();
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().cachedInputTokens()).isEqualTo(10);
        assertThat(rows.getFirst().reasoningOutputTokens()).isEqualTo(2);
        assertThat(rows.toString()).doesNotContain("SECRET_DO_NOT_STORE");
    }
}
