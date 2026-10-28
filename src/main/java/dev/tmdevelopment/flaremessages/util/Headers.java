package dev.tmdevelopment.flaremessages.util;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.bukkit.plugin.Plugin;

/** Keeps the explanation header (top comments) of the files up to date with the jar, without touching values. */
public final class Headers {
    private Headers() {
    }

    public static String of(Plugin plugin, String resource) {
        try (InputStream in = plugin.getResource(resource)) {
            if (in == null) {
                return "";
            }
            return split(new String(Text.readAll(in), StandardCharsets.UTF_8))[0];
        } catch (Exception e) {
            return "";
        }
    }

    public static void sync(Plugin plugin, File file, String resource) {
        if (!file.exists()) {
            return;
        }
        String header = of(plugin, resource);
        if (header.isEmpty()) {
            return;
        }
        try {
            String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            String[] parts = split(content);
            if (parts[0].equals(header)) {
                return;
            }
            Files.write(file.toPath(), (header + parts[1]).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            plugin.getLogger().warning("Could not update the explanations of " + file.getName() + ": " + e.getMessage());
        }
    }

    private static String[] split(String content) {
        String[] lines = content.replace("\r\n", "\n").split("\n", -1);
        int body = 0;
        while (body < lines.length && (lines[body].startsWith("#") || lines[body].trim().isEmpty())) {
            ++body;
        }
        int last = body - 1;
        while (last >= 0 && lines[last].trim().isEmpty()) {
            --last;
        }
        StringBuilder head = new StringBuilder();
        for (int i = 0; i <= last; ++i) {
            head.append(lines[i]).append('\n');
        }
        if (head.length() > 0) {
            head.append('\n');
        }
        StringBuilder rest = new StringBuilder();
        for (int i = body; i < lines.length; ++i) {
            rest.append(lines[i]);
            if (i < lines.length - 1) {
                rest.append('\n');
            }
        }
        return new String[]{head.toString(), rest.toString()};
    }
}
