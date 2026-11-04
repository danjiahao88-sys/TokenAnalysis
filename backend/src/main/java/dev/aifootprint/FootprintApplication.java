package dev.aifootprint;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import java.nio.file.Files;
import java.nio.file.Path;

@SpringBootApplication
public class FootprintApplication {
    public static void main(String[] args) throws Exception {
        String database = System.getenv().getOrDefault("AIF_DATABASE", "../.local/footprint.db");
        Path parent = Path.of(database).toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        SpringApplication.run(FootprintApplication.class, args);
    }
}
