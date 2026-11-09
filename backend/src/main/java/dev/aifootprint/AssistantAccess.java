package dev.aifootprint;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.net.URI;
import java.security.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

/** A short-lived local pairing code grants one browser a revocable, origin-bound token. */
@Service
public class AssistantAccess {
    private final JdbcTemplate jdbc;
    private final Set<String> origins;
    private final SecureRandom random = new SecureRandom();
    private String code;
    private Instant expires = Instant.EPOCH, window = Instant.EPOCH;
    private int attempts;
    public record PairingCode(String code, String expiresAt, Set<String> origins) {}
    public record Grant(String token) {}
    public AssistantAccess(JdbcTemplate jdbc, @Value("${footprint.assistant.origins}") String configured) {
        this.jdbc=jdbc;
        Set<String> approved=new TreeSet<>();
        for(String origin:configured.split(",")) {
            origin=origin.strip(); URI uri=URI.create(origin);
            if(uri.getHost()==null || uri.getRawUserInfo()!=null || uri.getRawQuery()!=null || uri.getRawFragment()!=null
                || (uri.getRawPath()!=null && !uri.getRawPath().isEmpty())
                || !("https".equals(uri.getScheme()) || ("http".equals(uri.getScheme()) && Set.of("127.0.0.1","localhost").contains(uri.getHost()))))
                throw new IllegalArgumentException("Assistant origins must be exact HTTPS origins or local development origins");
            approved.add(origin);
        }
        origins=Set.copyOf(approved);
        jdbc.execute("CREATE TABLE IF NOT EXISTS assistant_grant (token_hash TEXT PRIMARY KEY, origin TEXT NOT NULL, created_at TEXT NOT NULL)");
    }
    public Set<String> origins() { return origins; }
    public boolean allows(String origin) { return origin!=null && origins.contains(origin); }
    public synchronized PairingCode newCode() {
        code=String.format(Locale.ROOT,"%08d",random.nextInt(100_000_000)); expires=Instant.now().plusSeconds(300);
        return new PairingCode(code,expires.toString(),origins);
    }
    public synchronized Grant pair(String origin,String supplied) {
        if(!allows(origin)) throw denied();
        Instant now=Instant.now();
        if(now.isAfter(window.plusSeconds(60))) {window=now;attempts=0;}
        if(++attempts>5) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS);
        if(code==null || !now.isBefore(expires) || supplied==null || !supplied.matches("[0-9]{8}")
            || !MessageDigest.isEqual(code.getBytes(StandardCharsets.UTF_8),supplied.getBytes(StandardCharsets.UTF_8))) throw denied();
        if(jdbc.queryForObject("SELECT COUNT(*) FROM assistant_grant",Integer.class)>=20)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"请在本机助手撤销旧授权后重试");
        byte[] bytes=new byte[32];random.nextBytes(bytes);
        String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        jdbc.update("INSERT INTO assistant_grant VALUES (?,?,?)",hash(token),origin,now.toString());
        code=null;expires=Instant.EPOCH;
        return new Grant(token);
    }
    public boolean authorized(String origin,String authorization) {
        if(!allows(origin) || authorization==null || !authorization.matches("Bearer [A-Za-z0-9_-]{43}")) return false;
        return jdbc.queryForObject("SELECT COUNT(*) FROM assistant_grant WHERE token_hash=? AND origin=?",Integer.class,hash(authorization.substring(7)),origin)==1;
    }
    public void revoke(String authorization) { jdbc.update("DELETE FROM assistant_grant WHERE token_hash=?",hash(authorization.substring(7))); }
    public synchronized void revokeAll() {jdbc.update("DELETE FROM assistant_grant");code=null;expires=Instant.EPOCH;}
    private static String hash(String token) {
        try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));}
        catch(NoSuchAlgorithmException ex){throw new IllegalStateException(ex);}
    }
    private ResponseStatusException denied(){return new ResponseStatusException(HttpStatus.FORBIDDEN,"配对未通过，请在本机助手生成新的配对码");}
}
