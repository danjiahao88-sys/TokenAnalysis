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
        r.add("footprint.claude-config",()->root.resolve("claude.json").toString());
        r.add("footprint.live.enabled",()->"false");
    }
    @Autowired MockMvc mvc;
    @Autowired AssistantAccess access;
    @Autowired LiveCollection live;
    @Autowired com.fasterxml.jackson.databind.ObjectMapper mapper;

    @Test void websitePairingIsOriginBoundRevocableAndDoesNotOpenTheLegacyApi() throws Exception {
        String origin="https://danjiahao88-sys.github.io";
        mvc.perform(get("/bridge/snapshot").header("Origin",origin)).andExpect(status().isUnauthorized());
        mvc.perform(get("/bridge/health").header("Origin","https://evil.example")).andExpect(status().isForbidden())
            .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
        mvc.perform(options("/bridge/pair").header("Origin",origin).header("Access-Control-Request-Method","POST")
            .header("Access-Control-Request-Headers","content-type").header("Access-Control-Request-Private-Network","true"))
            .andExpect(status().isNoContent()).andExpect(header().string("Access-Control-Allow-Origin",origin))
            .andExpect(header().string("Access-Control-Allow-Private-Network","true"));
        mvc.perform(options("/bridge/pair").header("Origin",origin).header("Access-Control-Request-Method","DELETE"))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/assistant/code").header("Origin",origin).header("X-AI-Footprint","local"))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/assistant/code")).andExpect(status().isForbidden());
        String code=access.newCode().code();
        String body=mvc.perform(post("/bridge/pair").header("Origin",origin).contentType("application/json").content("{\"code\":\""+code+"\"}"))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andReturn().getResponse().getContentAsString();
        String token=mapper.readTree(body).path("token").asText();assertThat(token).hasSize(43);
        mvc.perform(get("/bridge/snapshot").header("Origin",origin).header("Authorization","Bearer "+token))
            .andExpect(status().isOk()).andExpect(jsonPath("$.mode").value("local"));
        mvc.perform(get("/bridge/snapshot").header("Origin","http://127.0.0.1:5173").header("Authorization","Bearer "+token))
            .andExpect(status().isUnauthorized());
        mvc.perform(get("/bridge/snapshot").header("Origin",origin).header("Authorization","Bearer "+token)
            .with(r->{r.setServerName("evil.example");return r;})).andExpect(status().isForbidden());
        mvc.perform(get("/api/snapshot").header("Origin",origin)).andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
        mvc.perform(post("/bridge/pair").header("Origin",origin).contentType("application/json").content("{\"code\":\""+code+"\"}"))
            .andExpect(status().isForbidden());
        mvc.perform(post("/bridge/disconnect").header("Origin",origin).header("Authorization","Bearer "+token)).andExpect(status().isOk());
        mvc.perform(get("/bridge/snapshot").header("Origin",origin).header("Authorization","Bearer "+token)).andExpect(status().isUnauthorized());
    }

    @Test void automaticCollectionDetectsNewUsageAndKeepsPreviousDataWhenDirectoriesDisappear() throws Exception {
        Path file=root.resolve("codex/sessions/test.jsonl");String original=Files.readString(file);
        try {
            live.scan(true);
            String unchanged=live.status().lastSuccessAt();live.scan(false);assertThat(live.status().lastSuccessAt()).isEqualTo(unchanged);
            Files.writeString(file,"\n{\"timestamp\":\"2026-11-04T00:01:00Z\",\"type\":\"token_usage_record\",\"payload\":{\"response_id\":\"live-new\",\"usage\":{\"input_tokens\":25,\"output_tokens\":5}}}\n",StandardOpenOption.APPEND);
            live.scan(false);assertThat(live.status().state()).isEqualTo("ready");
            mvc.perform(get("/api/snapshot")).andExpect(jsonPath("$.usage[0].totalTokens").value(150));
            Files.move(root.resolve("codex"),root.resolve("offline"));
            try {live.scan(false);assertThat(live.status().state()).isEqualTo("error");
                mvc.perform(get("/api/snapshot")).andExpect(jsonPath("$.usage[0].totalTokens").value(150));
            } finally {Files.move(root.resolve("offline"),root.resolve("codex"));}
        } finally {Files.writeString(file,original);live.scan(true);}
    }
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
        mvc.perform(get("/api/inventory").with(request -> { request.setServerName("evil.example"); return request; })).andExpect(status().isForbidden());
        mvc.perform(get("/api/inventory")).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(jsonPath("$.sources.length()").value(2)).andExpect(jsonPath("$.servers.length()").value(0));
        mvc.perform(get("/api/codex-history").with(request -> { request.setServerName("evil.example"); return request; })).andExpect(status().isForbidden());
        mvc.perform(get("/api/codex-history")).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(jsonPath("$.status").value("missing")).andExpect(jsonPath("$.groups.length()").value(0));
    }
}
