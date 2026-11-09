package dev.aifootprint;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

/** Polls metadata, then reuses the existing accounting collector only when needed. */
@Service
public class LiveCollection {
    private final SnapshotService service;
    private final List<Path> roots;
    private final boolean enabled;
    private final ReentrantLock lock=new ReentrantLock();
    private String fingerprint="";
    private Instant lastFull=Instant.EPOCH;
    public record Status(boolean enabled, boolean collecting, String state, String lastAttemptAt, String lastSuccessAt, int pollSeconds) {}
    private volatile Status status;
    public LiveCollection(SnapshotService service,@Value("${footprint.codex-home}") String codex,
        @Value("${footprint.claude-home}") String claude,@Value("${footprint.live.enabled:true}") boolean enabled) {
        this.service=service;this.enabled=enabled;
        roots=List.of(Path.of(codex).resolve("sessions"),Path.of(codex).resolve("archived_sessions"),Path.of(codex).resolve("skills"),Path.of(claude).resolve("projects"));
        status=new Status(enabled,false,"waiting",null,null,5);
    }
    public Status status(){return status;}
    @Scheduled(fixedDelayString="${footprint.live.delay-ms:5000}",initialDelay=5000)
    public void scheduled(){if(enabled) scan(false);}
    public void scan(boolean force) {
        if(!lock.tryLock()) return;
        try {
            String next=fingerprint();
            if(!force && next.equals(fingerprint) && Instant.now().isBefore(lastFull.plusSeconds(60))) return;
            String at=Instant.now().toString();
            status=new Status(enabled,true,"collecting",at,status.lastSuccessAt(),5);
            service.collect();
            fingerprint=next;lastFull=Instant.now();
            status=new Status(enabled,false,"ready",at,lastFull.toString(),5);
        } catch(Exception ex) {
            status=new Status(enabled,false,"error",Instant.now().toString(),status.lastSuccessAt(),5);
        } finally {lock.unlock();}
    }
    private String fingerprint() throws Exception {
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        for(Path root:roots) {
            digest.update(root.toString().getBytes(StandardCharsets.UTF_8));
            if(Files.notExists(root)) continue;
            try(var files=Files.walk(root)) {
                for(Path p:files.filter(p->p.toString().endsWith(".jsonl") || p.getFileName().toString().equals("SKILL.md")).sorted().toList()) {
                    var attr=Files.readAttributes(p,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
                    if(!attr.isRegularFile()) continue;
                    digest.update((p+":"+attr.size()+":"+attr.lastModifiedTime()).getBytes(StandardCharsets.UTF_8));
                }
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
