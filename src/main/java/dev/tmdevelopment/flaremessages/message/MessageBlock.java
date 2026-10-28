package dev.tmdevelopment.flaremessages.message;

import dev.tmdevelopment.flaremessages.service.MessageService;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.configuration.ConfigurationSection;

/**
 * One message, used everywhere (welcome, first join, announcements):
 * chat (with the skin face), title + subtitle, action bar, boss bar, sound and commands.
 * Same keys as the "display" of the other TMDevelopment plugins. "" or [] = off.
 */
public final class MessageBlock {
    public static final String[] BOSSBAR_COLORS = new String[]{"PINK", "BLUE", "RED", "GREEN", "YELLOW", "PURPLE", "WHITE"};
    public static final String[] BOSSBAR_STYLES = new String[]{"PROGRESS", "NOTCHED_6", "NOTCHED_10", "NOTCHED_12", "NOTCHED_20"};
    public static final MessageBlock EMPTY = new MessageBlock();

    // chat
    private List<Template> chat = Collections.emptyList();
    private boolean face;
    private String faceGap = "  ";
    private int clear;

    // title
    private Template title = Template.EMPTY;
    private Template subtitle = Template.EMPTY;
    private int fadeIn = 10;
    private int stay = 60;
    private int fadeOut = 20;

    // action bar
    private Template actionbar = Template.EMPTY;
    private int actionbarSeconds = 3;

    // boss bar
    private Template bossbar = Template.EMPTY;
    private BarColor bossbarColor = BarColor.PINK;
    private BarStyle bossbarStyle = BarStyle.SOLID;
    private int bossbarSeconds = 5;
    private boolean bossbarCountdown = true;

    // sound
    private String sound = "";
    private float volume = 1.0f;
    private float pitch = 1.0f;

    // commands
    private List<String> consoleCommands = Collections.emptyList();
    private List<String> playerCommands = Collections.emptyList();

    private boolean dynamic;

    private MessageBlock() {
    }

    public static MessageBlock load(ConfigurationSection s, MessageService messages) {
        if (s == null || !s.getBoolean("enabled", true)) {
            return EMPTY;
        }
        MessageBlock b = new MessageBlock();
        ConfigurationSection chat = s.getConfigurationSection("chat");
        if (chat != null) {
            b.chat = messages.compile(chat.getStringList("lines"));
            b.face = chat.getBoolean("face", false);
            b.faceGap = chat.getString("face-gap", "  ");
            b.clear = Math.max(0, Math.min(100, chat.getInt("clear", 0)));
        } else if (s.isList("chat")) {
            b.chat = messages.compile(s.getStringList("chat"));
        }
        ConfigurationSection title = s.getConfigurationSection("title");
        if (title != null) {
            b.title = messages.compile(title.getString("title", ""));
            b.subtitle = messages.compile(title.getString("subtitle", ""));
            b.fadeIn = Math.max(0, title.getInt("fade-in", 10));
            b.stay = Math.max(1, title.getInt("stay", 60));
            b.fadeOut = Math.max(0, title.getInt("fade-out", 20));
        }
        b.actionbar = messages.compile(s.getString("actionbar", ""));
        b.actionbarSeconds = Math.max(0, Math.min(600, s.getInt("actionbar-seconds", 3)));
        ConfigurationSection bossbar = s.getConfigurationSection("bossbar");
        if (bossbar != null) {
            b.bossbar = messages.compile(bossbar.getString("text", ""));
            b.bossbarColor = BarColor.valueOf(valid(bossbar.getString("color", "PINK"), BOSSBAR_COLORS, "PINK"));
            b.bossbarStyle = style(bossbar.getString("style", "PROGRESS"));
            b.bossbarSeconds = Math.max(1, Math.min(600, bossbar.getInt("seconds", 5)));
            b.bossbarCountdown = bossbar.getBoolean("countdown", true);
        }
        b.sound = s.getString("sound", "");
        if (b.sound == null) {
            b.sound = "";
        }
        b.volume = (float) s.getDouble("sound-volume", 1.0);
        b.pitch = (float) s.getDouble("sound-pitch", 1.0);
        ConfigurationSection commands = s.getConfigurationSection("commands");
        if (commands != null) {
            b.consoleCommands = commands.getStringList("console");
            b.playerCommands = commands.getStringList("player");
        }
        b.dynamic = b.face || b.title.dynamic() || b.subtitle.dynamic() || b.actionbar.dynamic() || b.bossbar.dynamic();
        for (Template t : b.chat) {
            b.dynamic |= t.dynamic();
        }
        return b;
    }

    /** PROGRESS / NOTCHED_6 / 10 / 12 / 20 (also the Bukkit names SOLID / SEGMENTED_x) */
    public static BarStyle style(String value) {
        String v = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (v.startsWith("NOTCHED_")) {
            v = "SEGMENTED_" + v.substring(8);
        } else if (v.equals("PROGRESS")) {
            v = "SOLID";
        }
        try {
            return BarStyle.valueOf(v);
        } catch (IllegalArgumentException e) {
            return BarStyle.SOLID;
        }
    }

    public static BarColor color(String value, BarColor def) {
        String v = valid(value, BOSSBAR_COLORS, null);
        return v == null ? def : BarColor.valueOf(v);
    }

    private static String valid(String value, String[] allowed, String def) {
        if (value == null) {
            return def;
        }
        String upper = value.toUpperCase(Locale.ROOT);
        for (String a : allowed) {
            if (a.equals(upper)) {
                return a;
            }
        }
        return def;
    }

    public boolean hasChat() {
        return !this.chat.isEmpty() || this.face;
    }

    public boolean hasTitle() {
        return !this.title.isEmpty() || !this.subtitle.isEmpty();
    }

    public boolean hasActionbar() {
        return !this.actionbar.isEmpty();
    }

    public boolean hasBossbar() {
        return !this.bossbar.isEmpty();
    }

    public boolean hasSound() {
        return !this.sound.trim().isEmpty();
    }

    public boolean hasCommands() {
        return !this.consoleCommands.isEmpty() || !this.playerCommands.isEmpty();
    }

    public boolean any() {
        return this.hasChat() || this.hasTitle() || this.hasActionbar() || this.hasBossbar() || this.hasSound() || this.hasCommands();
    }

    public boolean usesFace() {
        return this.face;
    }

    public boolean dynamic() {
        return this.dynamic;
    }

    public List<Template> chat() {
        return this.chat;
    }

    public String faceGap() {
        return this.faceGap;
    }

    public int clear() {
        return this.clear;
    }

    public Template title() {
        return this.title;
    }

    public Template subtitle() {
        return this.subtitle;
    }

    public int fadeIn() {
        return this.fadeIn;
    }

    public int stay() {
        return this.stay;
    }

    public int fadeOut() {
        return this.fadeOut;
    }

    public Template actionbar() {
        return this.actionbar;
    }

    public int actionbarSeconds() {
        return this.actionbarSeconds;
    }

    public Template bossbar() {
        return this.bossbar;
    }

    public BarColor bossbarColor() {
        return this.bossbarColor;
    }

    public BarStyle bossbarStyle() {
        return this.bossbarStyle;
    }

    public int bossbarSeconds() {
        return this.bossbarSeconds;
    }

    public boolean bossbarCountdown() {
        return this.bossbarCountdown;
    }

    public String sound() {
        return this.sound;
    }

    public float volume() {
        return this.volume;
    }

    public float pitch() {
        return this.pitch;
    }

    public List<String> consoleCommands() {
        return this.consoleCommands;
    }

    public List<String> playerCommands() {
        return this.playerCommands;
    }
}
