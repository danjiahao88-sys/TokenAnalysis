package dev.aifootprint;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import java.nio.file.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@AutoConfigureMockMvc
class ApiIntegrationTest {
    static final Path root;
    static {
        try {
            root=Files.createTempDirectory("footprint-test");
            Path sessions=Files.createDirectories(root.resolve("codex/sessions"));
            Files.writeString(sessions.resolve("test.jsonl"),"""
                {"type":"session_meta","payload":{"id":"PRIVATE_SESSION","cwd":"/private/SECRET_PROJECT"}}
                {"type":"turn_context","payload":{"model":"test-model"}}
                {"timestamp":"2026-11-03T23:59:00Z","type":"token_usage_record","payload":{"response_id":"r1","usage":{"input_tokens":100,"cached_input_tokens":40,"output_tokens":20}}}
                """);
            Path skill=Files.createDirectories(root.resolve("codex/skills/SECRET_SKILL"));
            Files.writeString(skill.resolve("SKILL.md"),"---\nname: SECRET_SKILL\ndescription: PRIVATE_DESCRIPTION\n---\nPRIVATE_INSTRUCTIONS");
        } catch(Exception ex) { throw new ExceptionInInitializerError(ex); }
    }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",()->"jdbc:sqlite:"+root.resolve("test.db"));
        r.add("footprint.codex-home",()->root.resolve("codex").toString());
        r.add("footprint.claude-home",()->root.resolve("claude").toString());
    }
    @Autowired MockMvc mvc;
    @Test void collectIsIdempotentAndExportIsAnAllowlistedPublicSnapshot() throws Exception {
        mvc.perform(post("/api/collect").header("X-AI-Footprint","local")).andExpect(status().isOk());
        mvc.perform(post("/api/collect").header("X-AI-Footprint","local")).andExpect(status().isOk());
        mvc.perform(get("/api/snapshot")).andExpect(status().isOk()).andExpect(jsonPath("$.usage.length()").value(1))
                .andExpect(jsonPath("$.usage[0].totalTokens").value(120)).andExpect(jsonPath("$.usage[0].date").value("2026-11-04"));
        String body=mvc.perform(get("/api/export")).andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("public")).andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("SECRET_PROJECT","SECRET_SKILL","PRIVATE_SESSION","PRIVATE_DESCRIPTION","PRIVATE_INSTRUCTIONS","/private/");
    }
    @Test void crossOriginCollectionIsRejected() throws Exception {
        mvc.perform(post("/api/collect")).andExpect(status().isForbidden());
        mvc.perform(post("/api/collect").header("X-AI-Footprint","local").header("Origin","https://evil.example")).andExpect(status().isForbidden());
        mvc.perform(post("/api/collect").header("X-AI-Footprint","local").header("Origin","http://localhost:9999")).andExpect(status().isForbidden());
        mvc.perform(get("/api/snapshot").with(request -> { request.setServerName("evil.example"); return request; })).andExpect(status().isForbidden());
    }
}
