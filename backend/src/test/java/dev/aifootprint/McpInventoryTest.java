package dev.aifootprint;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.*;

class McpInventoryTest {
    @TempDir Path root;
    private final ObjectMapper mapper = new ObjectMapper();
    McpInventory inventory() { return new McpInventory(mapper,root.toString(),root.resolve("claude.json").toString()); }
    @Test void parsesQuotedNamesAndDisabledWithoutReturningPrivateFields() throws Exception {
        Files.writeString(root.resolve("config.toml"), """
            [mcp_servers."docs.example"]
            command = "PRIVATE_EXECUTABLE"
            args = ["PRIVATE_PATH"]
            enabled = false
            [mcp_servers."docs.example".env]
            TOKEN = "PRIVATE_TOKEN"
            [mcp_servers.remote]
            url = "https://private.example/SECRET_ENDPOINT"
            """);
        Files.writeString(root.resolve("claude.json"), """
            {"mcpServers":{"research":{"type":"sse","url":"PRIVATE_URL","headers":{"Authorization":"PRIVATE_AUTH"}},"/private/path":{"command":"PRIVATE_COMMAND","disabled":true}},"projects":{"/private/project":{"mcpServers":{"not-user-scope":{}}}}}
            """);
        var result = inventory().scan();
        assertThat(result.sources()).allMatch(s -> s.status().equals("ready"));
        assertThat(result.servers()).hasSize(4);
        assertThat(result.servers()).anyMatch(s -> s.name().equals("docs.example") && !s.enabled() && s.transport().equals("stdio"));
        assertThat(result.servers()).anyMatch(s -> s.name().equals("remote") && s.transport().equals("http"));
        assertThat(result.servers()).anyMatch(s -> s.name().equals("research") && s.transport().equals("sse"));
        String json = mapper.writeValueAsString(result);
        assertThat(json).doesNotContain("PRIVATE_","SECRET_","private.example","/private/","not-user-scope",root.toString(),"Authorization","command","args","env");
        assertThat(inventory().scan().servers()).isEqualTo(result.servers());
    }
    @Test void missingInvalidAndOversizeAreDistinctAndDoNotPartiallyPublish() throws Exception {
        assertThat(inventory().scan().sources()).allMatch(s -> s.status().equals("missing"));
        Files.writeString(root.resolve("config.toml"), "[mcp_servers.good]\ncommand='ok'\n[mcp_servers.bad]\nenabled='not boolean'");
        Files.writeString(root.resolve("claude.json"), "{\"mcpServers\": []}");
        assertThat(inventory().scan().sources()).allMatch(s -> s.status().equals("invalid"));
        assertThat(inventory().scan().servers()).isEmpty();
        Files.write(root.resolve("config.toml"), new byte[McpInventory.MAX_BYTES+1]);
        assertThat(inventory().scan().sources().getFirst().status()).isEqualTo("invalid");
        Files.writeString(root.resolve("claude.json"), "{}");
        assertThat(inventory().scan().sources().get(1).status()).isEqualTo("ready");
        Files.writeString(root.resolve("claude.json"), "{BROKEN");
        assertThat(inventory().scan().sources().get(1).status()).isEqualTo("invalid");
        Files.writeString(root.resolve("config.toml"), "[mcp_servers.\n");
        assertThat(inventory().scan().sources().getFirst().status()).isEqualTo("invalid");
    }
}
