package dev.tmdevelopment.flaremessages.util;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.configuration.file.FileConfigurationOptions;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.yaml.snakeyaml.error.MarkedYAMLException;

/**
 * Loads a YAML file and remembers WHERE it is broken (file + line + problem),
 * so /fmessages reload can tell the admin exactly what to fix instead of failing silently.
 */
public final class Yaml {
    private static final Pattern POSITION = Pattern.compile("line (\\d+), column (\\d+)");
    private static final List<Problem> PROBLEMS = new ArrayList<>();
    private static Boolean keepsComments;

    private Yaml() {
    }

    /** kind = tab / indent / quote / other: used to show a clear explanation instead of the raw SnakeYAML text */
    public static final class Problem {
        private final String file;
        private final String line;
        private final String message;
        private final String kind;

        Problem(String file, String line, String message, String kind) {
            this.file = file;
            this.line = line;
            this.message = message;
            this.kind = kind;
        }

        public String file() {
            return this.file;
        }

        public String line() {
            return this.line;
        }

        public String message() {
            return this.message;
        }

        public String kind() {
            return this.kind;
        }
    }

    private static String kind(String message) {
        String m = message.toLowerCase(Locale.ROOT);
        if (m.contains("tab")) {
            return "tab";
        }
        if (m.contains("quoted scalar") || m.contains("end of stream") || m.contains("found '<scalar>'")) {
            return "quote";
        }
        if (m.contains("block end") || m.contains("mapping values") || m.contains("expected ':'")
                || m.contains("block mapping") || m.contains("block collection") || m.contains("indentation")) {
            return "indent";
        }
        return "other";
    }

    public static synchronized void clear() {
        PROBLEMS.clear();
    }

    public static synchronized List<Problem> problems() {
        return new ArrayList<>(PROBLEMS);
    }

    /**
     * Before 1.18 Bukkit deletes every comment when it saves a YAML file.
     * On those servers the plugin never rewrites the admin's files (new options just use their default value).
     */
    public static boolean keepsComments() {
        if (keepsComments == null) {
            boolean ok;
            try {
                FileConfigurationOptions.class.getMethod("parseComments");
                ok = true;
            } catch (NoSuchMethodException e) {
                ok = false;
            }
            keepsComments = ok;
        }
        return keepsComments;
    }

    /** @return the loaded file, or an empty configuration (defaults are used) when it has an error */
    public static YamlConfiguration load(Plugin plugin, File file) {
        YamlConfiguration yml = new YamlConfiguration();
        if (!file.exists()) {
            return yml;
        }
        try {
            yml.load(file);
        } catch (Exception e) {
            String line = "?";
            String message = null;
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof MarkedYAMLException && ((MarkedYAMLException) cause).getProblemMark() != null) {
                // exact data from SnakeYAML: problem + line (1-based)
                MarkedYAMLException marked = (MarkedYAMLException) cause;
                line = String.valueOf(marked.getProblemMark().getLine() + 1);
                message = marked.getProblem() != null ? marked.getProblem() : marked.getContext();
            } else {
                String raw = cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
                Matcher m = POSITION.matcher(raw);
                while (m.find()) {
                    line = m.group(1);
                }
                for (String s : raw.split("\n")) {
                    s = s.trim();
                    if (!s.isEmpty() && !s.startsWith("in '") && !s.startsWith("^") && !POSITION.matcher(s).find()) {
                        message = s;
                        break;
                    }
                }
                if (message == null) {
                    message = raw;
                }
            }
            message = message == null ? "invalid YAML" : message.trim();
            String name = relative(plugin, file);
            synchronized (Yaml.class) {
                PROBLEMS.add(new Problem(name, line, message, kind(message)));
            }
            plugin.getLogger().severe(name + " has an error at line " + line + ": " + message
                    + " -> default values are used until it is fixed.");
        }
        return yml;
    }

    /** true when this file had an error on the last load: never save over it, the admin would lose his text. */
    public static synchronized boolean broken(Plugin plugin, File file) {
        String name = relative(plugin, file);
        for (Problem p : PROBLEMS) {
            if (p.file().equals(name)) {
                return true;
            }
        }
        return false;
    }

    private static String relative(Plugin plugin, File file) {
        String base = plugin.getDataFolder().getAbsolutePath();
        String path = file.getAbsolutePath();
        return path.startsWith(base) ? path.substring(base.length() + 1).replace('\\', '/') : file.getName();
    }
}
