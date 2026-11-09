package dev.aifootprint;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class StartupCollection implements ApplicationRunner {
    private static final Logger LOG = LoggerFactory.getLogger(StartupCollection.class);
    private final LiveCollection service;
    public StartupCollection(LiveCollection service) { this.service=service; }
    @Override public void run(ApplicationArguments args) {
        try { service.scan(true); LOG.info("Initial local AI scan: {}",service.status().state()); }
        catch (Exception ex) { LOG.warn("Initial scan unavailable. Previous data retained; use the local sync action to retry."); }
    }
}
