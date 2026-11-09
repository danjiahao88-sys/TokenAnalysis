package dev.aifootprint;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.*;

class AssistantAccessTest {
    @TempDir Path root;
    @Test void grantsPersistAsHashesAndCanBeRevokedAcrossRestarts() {
        var jdbc=new JdbcTemplate(new DriverManagerDataSource("jdbc:sqlite:"+root.resolve("grants.db")));
        String origin="https://example.com";var access=new AssistantAccess(jdbc,origin);
        var code=access.newCode();assertThat(code.code()).matches("[0-9]{8}");
        String token=access.pair(origin,code.code()).token();
        assertThat(jdbc.queryForObject("SELECT token_hash FROM assistant_grant",String.class)).hasSize(64).isNotEqualTo(token);
        var restarted=new AssistantAccess(jdbc,origin);assertThat(restarted.authorized(origin,"Bearer "+token)).isTrue();
        assertThat(restarted.authorized("https://other.example","Bearer "+token)).isFalse();
        restarted.revokeAll();assertThat(access.authorized(origin,"Bearer "+token)).isFalse();
        assertThatThrownBy(()->new AssistantAccess(jdbc,"https://example.com/path")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new AssistantAccess(jdbc,"http://public.example")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void failedPairingIsRateLimitedAndNeverIssuesADataToken() {
        var jdbc=new JdbcTemplate(new DriverManagerDataSource("jdbc:sqlite:"+root.resolve("throttle.db")));
        var access=new AssistantAccess(jdbc,"https://example.com");access.newCode();
        for(int i=0;i<5;i++) assertThatThrownBy(()->access.pair("https://example.com","bad-code"))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class).hasMessageContaining("403");
        assertThatThrownBy(()->access.pair("https://example.com",access.newCode().code())).hasMessageContaining("429");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM assistant_grant",Integer.class)).isZero();
    }
}
