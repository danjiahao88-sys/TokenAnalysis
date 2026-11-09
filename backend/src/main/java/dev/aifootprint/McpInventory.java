package dev.aifootprint;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.tomlj.*;
import java.io.*;
import java.nio.file.*;
import java.nio.channels.Channels;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

/** Configuration declarations only. Never executes commands or contacts MCP servers. */
@Service
public class McpInventory {
    static final int MAX_BYTES = 1024 * 1024;
    public record Server(String id, String name, String tool, String source, String transport, boolean enabled) {}
    public record Source(String id, String label, String status, int count) {}
    public record Inventory(String checkedAt, List<Source> sources, List<Server> servers) {}
    private final Path codex, claude;
    private final ObjectMapper mapper;
    public McpInventory(ObjectMapper mapper, @Value("${footprint.codex-home}") String codexHome,
                        @Value("${footprint.claude-config}") String claudeConfig) {
        this.mapper = mapper; codex = Path.of(codexHome).resolve("config.toml"); claude = Path.of(claudeConfig);
    }
    public Inventory scan() {
        List<Source> sources = new ArrayList<>(); List<Server> servers = new ArrayList<>();
        scanOne(codex, "codex-user", "Codex 用户配置", "codex", true, sources, servers);
        scanOne(claude, "claude-user", "Claude Code 用户配置", "claude-code", false, sources, servers);
        return new Inventory(Instant.now().toString(), sources, servers);
    }
    private void scanOne(Path path, String id, String label, String tool, boolean toml,
                         List<Source> sources, List<Server> all) {
        if (Files.notExists(path, LinkOption.NOFOLLOW_LINKS)) { sources.add(new Source(id,label,"missing",0)); return; }
        try {
            if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) throw new IOException();
            byte[] bytes;
            try (var channel = Files.newByteChannel(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);
                 var stream = Channels.newInputStream(channel)) { bytes = stream.readNBytes(MAX_BYTES + 1); }
            if (bytes.length > MAX_BYTES) throw new IllegalArgumentException();
            List<Server> parsed = toml ? parseToml(new String(bytes, StandardCharsets.UTF_8), tool, id) : parseJson(bytes, tool, id);
            if (parsed.size() > 200) throw new IllegalArgumentException();
            all.addAll(parsed); sources.add(new Source(id,label,"ready",parsed.size()));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) { sources.add(new Source(id,label,"invalid",0)); }
        catch (IOException | SecurityException e) { sources.add(new Source(id,label,"unreadable",0)); }
        catch (RuntimeException e) { sources.add(new Source(id,label,"invalid",0)); }
    }
    private List<Server> parseToml(String input, String tool, String source) {
        TomlParseResult root = Toml.parse(input);
        if (root.hasErrors()) throw new IllegalArgumentException();
        Object value = root.get("mcp_servers");
        if (value == null) return List.of();
        if (!(value instanceof TomlTable table)) throw new IllegalArgumentException();
        if (table.size() > 200) throw new IllegalArgumentException();
        List<Server> result = new ArrayList<>();
        for (String key : table.keySet()) {
            if (!(table.get(List.of(key)) instanceof TomlTable server)) throw new IllegalArgumentException();
            Object enabled = server.get("enabled");
            if (enabled != null && !(enabled instanceof Boolean)) throw new IllegalArgumentException();
            String transport = server.contains("command") ? "stdio" : server.contains("url") ? "http" : "unknown";
            result.add(server(key, tool, source, transport, !Boolean.FALSE.equals(enabled)));
        }
        return result;
    }
    private List<Server> parseJson(byte[] input, String tool, String source) throws IOException {
        JsonNode root = mapper.readTree(input);
        if (root == null || !root.isObject()) throw new IllegalArgumentException();
        JsonNode table = root.get("mcpServers");
        if (table == null) return List.of();
        if (!table.isObject() || table.size() > 200) throw new IllegalArgumentException();
        List<Server> result = new ArrayList<>();
        table.fields().forEachRemaining(field -> {
            JsonNode server = field.getValue();
            if (!server.isObject() || (server.has("disabled") && !server.get("disabled").isBoolean())) throw new IllegalArgumentException();
            String type = server.path("type").asText("");
            String transport = Set.of("stdio","http","sse").contains(type) ? type : server.has("command") ? "stdio" : "unknown";
            result.add(server(field.getKey(),tool,source,transport,!server.path("disabled").asBoolean(false)));
        });
        return result;
    }
    private Server server(String rawName, String tool, String source, String transport, boolean enabled) {
        // Only short configuration labels, never paths, endpoint strings or arbitrary config values.
        String name = rawName.matches("[\\p{L}\\p{N}_. -]{1,80}") ? rawName.strip() : "未展示的配置名称";
        try {
            String id = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((tool+"|"+source+"|"+rawName).getBytes(StandardCharsets.UTF_8))).substring(0,20);
            return new Server(id,name,tool,source,transport,enabled);
        } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(); }
    }
}
