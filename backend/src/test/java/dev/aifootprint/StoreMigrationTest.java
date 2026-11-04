package dev.aifootprint;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class StoreMigrationTest {
    @TempDir Path root;
    @Test void oldRowsSurviveMigrationAndToolSessionsStayDistinct() {
        var jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:sqlite:" + root.resolve("old.db")));
        jdbc.execute("CREATE TABLE usage_event (id TEXT PRIMARY KEY, session TEXT NOT NULL, at TEXT NOT NULL, model TEXT NOT NULL, project TEXT NOT NULL, input_tokens INTEGER NOT NULL, cached_tokens INTEGER NOT NULL, output_tokens INTEGER NOT NULL, reasoning_tokens INTEGER NOT NULL, evidence TEXT NOT NULL)");
        jdbc.update("INSERT INTO usage_event VALUES ('old','same','2026-11-04T00:00:00Z','test','private-project',10,0,2,0,'test')");
        var store = new FootprintStore(jdbc); store.initialize(); store.initialize();
        Usage codex = store.usage().getFirst(); assertThat(codex.tool()).isEqualTo("codex"); assertThat(codex.totalTokens()).isEqualTo(12);
        Usage claude = new Usage("claude:new", "same", Instant.parse("2026-11-04T00:00:00Z"), "test", "private-project", 20, 5, 3, 0, "test", "claude-code");
        store.replace(new CodexCollector.Result(List.of(codex,claude),2,0,0,0),List.of());
        var mapper = new ObjectMapper();
        var service = new SnapshotService(store,new CodexCollector(mapper),new SkillInventory(),new ClaudeCollector(mapper),root.resolve("codex").toString(),root.resolve("claude").toString(),"Asia/Shanghai");
        var snapshot = service.snapshot(true);
        assertThat(snapshot.usage()).hasSize(2);
        assertThat(snapshot.usage()).extracting(SnapshotService.Row::sessionRef).doesNotHaveDuplicates();
        assertThat(snapshot.usage()).extracting(SnapshotService.Row::tool).containsExactlyInAnyOrder("codex","claude-code");
        assertThat(snapshot.usage()).extracting(SnapshotService.Row::project).containsOnly("项目 01");
        assertThatThrownBy(service::collect).isInstanceOf(java.io.IOException.class);
        assertThat(store.usage()).hasSize(2);
    }
}
