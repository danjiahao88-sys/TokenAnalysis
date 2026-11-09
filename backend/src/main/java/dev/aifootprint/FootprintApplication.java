package dev.aifootprint;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import java.nio.file.Files;
import java.nio.file.Path;

@SpringBootApplication
@EnableScheduling
public class FootprintApplication {
    public static void main(String[] args) throws Exception {
        if(!DesktopAssistant.prepare(args)) return;
        String database = System.getenv().getOrDefault("AIF_DATABASE", "../.local/footprint.db");
        if(!Boolean.getBoolean("aif.desktop")) {
            Path parent = Path.of(database).toAbsolutePath().getParent();
            if (parent != null) Files.createDirectories(parent);
        }
        SpringApplication.run(FootprintApplication.class, args);
    }
}
