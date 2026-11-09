package dev.aifootprint;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.sql.*;
import static org.assertj.core.api.Assertions.*;

class CodexHistoryInventoryTest {
    @TempDir Path root;
    Connection database(String name, boolean millis) throws Exception {
        Connection c=DriverManager.getConnection("jdbc:sqlite:"+root.resolve(name));
        c.createStatement().execute("CREATE TABLE threads(model TEXT,tokens_used INTEGER,rollout_path TEXT,updated_at INTEGER,title TEXT"+(millis?",updated_at_ms INTEGER":"")+")");
        return c;
    }
    void row(Connection c,String model,Object tokens,String filename,long at) throws Exception {
        try(var s=c.prepareStatement("INSERT INTO threads(model,tokens_used,rollout_path,updated_at,title) VALUES(?,?,?,?,?)")) {
            s.setString(1,model);s.setObject(2,tokens);s.setString(3,root.resolve("sessions").resolve(filename).toString());s.setLong(4,at);s.setString(5,"PRIVATE_CHAT_TITLE");s.executeUpdate();
        }
    }
    @Test void missingRolloutsAreSeparateFromExistingLogsAndDatabaseRemainsUnchanged() throws Exception {
        Files.createDirectories(root.resolve("sessions")); Files.writeString(root.resolve("sessions/present.jsonl"),"PRIVATE_PROMPT_NOT_READ");
        try(var c=database("state_5.sqlite",true)) {
            row(c,"deepseek-v4-flash",9000,"present.jsonl",1_790_000_000L);
            row(c,"deepseek-v4-flash",120,"PRIVATE_SESSION_A.jsonl",1_790_000_001L);
            row(c,"deepseek-v4-flash",50,"PRIVATE_SESSION_B.jsonl",1_790_000_002L);
            row(c,"other-model",30,"PRIVATE_SESSION_C.jsonl",1_790_000_003L);
        }
        byte[] before=Files.readAllBytes(root.resolve("state_5.sqlite"));
        var inventory=new CodexHistoryInventory(root.toString());var result=inventory.scan();
        assertThat(result.status()).isEqualTo("ready");assertThat(result.checkedSessions()).isEqualTo(4);assertThat(result.availableRollouts()).isEqualTo(1);
        assertThat(result.groups()).hasSize(2);
        assertThat(result.groups().getFirst()).isEqualTo(new CodexHistoryInventory.Group("deepseek-v4-flash",2,170,"2026-09-21T14:13:22Z"));
        assertThat(result.skippedRows()).isZero();assertThat(result.truncated()).isFalse();
        assertThat(new ObjectMapper().writeValueAsString(result)).doesNotContain("PRIVATE_",root.toString(),"rollout_path","title","inputTokens","outputTokens");
        inventory.scan();assertThat(Files.readAllBytes(root.resolve("state_5.sqlite"))).isEqualTo(before);
    }
    @Test void missingOrIncompatibleIndexDoesNotCreateAFileOrSilentlyUseAnOlderVersion() throws Exception {
        var inventory=new CodexHistoryInventory(root.toString());assertThat(inventory.scan().status()).isEqualTo("missing");
        try(var files=Files.list(root)){assertThat(files.toList()).isEmpty();}
        try(var c=database("state_5.sqlite",false)){row(c,"deepseek-v4-flash",10,"missing.jsonl",1_790_000_001L);}
        assertThat(inventory.scan().groups()).hasSize(1);
        try(var c=DriverManager.getConnection("jdbc:sqlite:"+root.resolve("state_6.sqlite"))){c.createStatement().execute("CREATE TABLE unrelated(secret TEXT)");}
        assertThat(inventory.scan().status()).isEqualTo("incompatible");assertThat(inventory.scan().groups()).isEmpty();
    }
    @Test void invalidValuesAndPathsAreSkippedRatherThanFabricatedAsZeroUsage() throws Exception {
        try(var c=database("state_5.sqlite",false)) {
            row(c,"deepseek",-1,"negative.jsonl",1_790_000_001L);row(c,"deepseek","not-a-number","text.jsonl",1_790_000_001L);
            row(c,"deepseek",null,"null.jsonl",1_790_000_001L);row(c,"deepseek",15,"../../OUTSIDE.jsonl",1_790_000_001L);
            row(c,"deepseek",2,"valid.jsonl",1_790_000_001L);
        }
        var result=new CodexHistoryInventory(root.toString()).scan();assertThat(result.status()).isEqualTo("ready");
        assertThat(result.skippedRows()).isEqualTo(4);assertThat(result.groups().getFirst().indexTokens()).isEqualTo(2);
    }
    @Test void boundedReadsReportTruncationAndDoNotPretendToCoverTheEntireHistory() throws Exception {
        try(var c=database("state_5.sqlite",false)) {
            c.setAutoCommit(false);
            for(int i=0;i<=CodexHistoryInventory.LIMIT;i++)row(c,"deepseek",1,"missing-"+i+".jsonl",1_790_000_000L+i);
            c.commit();
        }
        var result=new CodexHistoryInventory(root.toString()).scan();assertThat(result.truncated()).isTrue();
        assertThat(result.checkedSessions()).isEqualTo(CodexHistoryInventory.LIMIT);assertThat(result.groups().getFirst().indexTokens()).isEqualTo(CodexHistoryInventory.LIMIT);
    }
}
