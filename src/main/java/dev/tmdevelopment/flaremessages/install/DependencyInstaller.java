package dev.tmdevelopment.flaremessages.install;

import dev.tmdevelopment.flaremessages.FlareMessages;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;

/** Downloads and enables missing soft-dependencies (config.yml > auto-install), no restart needed. */
public final class DependencyInstaller {
    private static final Pattern MODRINTH_URL = Pattern.compile("\"url\"\\s*:\\s*\"(https://cdn\\.modrinth\\.com/[^\"]+?\\.jar)\"");
    private static final Pattern GITHUB_ASSET = Pattern.compile("\"browser_download_url\"\\s*:\\s*\"([^\"]+?\\.jar)\"");
    private static final Pattern YAML_NAME = Pattern.compile("^name\\s*:\\s*['\"]?([^'\"#\\s]+)", Pattern.MULTILINE);
    private final FlareMessages plugin;

    public DependencyInstaller(FlareMessages plugin) {
        this.plugin = plugin;
    }

    public void run(Consumer<String> onEnabled) {
        ConfigurationSection root = this.plugin.getConfig().getConfigurationSection("auto-install");
        if (root == null || !root.getBoolean("enabled", true)) {
            return;
        }
        ConfigurationSection plugins = root.getConfigurationSection("plugins");
        if (plugins == null) {
            return;
        }
        for (String name : plugins.getKeys(false)) {
            ConfigurationSection section = plugins.getConfigurationSection(name);
            if (section == null || !section.getBoolean("enabled", true) || Bukkit.getPluginManager().getPlugin(name) != null) {
                continue;
            }
            File existing = this.findJar(name);
            if (existing != null) {
                this.plugin.getLogger().warning(name + " jar found (" + existing.getName() + ") but the plugin is not loaded. Check the console for its errors.");
                continue;
            }
            List<String> sources = section.getStringList("sources");
            if (sources.isEmpty()) {
                continue;
            }
            this.plugin.getLogger().info(name + " is missing, downloading it automatically...");
            Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> {
                File file = this.download(name, sources);
                if (file == null) {
                    this.plugin.getLogger().warning("Could not download " + name + ". Install it manually or disable it in config.yml > auto-install.");
                    return;
                }
                Bukkit.getScheduler().runTask(this.plugin, () -> this.load(name, file, onEnabled));
            });
        }
    }

    private File download(String name, List<String> sources) {
        File pluginsDir = this.plugin.getDataFolder().getParentFile();
        for (String source : sources) {
            int colon = source.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String type = source.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            String value = source.substring(colon + 1).trim();
            try {
                String url;
                switch (type) {
                    case "modrinth":
                        url = this.modrinthUrl(value);
                        break;
                    case "hangar":
                        url = this.hangarUrl(value);
                        break;
                    case "github":
                        url = this.githubUrl(value);
                        break;
                    case "url":
                        url = value;
                        break;
                    default:
                        url = null;
                }
                if (url == null) {
                    continue;
                }
                File temp = new File(pluginsDir, name + ".jar.download");
                HttpURLConnection c = this.open(url);
                try {
                    int code = c.getResponseCode();
                    if (code != 200) {
                        this.plugin.getLogger().info(name + ": " + type + " answered " + code + ", trying next source.");
                        continue;
                    }
                    try (InputStream body = c.getInputStream()) {
                        Files.copy(body, temp.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    }
                } finally {
                    c.disconnect();
                }
                String jarName = this.pluginName(temp);
                if (jarName == null || !jarName.equalsIgnoreCase(name)) {
                    Files.deleteIfExists(temp.toPath());
                    this.plugin.getLogger().info(name + ": file from " + type + " is not a valid " + name + " jar, trying next source.");
                    continue;
                }
                File target = new File(pluginsDir, name + ".jar");
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                this.plugin.getLogger().info("Downloaded " + name + " from " + type + ".");
                return target;
            } catch (Exception e) {
                this.plugin.getLogger().info(name + ": " + type + " failed (" + e.getClass().getSimpleName()
                        + (e.getMessage() == null ? "" : ": " + e.getMessage()) + "), trying next source.");
            }
        }
        return null;
    }

    /** GET with redirects followed by hand (also from one site to another, which Java doesn't do alone). */
    private HttpURLConnection open(String url) throws Exception {
        for (int i = 0; i < 6; ++i) {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(15000);
            c.setReadTimeout(60000);
            c.setInstanceFollowRedirects(false);
            c.setRequestProperty("User-Agent", "TMDevelopment/FlareMessages/" + this.plugin.getDescription().getVersion());
            int code = c.getResponseCode();
            if (code >= 300 && code < 400 && c.getHeaderField("Location") != null) {
                url = new URL(new URL(url), c.getHeaderField("Location")).toString();
                c.disconnect();
                continue;
            }
            return c;
        }
        throw new IllegalStateException("too many redirects");
    }

    private String get(String url) throws Exception {
        HttpURLConnection c = this.open(url);
        try {
            if (c.getResponseCode() != 200) {
                return null;
            }
            try (InputStream in = c.getInputStream()) {
                return new String(dev.tmdevelopment.flaremessages.util.Text.readAll(in), StandardCharsets.UTF_8);
            }
        } finally {
            c.disconnect();
        }
    }

    private String modrinthUrl(String slug) throws Exception {
        String loaders = URLEncoder.encode("[\"paper\",\"spigot\",\"bukkit\"]", "UTF-8");
        String json = this.get("https://api.modrinth.com/v2/project/" + slug + "/version?loaders=" + loaders);
        if (json == null) {
            return null;
        }
        Matcher m = MODRINTH_URL.matcher(json);
        return m.find() ? m.group(1) : null;
    }

    private String hangarUrl(String slug) throws Exception {
        String version = this.get("https://hangar.papermc.io/api/v1/projects/" + slug + "/latestrelease");
        if (version == null || version.trim().isEmpty()) {
            return null;
        }
        return "https://hangar.papermc.io/api/v1/projects/" + slug + "/versions/"
                + URLEncoder.encode(version.trim(), "UTF-8").replace("+", "%20") + "/PAPER/download";
    }

    private String githubUrl(String repo) throws Exception {
        String json = this.get("https://api.github.com/repos/" + repo + "/releases/latest");
        if (json == null) {
            return null;
        }
        Matcher m = GITHUB_ASSET.matcher(json);
        return m.find() ? m.group(1) : null;
    }

    private String pluginName(File file) {
        try (ZipFile zip = new ZipFile(file)) {
            ZipEntry entry = zip.getEntry("plugin.yml");
            if (entry == null) {
                entry = zip.getEntry("paper-plugin.yml");
            }
            if (entry == null) {
                return null;
            }
            StringBuilder yml = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(zip.getInputStream(entry), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    yml.append(line).append('\n');
                }
            }
            Matcher m = YAML_NAME.matcher(yml);
            return m.find() ? m.group(1) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private File findJar(String name) {
        File pluginsDir = this.plugin.getDataFolder().getParentFile();
        File[] jars = pluginsDir.listFiles((dir, file) -> file.toLowerCase(Locale.ROOT).endsWith(".jar"));
        if (jars == null) {
            return null;
        }
        ArrayList<File> candidates = new ArrayList<>();
        for (File jar : jars) {
            if (jar.getName().toLowerCase(Locale.ROOT).startsWith(name.toLowerCase(Locale.ROOT))) {
                candidates.add(jar);
            }
        }
        for (File jar : candidates) {
            String jarName = this.pluginName(jar);
            if (jarName != null && jarName.equalsIgnoreCase(name)) {
                return jar;
            }
        }
        return null;
    }

    private void load(String name, File file, Consumer<String> onEnabled) {
        if (Bukkit.getPluginManager().getPlugin(name) != null) {
            return;
        }
        try {
            Plugin loaded = Bukkit.getPluginManager().loadPlugin(file);
            if (loaded == null) {
                throw new IllegalStateException("invalid plugin file");
            }
            Bukkit.getPluginManager().enablePlugin(loaded);
            if (!loaded.isEnabled()) {
                throw new IllegalStateException("plugin did not enable");
            }
            this.plugin.getLogger().info(name + " installed and enabled, no restart needed.");
            onEnabled.accept(name);
        } catch (Throwable t) {
            this.plugin.getLogger().warning(name + " was downloaded but could not be started now (" + t.getMessage() + "). It will be active after the next restart.");
        }
    }
}
