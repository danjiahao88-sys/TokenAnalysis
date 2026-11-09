package dev.aifootprint;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.sqlite.SQLiteConfig;
import java.io.IOException;
import java.nio.file.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;

/** Last-observed thread totals for missing rollouts, never request-level accounting. */
@Service
public class CodexHistoryInventory {
    static final int LIMIT = 1000;
    public record Group(String model, int sessions, long indexTokens, String lastSeenAt) {}
    public record Inventory(String status, String checkedAt, int checkedSessions, int availableRollouts,
                            int skippedRows, boolean truncated, List<Group> groups) {}
    private final Path home;
    public CodexHistoryInventory(@Value("${footprint.codex-home}") String home) {
        this.home = Path.of(home).toAbsolutePath().normalize();
    }
    public Inventory scan() {
        String at = Instant.now().toString();
        try {
            Path database;
            if (Files.notExists(home)) return empty("missing",at);
            try (var files = Files.list(home)) {
                database = files.filter(p -> p.getFileName().toString().matches("state_[0-9]{1,6}\\.sqlite"))
                    .filter(p -> Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS))
                    .max(Comparator.comparingInt(p -> Integer.parseInt(p.getFileName().toString().replaceAll("[^0-9]",""))))
                    .orElse(null);
            }
            if (database == null) return empty("missing",at);
            SQLiteConfig config = new SQLiteConfig();
            config.setReadOnly(true); config.setBusyTimeout(2000);
            try (Connection connection = DriverManager.getConnection("jdbc:sqlite:"+database,config.toProperties());
                 Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA query_only=ON");
                Set<String> columns = new HashSet<>();
                try (ResultSet schema = statement.executeQuery("PRAGMA table_info(threads)")) {
                    while(schema.next()) columns.add(schema.getString("name"));
                }
                if (!columns.containsAll(Set.of("model","tokens_used","rollout_path","updated_at"))) return empty("incompatible",at);
                // No titles, prompts, cwd, original thread ids or configuration values are selected.
                String time = columns.contains("updated_at_ms") ? "COALESCE(updated_at_ms,updated_at*1000)" : "updated_at*1000";
                String sql = "SELECT model,tokens_used,rollout_path,"+time+" AS seen_at FROM threads ORDER BY "+time+" DESC LIMIT "+(LIMIT+1);
                Map<String,Group> groups = new HashMap<>();
                int checked=0, available=0, skipped=0; boolean truncated=false;
                try (ResultSet rows = statement.executeQuery(sql)) {
                    while (rows.next()) {
                        if (checked == LIMIT) { truncated=true; break; }
                        checked++;
                        Object rawTokens = rows.getObject("tokens_used");
                        String rawPath=rows.getString("rollout_path");
                        if (!(rawTokens instanceof Number) || !(rawTokens instanceof Long || rawTokens instanceof Integer)
                            || ((Number)rawTokens).longValue()<0 || ((Number)rawTokens).longValue()>1_000_000_000_000L || rawPath==null) { skipped++; continue; }
                        Path rollout;
                        try {
                            if (java.io.File.separatorChar=='\\' && rawPath.startsWith("\\\\?\\")) rawPath=rawPath.substring(4);
                            rollout=Path.of(rawPath).normalize();
                            if (!rollout.isAbsolute() || !(rollout.startsWith(home.resolve("sessions")) || rollout.startsWith(home.resolve("archived_sessions")))) { skipped++; continue; }
                        } catch (InvalidPathException ex) { skipped++; continue; }
                        if (Files.isRegularFile(rollout,LinkOption.NOFOLLOW_LINKS)) { available++; continue; }
                        if (!Files.notExists(rollout,LinkOption.NOFOLLOW_LINKS)) { skipped++; continue; }
                        long tokens=((Number)rawTokens).longValue();
                        String model=CodexCollector.safeLabel(Objects.toString(rows.getString("model"),"unknown")).strip();
                        if(model.isBlank()) model="unknown";
                        String seen=null;
                        long millis=rows.getLong("seen_at");
                        if(!rows.wasNull() && millis>=0 && millis<=253402300799999L) seen=Instant.ofEpochMilli(millis).toString();
                        Group old=groups.get(model);
                        long total=old==null ? tokens : Math.addExact(old.indexTokens(),tokens);
                        if(total>9_007_199_254_740_991L) return empty("incompatible",at);
                        String last=old==null ? seen : later(old.lastSeenAt(),seen);
                        groups.put(model,new Group(model,old==null?1:old.sessions()+1,total,last));
                    }
                }
                return new Inventory("ready",at,checked,available,skipped,truncated,
                    groups.values().stream().sorted(Comparator.comparingLong(Group::indexTokens).reversed().thenComparing(Group::model)).toList());
            } catch (SQLException | ArithmeticException ex) { return empty("incompatible",at); }
        } catch (IOException | SecurityException ex) { return empty("unreadable",at); }
    }
    private String later(String a,String b) {
        if(a==null) return b; if(b==null) return a;
        return Instant.parse(a).isAfter(Instant.parse(b)) ? a : b;
    }
    private Inventory empty(String status,String at) { return new Inventory(status,at,0,0,0,false,List.of()); }
}
