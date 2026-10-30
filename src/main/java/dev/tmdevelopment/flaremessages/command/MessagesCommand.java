package dev.tmdevelopment.flaremessages.command;

import dev.tmdevelopment.flaremessages.FlareMessages;
import dev.tmdevelopment.flaremessages.config.Lang;
import dev.tmdevelopment.flaremessages.face.Face;
import dev.tmdevelopment.flaremessages.message.MessageBlock;
import dev.tmdevelopment.flaremessages.service.AnnouncementService;
import dev.tmdevelopment.flaremessages.update.UpdateChecker;
import dev.tmdevelopment.flaremessages.util.Text;
import dev.tmdevelopment.flaremessages.util.Yaml;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import dev.tmdevelopment.flaremessages.libs.kyori.adventure.text.Component;
import dev.tmdevelopment.flaremessages.libs.kyori.adventure.text.JoinConfiguration;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class MessagesCommand implements TabExecutor {
    private static final String ADMIN = "flaremessages.admin";
    private static final String VERSION = "flaremessages.command.version";
    private static final List<String> BROADCAST_TYPES = Arrays.asList("chat", "actionbar", "title", "bossbar");
    private static final List<String> PREVIEW_TYPES = Arrays.asList("welcome", "firstjoin", "firstjoin-broadcast");
    private final FlareMessages plugin;

    public MessagesCommand(FlareMessages plugin) {
        this.plugin = plugin;
    }

    private Lang lang() {
        return this.plugin.lang();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "help":
            case "?":
                this.help(sender, label);
                break;
            case "about":
            case "version":
            case "ver":
                if (!sender.hasPermission(VERSION)) {
                    return this.noPerm(sender, VERSION);
                }
                if (args.length > 1 && args[1].toLowerCase(Locale.ROOT).startsWith("perm")) {
                    this.permissions(sender);
                } else {
                    this.about(sender, label);
                }
                break;
            case "reload":
            case "list":
            case "announce":
            case "broadcast":
            case "bc":
            case "preview":
            case "face":
                if (!sender.hasPermission(ADMIN)) {
                    return this.noPerm(sender, ADMIN);
                }
                if (sub.equals("reload")) {
                    this.reload(sender);
                } else if (sub.equals("list")) {
                    this.list(sender, label);
                } else if (sub.equals("announce")) {
                    this.announce(sender, label, args);
                } else if (sub.equals("broadcast") || sub.equals("bc")) {
                    this.broadcast(sender, label, args);
                } else if (sub.equals("preview")) {
                    this.preview(sender, label, args);
                } else {
                    this.face(sender, label, args);
                }
                break;
            default:
                this.lang().send(sender, "general.unknown-command", "%label%", label, "%sub%", escape(args[0]));
        }
        return true;
    }

    /** Text typed by players / written in files is shown as it is, never parsed as color tags. */
    private static String escape(String text) {
        return text.replace("\\", "\\\\").replace("<", "\\<");
    }

    /** Same, for text placed inside a quoted tag argument like <hover:show_text:'...'>. */
    private static String escapeArg(String text) {
        return escape(text).replace("'", "\\'");
    }

    private boolean noPerm(CommandSender sender, String permission) {
        this.lang().send(sender, "general.no-permission", "%permission%", permission);
        return true;
    }

    private void help(CommandSender sender, String label) {
        String version = this.plugin.getDescription().getVersion();
        this.lang().send(sender, "help.header", "%label%", label, "%version%", version);
        boolean admin = sender.hasPermission(ADMIN);
        if (admin) {
            this.lang().send(sender, "help.admin", "%label%", label);
        }
        if (sender.hasPermission(VERSION)) {
            this.lang().send(sender, "help.lines.version", "%label%", label);
        }
        this.lang().send(sender, admin ? "help.admin-footer" : "help.footer", "%label%", label);
    }

    // ------------------------------------------------------------------ about

    /** /fmessages about (also version / ver): version (green = latest, red = outdated), server, notifier, hooks. */
    private void about(CommandSender sender, String label) {
        boolean now = this.plugin.updates().get(state -> {
            if (sender instanceof Player && !((Player) sender).isOnline()) {
                return;
            }
            this.aboutAnswer(sender, label, state);
        });
        if (!now) {
            this.lang().send(sender, "about.checking");
        }
    }

    private void aboutAnswer(CommandSender sender, String label, UpdateChecker.State state) {
        UpdateChecker updates = this.plugin.updates();
        updates.send(sender, "about.header");
        switch (state) {
            case OUTDATED:
                updates.send(sender, sender instanceof Player ? "about.outdated" : "about.outdated-console");
                break;
            case LATEST:
                updates.send(sender, "about.latest");
                break;
            case DISABLED:
                updates.send(sender, "about.check-off");
                break;
            default:
                updates.send(sender, "about.failed");
        }
        String separator = this.lang().raw("about.separator");
        StringBuilder hooks = new StringBuilder();
        for (String hook : new String[]{"PlaceholderAPI", "SkinsRestorer"}) {
            if (hooks.length() > 0) {
                hooks.append(separator);
            }
            hooks.append(this.lang().raw(enabled(hook) ? "about.hooked" : "about.missing").replace("{hook}", hook));
        }
        String[] replacements = {
                "%label%", label,
                "%version%", updates.current(),
                "%minecraft%", Bukkit.getBukkitVersion().split("-")[0],
                "%server%", UpdateChecker.serverSoftware(),
                "%java%", UpdateChecker.javaVersion(),
                "%textapi%", this.plugin.platform().name(),
                "%notifier%", this.lang().raw(updates.enabled() ? "about.notifier-on" : "about.notifier-off"),
                "%hooks%", hooks.toString()};
        if (sender instanceof Player) {
            this.lang().send(sender, "about.info", replacements);
            return;
        }
        // the console can't click [Show list]: the permissions are written right away
        List<String> lines = new ArrayList<>();
        for (String line : this.lang().lines("about.info")) {
            if (!line.contains("about permissions")) {
                lines.add(line);
            }
        }
        this.lang().sendRaw(sender, lines, replacements);
        this.permissions(sender);
    }

    private static boolean enabled(String name) {
        org.bukkit.plugin.Plugin p = Bukkit.getPluginManager().getPlugin(name);
        return p != null && p.isEnabled();
    }

    /** Every permission of plugin.yml with its description and who has it by default. */
    private void permissions(CommandSender sender) {
        this.lang().send(sender, "about.permissions.header");
        for (org.bukkit.permissions.Permission perm : this.plugin.getDescription().getPermissions()) {
            String def;
            switch (perm.getDefault()) {
                case TRUE:
                    def = this.lang().raw("about.permissions.everyone");
                    break;
                case OP:
                    def = this.lang().raw("about.permissions.op");
                    break;
                case NOT_OP:
                    def = this.lang().raw("about.permissions.not-op");
                    break;
                default:
                    def = this.lang().raw("about.permissions.nobody");
            }
            String desc = perm.getDescription() == null || perm.getDescription().isEmpty() ? "-" : perm.getDescription();
            this.lang().send(sender, "about.permissions.line", "%permission%", perm.getName(),
                    "%description%", escape(desc), "%default%", def);
        }
    }

    // ------------------------------------------------------------------ reload

    private void reload(CommandSender sender) {
        long start = System.currentTimeMillis();
        this.plugin.reloadAll();
        String ms = String.valueOf(System.currentTimeMillis() - start);
        List<Yaml.Problem> problems = Yaml.problems();
        if (problems.isEmpty()) {
            AnnouncementService ann = this.plugin.announcements();
            this.lang().send(sender, "admin.reloaded",
                    "%ms%", ms,
                    "%announcements%", String.valueOf(ann.size()),
                    "%rotation%", String.valueOf(ann.rotationSize()),
                    "%interval%", String.valueOf(ann.interval()),
                    "%formats%", String.valueOf(this.plugin.welcome().formatCount()),
                    "%papi%", this.lang().raw(Text.papi() ? "admin.papi-on" : "admin.papi-off"));
            return;
        }
        this.lang().send(sender, "admin.reloaded-errors", "%ms%", ms, "%count%", String.valueOf(problems.size()));
        for (Yaml.Problem p : problems) {
            this.lang().send(sender, "admin.reload-error", "%file%", p.file(), "%line%", p.line(),
                    "%problem%", this.lang().raw("admin.reload-problem." + p.kind()), "%details%", escapeArg(p.message()));
        }
        this.lang().send(sender, "admin.reload-error-hint");
    }

    // ------------------------------------------------------------------ list

    private void list(CommandSender sender, String label) {
        AnnouncementService ann = this.plugin.announcements();
        String automatic;
        if (!this.plugin.getConfig().getBoolean("modules.announcements", true)) {
            automatic = this.lang().raw("admin.list.automatic-off");
        } else if (!ann.hasRotation()) {
            automatic = this.lang().raw("admin.list.automatic-empty");
        } else {
            automatic = Text.replace(this.lang().raw("admin.list.automatic-on"),
                    "%interval%", String.valueOf(ann.interval()),
                    "%order%", this.lang().raw(ann.random() ? "admin.list.order-random" : "admin.list.order-sequential"),
                    "%min%", String.valueOf(ann.minPlayers()));
        }
        this.lang().send(sender, "admin.list.header", "%count%", String.valueOf(ann.size()), "%automatic%", automatic);
        if (ann.size() == 0) {
            this.lang().send(sender, "admin.list.empty");
        }
        for (AnnouncementService.Announcement a : ann.all()) {
            this.lang().send(sender, "admin.list.line", "%label%", label, "%id%", a.id(),
                    "%parts%", this.parts(a.block()),
                    "%mode%", this.lang().raw(a.inRotation() ? "admin.list.mode-auto" : "admin.list.mode-manual"));
        }
        this.lang().send(sender, "admin.list.footer", "%label%", label);
    }

    private String parts(MessageBlock b) {
        ArrayList<String> parts = new ArrayList<>();
        if (b.hasChat()) {
            parts.add(this.lang().raw("admin.list.parts.chat"));
        }
        if (b.hasTitle()) {
            parts.add(this.lang().raw("admin.list.parts.title"));
        }
        if (b.hasActionbar()) {
            parts.add(this.lang().raw("admin.list.parts.actionbar"));
        }
        if (b.hasBossbar()) {
            parts.add(this.lang().raw("admin.list.parts.bossbar"));
        }
        if (b.hasSound()) {
            parts.add(this.lang().raw("admin.list.parts.sound"));
        }
        if (b.hasCommands()) {
            parts.add(this.lang().raw("admin.list.parts.commands"));
        }
        return String.join(this.lang().raw("admin.list.parts.separator"), parts);
    }

    private String ids() {
        List<String> ids = this.plugin.announcements().ids();
        return ids.isEmpty() ? "-" : String.join(", ", ids);
    }

    // ------------------------------------------------------------------ announce

    private void announce(CommandSender sender, String label, String[] args) {
        AnnouncementService ann = this.plugin.announcements();
        if (args.length < 2) {
            this.lang().send(sender, "usage.announce", "%label%", label);
            return;
        }
        AnnouncementService.Announcement a;
        if (args[1].equalsIgnoreCase("next")) {
            if (!ann.hasRotation()) {
                this.lang().send(sender, "admin.no-rotation", "%label%", label);
                return;
            }
            a = ann.next();
        } else {
            a = ann.get(args[1]);
            if (a == null) {
                this.lang().send(sender, "admin.unknown-announcement", "%id%", escape(args[1]), "%list%", this.ids());
                return;
            }
        }
        AnnouncementService.Result r = ann.broadcast(a);
        this.lang().send(sender, "admin.announced", "%id%", a.id(), "%sent%", String.valueOf(r.sent()));
        if (r.filtered() > 0) {
            this.lang().send(sender, "admin.announced-details", "%filtered%", String.valueOf(r.filtered()));
        }
    }

    // ------------------------------------------------------------------ broadcast

    /** /fmessages broadcast <chat|actionbar|title|bossbar> <text>   (title: "title | subtitle") */
    private void broadcast(CommandSender sender, String label, String[] args) {
        if (args.length < 3 || !BROADCAST_TYPES.contains(args[1].toLowerCase(Locale.ROOT))) {
            this.lang().send(sender, "usage.broadcast", "%label%", label);
            return;
        }
        Collection<? extends Player> online = Bukkit.getOnlinePlayers();
        if (online.isEmpty()) {
            this.lang().send(sender, "admin.nobody-online");
            return;
        }
        String type = args[1].toLowerCase(Locale.ROOT);
        String raw = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        FileConfiguration cfg = this.plugin.getConfig();
        int actionbarSeconds = Math.max(1, cfg.getInt("quick-broadcast.actionbar-seconds", 5));
        int fadeIn = Math.max(0, cfg.getInt("quick-broadcast.title.fade-in", 10));
        int stay = Math.max(1, cfg.getInt("quick-broadcast.title.stay", 60));
        int fadeOut = Math.max(0, cfg.getInt("quick-broadcast.title.fade-out", 20));
        BarColor color = MessageBlock.color(cfg.getString("quick-broadcast.bossbar.color"), BarColor.BLUE);
        BarStyle style = MessageBlock.style(cfg.getString("quick-broadcast.bossbar.style"));
        int bossbarSeconds = Math.max(1, cfg.getInt("quick-broadcast.bossbar.seconds", 10));
        String[] titleParts = raw.split("\\|", 2);
        for (Player p : online) {
            if (type.equals("chat")) {
                this.plugin.platform().send(p, this.plugin.messages().parse(raw, p));
            } else if (type.equals("actionbar")) {
                this.plugin.effects().actionbar(p, this.plugin.messages().parse(raw, p), actionbarSeconds);
            } else if (type.equals("title")) {
                this.plugin.platform().title(p, this.plugin.messages().parse(titleParts[0].trim(), p),
                        titleParts.length > 1 ? this.plugin.messages().parse(titleParts[1].trim(), p) : Component.empty(),
                        fadeIn, stay, fadeOut);
            } else {
                this.plugin.effects().bossbar(p, "broadcast:" + raw, this.plugin.platform().legacy(this.plugin.messages().parse(raw, p)),
                        color, style, bossbarSeconds, true);
            }
        }
        this.lang().send(sender, "admin.broadcast-sent", "%type%", type, "%count%", String.valueOf(online.size()));
    }

    // ------------------------------------------------------------------ preview

    /** /fmessages preview <welcome|firstjoin|firstjoin-broadcast|announcement> [player] */
    private void preview(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            this.lang().send(sender, "usage.preview", "%label%", label, "%list%", this.ids());
            return;
        }
        Player target = this.target(sender, label, "preview " + args[1], args, 2);
        if (target == null) {
            return;
        }
        String id = args[1].toLowerCase(Locale.ROOT);
        MessageBlock block;
        if (id.equals("welcome")) {
            block = this.plugin.welcome().welcome();
        } else if (id.equals("firstjoin")) {
            block = this.plugin.welcome().firstJoinPlayer();
        } else if (id.equals("firstjoin-broadcast")) {
            block = this.plugin.welcome().firstJoinBroadcast();
        } else {
            AnnouncementService.Announcement a = this.plugin.announcements().get(id);
            if (a == null) {
                this.lang().send(sender, "admin.unknown-message", "%id%", escape(args[1]), "%list%", this.ids());
                return;
            }
            block = a.block();
        }
        if (block == MessageBlock.EMPTY || !block.any()) {
            this.lang().send(sender, "admin.message-off", "%id%", args[1]);
            return;
        }
        this.plugin.messages().preview(block, target);
        this.lang().send(sender, "admin.preview-sent", "%id%", args[1], "%player%", target.getName());
    }

    // ------------------------------------------------------------------ face

    private void face(CommandSender sender, String label, String[] args) {
        Player target = this.target(sender, label, "face", args, 1);
        if (target == null) {
            return;
        }
        String name = target.getName();
        this.plugin.faces().request(target).whenComplete((face, ex) -> Bukkit.getScheduler().runTask(this.plugin, () -> {
            Face f = face != null ? face : this.plugin.faces().steve();
            this.plugin.platform().send(sender, Component.join(JoinConfiguration.newlines(), f.rows()));
            this.lang().send(sender, "admin.face-shown", "%player%", name, "%source%", this.source(f));
        }));
    }

    private String source(Face f) {
        String src = f.source();
        if (src == null || src.equals("steve")) {
            return this.lang().raw("admin.face-source.steve");
        }
        if (src.contains("textures.minecraft.net")) {
            return this.lang().raw("admin.face-source.profile");
        }
        String host;
        try {
            host = URI.create(src).getHost();
        } catch (Exception e) {
            host = src;
        }
        return Text.replace(this.lang().raw("admin.face-source.fallback"), "%host%", host == null ? src : host);
    }

    private Player target(CommandSender sender, String label, String usage, String[] args, int index) {
        if (args.length > index) {
            Player p = Bukkit.getPlayerExact(args[index]);
            if (p == null) {
                this.lang().send(sender, "general.player-not-found", "%player%", escape(args[index]));
            }
            return p;
        }
        if (sender instanceof Player) {
            return (Player) sender;
        }
        this.lang().send(sender, "general.console-needs-player", "%label%", label, "%usage%", usage);
        return null;
    }

    // ------------------------------------------------------------------ tab

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        ArrayList<String> out = new ArrayList<>();
        boolean admin = sender.hasPermission(ADMIN);
        if (args.length == 1) {
            ArrayList<String> options = new ArrayList<>(Arrays.asList("help"));
            if (sender.hasPermission(VERSION)) {
                options.add("about");
                options.add("version");
            }
            if (admin) {
                options.addAll(Arrays.asList("announce", "broadcast", "preview", "face", "list", "reload"));
            }
            this.filter(options, args[0], out);
        } else if (args.length == 2 && sender.hasPermission(VERSION)
                && Arrays.asList("about", "version", "ver").contains(args[0].toLowerCase(Locale.ROOT))) {
            this.filter(Arrays.asList("permissions"), args[1], out);
        } else if (admin && args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("announce")) {
                ArrayList<String> ids = new ArrayList<>(this.plugin.announcements().ids());
                ids.add(0, "next");
                this.filter(ids, args[1], out);
            } else if (sub.equals("broadcast") || sub.equals("bc")) {
                this.filter(BROADCAST_TYPES, args[1], out);
            } else if (sub.equals("preview")) {
                ArrayList<String> ids = new ArrayList<>(PREVIEW_TYPES);
                ids.addAll(this.plugin.announcements().ids());
                this.filter(ids, args[1], out);
            } else if (sub.equals("face")) {
                this.filter(this.onlineNames(), args[1], out);
            }
        } else if (admin && args.length == 3 && args[0].equalsIgnoreCase("preview")) {
            this.filter(this.onlineNames(), args[2], out);
        }
        return out;
    }

    private List<String> onlineNames() {
        ArrayList<String> names = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            names.add(p.getName());
        }
        return names;
    }

    private void filter(List<String> options, String start, List<String> out) {
        String s = start.toLowerCase(Locale.ROOT);
        for (String o : options) {
            if (o.toLowerCase(Locale.ROOT).startsWith(s)) {
                out.add(o);
            }
        }
    }
}
