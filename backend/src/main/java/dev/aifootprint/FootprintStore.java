package dev.aifootprint;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.*;

@Repository
public class FootprintStore {
    private final JdbcTemplate jdbc;
    public FootprintStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @PostConstruct void initialize() {
        jdbc.execute("PRAGMA journal_mode=WAL");
        jdbc.execute("CREATE TABLE IF NOT EXISTS usage_event (id TEXT PRIMARY KEY, session TEXT NOT NULL, at TEXT NOT NULL, model TEXT NOT NULL, project TEXT NOT NULL, input_tokens INTEGER NOT NULL, cached_tokens INTEGER NOT NULL, output_tokens INTEGER NOT NULL, reasoning_tokens INTEGER NOT NULL, evidence TEXT NOT NULL)");
        boolean hasTool = jdbc.queryForList("PRAGMA table_info(usage_event)").stream().anyMatch(column -> "tool".equals(column.get("name")));
        if (!hasTool) jdbc.execute("ALTER TABLE usage_event ADD COLUMN tool TEXT NOT NULL DEFAULT 'codex'");
        jdbc.execute("CREATE INDEX IF NOT EXISTS usage_event_at ON usage_event(at)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS skill (id TEXT PRIMARY KEY, name TEXT, description TEXT, category TEXT, source TEXT, status TEXT)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS metadata (key TEXT PRIMARY KEY, value TEXT)");
    }
    @Transactional
    public void replace(CodexCollector.Result result, List<SkillInventory.Skill> skills) {
        jdbc.update("DELETE FROM usage_event");
        jdbc.batchUpdate("INSERT INTO usage_event (id,session,at,model,project,input_tokens,cached_tokens,output_tokens,reasoning_tokens,evidence,tool) VALUES (?,?,?,?,?,?,?,?,?,?,?)", result.usage(), 500,
                (ps,u) -> { ps.setString(1,u.id()); ps.setString(2,u.session()); ps.setString(3,u.at().toString()); ps.setString(4,u.model()); ps.setString(5,u.project()); ps.setLong(6,u.inputTokens()); ps.setLong(7,u.cachedInputTokens()); ps.setLong(8,u.outputTokens()); ps.setLong(9,u.reasoningOutputTokens()); ps.setString(10,u.evidence()); ps.setString(11,u.tool()); });
        jdbc.update("DELETE FROM skill");
        jdbc.batchUpdate("INSERT INTO skill VALUES (?,?,?,?,?,?)", skills, 100,
                (ps,s) -> { ps.setString(1,s.id()); ps.setString(2,s.name()); ps.setString(3,s.description()); ps.setString(4,s.category()); ps.setString(5,s.source()); ps.setString(6,s.status()); });
        put("collectedAt", Instant.now().toString());
        put("files", result.files()+""); put("malformedLines", result.malformedLines()+"");
        put("unreadableFiles", result.unreadableFiles()+""); put("legacyFiles", result.legacyFiles()+"");
    }
    private void put(String key, String value) { jdbc.update("INSERT INTO metadata(key,value) VALUES (?,?) ON CONFLICT(key) DO UPDATE SET value=excluded.value", key, value); }
    public List<Usage> usage() {
        return jdbc.query("SELECT * FROM usage_event ORDER BY at", (rs,n) -> new Usage(rs.getString("id"), rs.getString("session"), Instant.parse(rs.getString("at")), rs.getString("model"), rs.getString("project"), rs.getLong("input_tokens"), rs.getLong("cached_tokens"), rs.getLong("output_tokens"), rs.getLong("reasoning_tokens"), rs.getString("evidence"), rs.getString("tool")));
    }
    public List<SkillInventory.Skill> skills() {
        return jdbc.query("SELECT * FROM skill ORDER BY name", (rs,n) -> new SkillInventory.Skill(rs.getString("id"),rs.getString("name"),rs.getString("description"),rs.getString("category"),rs.getString("source"),rs.getString("status")));
    }
    public Map<String,String> metadata() {
        Map<String,String> map = new LinkedHashMap<>();
        jdbc.query("SELECT key,value FROM metadata", (org.springframework.jdbc.core.RowCallbackHandler) rs -> map.put(rs.getString(1),rs.getString(2)));
        return map;
    }
}
