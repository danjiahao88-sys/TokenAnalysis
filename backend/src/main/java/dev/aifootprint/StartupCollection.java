package dev.aifootprint;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class StartupCollection implements ApplicationRunner {
    private static final Logger LOG = LoggerFactory.getLogger(StartupCollection.class);
    private final SnapshotService service;
    public StartupCollection(SnapshotService service) { this.service=service; }
    @Override public void run(ApplicationArguments args) {
        try { var c=service.collect(); LOG.info("Codex scan completed: {} files, {} malformed lines",c.files(),c.malformedLines()); }
        catch (Exception ex) { LOG.warn("Initial scan unavailable. Previous data retained; use the local sync action to retry."); }
    }
}
