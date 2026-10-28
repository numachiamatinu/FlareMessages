package dev.tmdevelopment.flaremessages.config;

import dev.tmdevelopment.flaremessages.FlareMessages;
import dev.tmdevelopment.flaremessages.util.Headers;
import dev.tmdevelopment.flaremessages.util.Text;
import dev.tmdevelopment.flaremessages.util.Yaml;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import dev.tmdevelopment.flaremessages.libs.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

/** Chat messages from lang/&lt;language&gt;.yml. */
public final class Lang {
    private static final String[] BUNDLED = new String[]{"en"};
    private final FlareMessages plugin;
    private YamlConfiguration cfg = new YamlConfiguration();

    public Lang(FlareMessages plugin) {
        this.plugin = plugin;
    }

    public void load() {
        File folder = new File(this.plugin.getDataFolder(), "lang");
        if (!folder.exists() && !folder.mkdirs()) {
            this.plugin.getLogger().warning("Could not create lang folder");
        }
        for (String id : BUNDLED) {
            if (!new File(folder, id + ".yml").exists()) {
                this.plugin.saveResource("lang/" + id + ".yml", false);
            }
        }
        String language = this.plugin.getConfig().getString("language", "en");
        File file = new File(folder, language + ".yml");
        if (!file.exists()) {
            this.plugin.getLogger().warning("Language file lang/" + language + ".yml not found, using en.yml");
            file = new File(folder, "en.yml");
            language = "en";
        }
        String resource = this.hasResource("lang/" + language + ".yml") ? "lang/" + language + ".yml" : "lang/en.yml";
        Headers.sync(this.plugin, file, resource);
        this.cfg = Yaml.load(this.plugin, file);
        try (InputStream in = this.plugin.getResource(resource)) {
            if (in == null) {
                return;
            }
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
            this.cfg.setDefaults(defaults);
            if (Yaml.broken(this.plugin, file)) {
                return; // broken file: use the defaults, never overwrite the admin's text
            }
            boolean changed = false;
            int current = this.cfg.isSet("lang-version") ? this.cfg.getInt("lang-version") : 1;
            int latest = defaults.getInt("lang-version");
            if (current < latest) {
                File backup = new File(file.getParentFile(), file.getName() + ".v" + current + ".bak");
                Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
                this.cfg.set("help", null);
                this.cfg.set("usage", null);
                this.cfg.set("lang-version", latest);
                changed = true;
                this.plugin.getLogger().info("Messages updated to the new version (old file saved as " + backup.getName() + ").");
            }
            for (String key : defaults.getKeys(true)) {
                if (defaults.isConfigurationSection(key) || this.cfg.isSet(key)) {
                    continue;
                }
                this.cfg.set(key, defaults.get(key));
                changed = true;
            }
            if (changed && Yaml.keepsComments()) {
                this.cfg.save(file);
                this.plugin.getLogger().info("Added new messages to lang/" + file.getName());
            } else if (changed) {
                // before 1.18 saving would delete the comments: the new messages just use their default text
                this.plugin.getLogger().info("New messages of this version use their default text (lang/" + file.getName()
                        + " is not rewritten on this server version, to keep its comments).");
            }
        } catch (Exception e) {
            this.plugin.getLogger().warning("Could not load default language: " + e.getMessage());
        }
    }

    private boolean hasResource(String path) {
        try (InputStream in = this.plugin.getResource(path)) {
            return in != null;
        } catch (Exception e) {
            return false;
        }
    }

    public String prefix() {
        return this.cfg.getString("prefix", "");
    }

    private Object value(String key) {
        return this.cfg.get(key);
    }

    private String pre(String text) {
        return text.replace("%prefix%", this.prefix()).replace("{prefix}", this.prefix());
    }

    public String raw(String key) {
        Object v = this.value(key);
        if (v instanceof List) {
            List<?> list = (List<?>) v;
            return list.isEmpty() ? "" : this.pre(String.valueOf(list.get(0)));
        }
        return v == null ? "<red>Missing text: " + key : this.pre(String.valueOf(v));
    }

    public List<String> lines(String key) {
        ArrayList<String> out = new ArrayList<>();
        Object v = this.value(key);
        if (v instanceof List) {
            for (Object o : (List<?>) v) {
                out.add(this.pre(String.valueOf(o)));
            }
        } else if (v == null) {
            out.add("<red>Missing text: " + key);
        } else if (!String.valueOf(v).isEmpty()) {
            out.add(this.pre(String.valueOf(v)));
        }
        return out;
    }

    public void send(CommandSender sender, String key, String... replacements) {
        Player player = sender instanceof Player ? (Player) sender : null;
        for (String line : this.lines(key)) {
            this.plugin.platform().send(sender, Text.parse(Text.replace(line, replacements), player));
        }
    }

    public void sendRaw(CommandSender sender, List<String> lines, String... replacements) {
        Player player = sender instanceof Player ? (Player) sender : null;
        for (String line : lines) {
            if (line == null || line.isEmpty()) {
                continue;
            }
            this.plugin.platform().send(sender, Text.parse(Text.replace(this.pre(line), replacements), player));
        }
    }

    public Component item(String key, Player player, String... replacements) {
        return Text.item(Text.replace(this.raw(key), replacements), player);
    }

    public List<Component> lore(String key, Player player, String... replacements) {
        return Text.items(Text.replace(this.lines(key), replacements), player);
    }

    public Component title(String key, Player player, String... replacements) {
        return Text.parse(Text.replace(this.raw(key), replacements), player);
    }
}
