package dev.aifootprint;

import org.springframework.stereotype.Component;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

@Component
public class SkillInventory {
    public record Skill(String id, String name, String description, String category, String source, String status) {}
    public List<Skill> scan(Path home) throws IOException {
        // Inventory of user/system skills only. Plugin caches can contain many historical versions.
        Path root = home.resolve("skills");
        if (!Files.isDirectory(root)) return List.of();
        List<Skill> result = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(root, 4)) {
            for (Path file : paths.filter(p -> Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS) && p.getFileName().toString().equals("SKILL.md")).sorted().toList()) {
                String name = file.getParent().getFileName().toString(), description = "本机已安装的 Skill；尚无可核验的调用次数。";
                // Read only a small frontmatter window, not the full instructions.
                try (var reader = Files.newBufferedReader(file)) {
                    for (int i = 0; i < 24; i++) {
                        String line = reader.readLine(); if (line == null) break;
                        if (line.startsWith("name:")) name = clean(line.substring(5));
                        if (line.startsWith("description:")) {
                            String text = clean(line.substring(12));
                            if (!text.isBlank() && !List.of(">", "|-", ">-", "|").contains(text)) description = text;
                        }
                    }
                }
                result.add(new Skill(CodexCollector.hash(root.relativize(file).toString()), name, description,
                        category(name), file.toString().contains(".system") ? "system" : "user", "installed"));
            }
        }
        return result;
    }
    private String clean(String text) { return CodexCollector.safeLabel(text.trim().replaceAll("^[\"']|[\"']$", "")); }
    private String category(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.matches(".*(design|ui|image|visual).*")) return "设计与创作";
        if (lower.matches(".*(doc|pdf|sheet|present).*")) return "文档与数据";
        if (lower.matches(".*(search|find|research).*")) return "检索与研究";
        return "开发与工作流";
    }
}
