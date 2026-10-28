package dev.tmdevelopment.flaremessages.service;

import dev.tmdevelopment.flaremessages.FlareMessages;
import dev.tmdevelopment.flaremessages.face.Face;
import dev.tmdevelopment.flaremessages.message.MessageBlock;
import dev.tmdevelopment.flaremessages.message.Template;
import dev.tmdevelopment.flaremessages.util.Center;
import dev.tmdevelopment.flaremessages.util.Text;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import dev.tmdevelopment.flaremessages.libs.kyori.adventure.text.Component;
import dev.tmdevelopment.flaremessages.platform.Compat;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

/**
 * Placeholders + render + delivery of MessageBlocks.
 * A block is rendered ONCE and sent to every target when its content doesn't depend on the target.
 */
public final class MessageService {
    private static final String CENTER_TAG = "<center>";
    private final FlareMessages plugin;
    private String serverName = "Server";
    private DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private DateTimeFormatter timeFormat = DateTimeFormatter.ofPattern("HH:mm");
    private ZoneId zone = ZoneId.systemDefault();

    public MessageService(FlareMessages plugin) {
        this.plugin = plugin;
    }

    public void load() {
        FileConfiguration cfg = this.plugin.getConfig();
        this.serverName = cfg.getString("server-name", "Server");
        try {
            this.dateFormat = DateTimeFormatter.ofPattern(cfg.getString("date-format", "dd.MM.yyyy"));
            this.timeFormat = DateTimeFormatter.ofPattern(cfg.getString("time-format", "HH:mm"));
            String z = cfg.getString("timezone", "");
            this.zone = Text.blank(z) ? ZoneId.systemDefault() : ZoneId.of(z);
        } catch (Exception e) {
            this.plugin.getLogger().warning("Invalid date-format, time-format or timezone in config.yml: " + e.getMessage());
        }
    }

    public String serverName() {
        return this.serverName;
    }

    // ------------------------------------------------------------------ compile / render

    public Template compile(String raw) {
        if (raw == null || raw.isEmpty()) {
            return Template.EMPTY;
        }
        boolean center = false;
        if (raw.regionMatches(true, 0, CENTER_TAG, 0, CENTER_TAG.length())) {
            center = true;
            raw = raw.substring(CENTER_TAG.length());
        }
        boolean dynamic = isDynamic(raw);
        Component cached = null;
        if (!dynamic) {
            cached = Text.parse(raw, null);
            if (center) {
                cached = Center.apply(cached);
            }
        }
        return new Template(raw, center, dynamic, cached);
    }

    public List<Template> compile(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return Collections.emptyList();
        }
        ArrayList<Template> out = new ArrayList<>(lines.size());
        for (String line : lines) {
            out.add(this.compile(line));
        }
        return out;
    }

    private static boolean isDynamic(String raw) {
        for (int i = 0, n = raw.length(); i < n; ++i) {
            char c = raw.charAt(i);
            if (c == '%') {
                return true;
            }
            if (c == '{' && !(i + 1 < n && raw.charAt(i + 1) == '#')) {
                return true;
            }
            if (c == '<' && raw.startsWith("<face_", i)) {
                return true;
            }
        }
        return false;
    }

    public Component render(Template t, Player subject, Face face) {
        if (t.cached() != null) {
            return t.cached();
        }
        Component c = Text.parse(this.placeholders(t.raw(), subject), subject, face != null ? face.resolver() : Face.EMPTY_RESOLVER);
        return t.center() ? Center.apply(c) : c;
    }

    /** Ad-hoc text (commands): placeholders + colors. */
    public Component parse(String raw, Player subject) {
        if (raw == null || raw.isEmpty()) {
            return Component.empty();
        }
        return this.render(this.compile(raw), subject, subject == null ? null : this.plugin.faces().cached(subject.getUniqueId()));
    }

    /** Internal {placeholders}. Unknown ones are left as they are (PlaceholderAPI runs later in Text.parse). */
    public String placeholders(String s, Player p) {
        int open = s.indexOf('{');
        if (open < 0) {
            return s;
        }
        StringBuilder sb = new StringBuilder(s.length() + 32);
        int last = 0;
        while (open >= 0) {
            int close = s.indexOf('}', open + 1);
            if (close < 0) {
                break;
            }
            String value = this.resolve(s.substring(open + 1, close), p);
            if (value != null) {
                sb.append(s, last, open).append(value);
                last = close + 1;
                open = s.indexOf('{', last);
            } else {
                open = s.indexOf('{', open + 1);
            }
        }
        sb.append(s, last, s.length());
        return sb.toString();
    }

    private String resolve(String key, Player p) {
        switch (key) {
            case "online":
                return String.valueOf(Bukkit.getOnlinePlayers().size());
            case "max_online":
            case "max":
                return String.valueOf(Bukkit.getMaxPlayers());
            case "server_name":
            case "server":
                return this.serverName;
            case "join_number":
            case "unique_joins":
                return String.valueOf(this.plugin.data().uniqueJoins());
            case "date":
                return this.dateFormat.format(ZonedDateTime.now(this.zone));
            case "time":
                return this.timeFormat.format(ZonedDateTime.now(this.zone));
            case "prefix":
                return this.plugin.lang().prefix();
            default:
                break;
        }
        if (key.length() == 6 && key.startsWith("face_") && key.charAt(5) >= '1' && key.charAt(5) <= '8') {
            return "<" + key + ">";
        }
        if (p == null) {
            return null;
        }
        switch (key) {
            case "player":
            case "name":
                return p.getName();
            case "displayname":
                return p.getDisplayName();
            case "uuid":
                return p.getUniqueId().toString();
            case "world":
                return p.getWorld().getName();
            case "ping":
                return String.valueOf(Compat.ping(p));
            case "level":
                return String.valueOf(p.getLevel());
            case "health":
                return String.valueOf((int) Math.ceil(p.getHealth()));
            case "gamemode":
                return p.getGameMode().name().toLowerCase(Locale.ROOT);
            case "x":
            case "y":
            case "z": {
                Location l = p.getLocation();
                return String.valueOf(key.equals("x") ? l.getBlockX() : key.equals("y") ? l.getBlockY() : l.getBlockZ());
            }
            default:
                return null;
        }
    }

    // ------------------------------------------------------------------ delivery

    /** A message already turned into components, ready to be shown to one or more players. */
    public static final class Rendered {
        final MessageBlock block;
        /** one chat message per line: Minecraft drops empty lines at the start / end of a single message */
        final List<Component> chat;
        final Component title;
        final Component subtitle;
        final Component actionbar;
        final Component bossbar;

        Rendered(MessageBlock block, List<Component> chat, Component title, Component subtitle, Component actionbar, Component bossbar) {
            this.block = block;
            this.chat = chat;
            this.title = title;
            this.subtitle = subtitle;
            this.actionbar = actionbar;
            this.bossbar = bossbar;
        }
    }

    public Rendered render(MessageBlock b, Player subject, Face face) {
        List<Component> chat = null;
        if (b.hasChat()) {
            List<Template> lines = b.chat();
            ArrayList<Component> out = new ArrayList<>(Math.max(8, lines.size()) + b.clear() + 2);
            for (int i = 0; i < b.clear(); ++i) {
                out.add(Component.empty());
            }
            if (b.usesFace() && face != null) {
                // "" at the start / end of the lines = empty lines above / below the message, outside the face
                int first = 0;
                int last = lines.size();
                while (first < last && lines.get(first).isEmpty()) {
                    ++first;
                }
                while (last > first && lines.get(last - 1).isEmpty()) {
                    --last;
                }
                for (int i = 0; i < first; ++i) {
                    out.add(Component.empty());
                }
                Component gap = Component.text(b.faceGap());
                int total = Math.max(8, last - first);
                for (int i = 0; i < total; ++i) {
                    Component line = first + i < last ? this.render(lines.get(first + i), subject, face) : Component.empty();
                    out.add(i < 8 ? Component.textOfChildren(face.row(i), gap, line) : line);
                }
                for (int i = last; i < lines.size(); ++i) {
                    out.add(Component.empty());
                }
            } else {
                for (Template line : lines) {
                    out.add(this.render(line, subject, face));
                }
            }
            chat = out;
        }
        Component title = null;
        Component subtitle = null;
        if (b.hasTitle()) {
            title = this.render(b.title(), subject, face);
            subtitle = this.render(b.subtitle(), subject, face);
        }
        Component actionbar = b.hasActionbar() ? this.render(b.actionbar(), subject, face) : null;
        Component bossbar = b.hasBossbar() ? this.render(b.bossbar(), subject, face) : null;
        return new Rendered(b, chat, title, subtitle, actionbar, bossbar);
    }

    public void deliver(Rendered r, Player target) {
        MessageBlock b = r.block;
        if (r.chat != null) {
            for (Component line : r.chat) {
                this.plugin.platform().send(target, line);
            }
        }
        if (r.title != null) {
            this.plugin.platform().title(target, r.title, r.subtitle, b.fadeIn(), b.stay(), b.fadeOut());
        }
        if (r.actionbar != null) {
            this.plugin.effects().actionbar(target, r.actionbar, b.actionbarSeconds());
        }
        if (r.bossbar != null) {
            this.plugin.effects().bossbar(target, this.plugin.platform().legacy(r.bossbar), b.bossbarColor(), b.bossbarStyle(),
                    b.bossbarSeconds(), b.bossbarCountdown());
        }
        if (b.hasSound()) {
            this.playSound(target, b.sound(), b.volume(), b.pitch());
        }
    }

    /** Sends a block to one player, who is also the subject of the placeholders. Runs the commands. */
    public void send(MessageBlock b, Player target, Face face) {
        if (b == MessageBlock.EMPTY) {
            return;
        }
        this.deliver(this.render(b, target, face), target);
        this.runCommands(b, target);
    }

    /** subject == null on a dynamic block: every target sees its own placeholders (announcements). */
    public void broadcast(MessageBlock b, Collection<? extends Player> targets, Player subject, Face face) {
        if (b == MessageBlock.EMPTY || targets.isEmpty()) {
            return;
        }
        if (subject != null || !b.dynamic()) {
            Rendered r = this.render(b, subject, face);
            for (Player p : targets) {
                this.deliver(r, p);
            }
            return;
        }
        for (Player p : targets) {
            this.deliver(this.render(b, p, b.usesFace() ? this.plugin.faces().cachedOrSteve(p) : null), p);
        }
    }

    /** Shows a block to a player with their own face, without running its commands. */
    public void preview(MessageBlock b, Player player) {
        java.util.UUID id = player.getUniqueId();
        this.plugin.faces().request(player).whenComplete((face, ex) -> Bukkit.getScheduler().runTask(this.plugin, () -> {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                this.deliver(this.render(b, p, face != null ? face : this.plugin.faces().steve()), p);
            }
        }));
    }

    public void runCommands(MessageBlock b, Player subject) {
        if (!b.hasCommands()) {
            return;
        }
        for (String cmd : b.consoleCommands()) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), strip(this.placeholders(cmd, subject)));
        }
        for (String cmd : b.playerCommands()) {
            subject.performCommand(strip(this.placeholders(cmd, subject)));
        }
    }

    public void playSound(Player player, String key, float volume, float pitch) {
        if (Text.blank(key)) {
            return;
        }
        try {
            player.playSound(player.getLocation(), key.toLowerCase(Locale.ROOT), volume, pitch);
        } catch (Exception ignored) {
        }
    }

    private static String strip(String cmd) {
        return cmd.startsWith("/") ? cmd.substring(1) : cmd;
    }
}
