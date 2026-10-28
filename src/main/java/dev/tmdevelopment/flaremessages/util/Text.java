package dev.tmdevelopment.flaremessages.util;

import dev.tmdevelopment.flaremessages.hook.PapiHook;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import dev.tmdevelopment.flaremessages.libs.kyori.adventure.text.Component;
import dev.tmdevelopment.flaremessages.libs.kyori.adventure.text.format.NamedTextColor;
import dev.tmdevelopment.flaremessages.libs.kyori.adventure.text.format.TextDecoration;
import dev.tmdevelopment.flaremessages.libs.kyori.adventure.text.minimessage.MiniMessage;
import dev.tmdevelopment.flaremessages.libs.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.entity.Player;

/**
 * Same API as the TMDevelopment base (parse / item / items / replace / toMini / formatAmount / raw).
 * Accepted colors, freely mixed in one line:
 * &a &l &r (and §), &#RRGGBB, {#RRGGBB}, &x&R&R&G&G&B&B, and full MiniMessage.
 * Like real legacy codes, a new color resets bold / italic / underline.
 */
public final class Text {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final String[] LEGACY = new String[128];
    private static boolean papi = false;

    private Text() {
    }

    public static void setPapi(boolean enabled) {
        papi = enabled;
    }

    public static boolean papi() {
        return papi;
    }

    /** Java 8 has no String.repeat. */
    public static String repeat(String s, int times) {
        StringBuilder sb = new StringBuilder(s.length() * Math.max(0, times));
        for (int i = 0; i < times; ++i) {
            sb.append(s);
        }
        return sb.toString();
    }

    /** Java 8 has no InputStream.readAllBytes. */
    public static byte[] readAll(java.io.InputStream in) throws java.io.IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) {
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    /** true for null, "" and spaces only (Java 8 has no String.isBlank). */
    public static boolean blank(String s) {
        return s == null || s.trim().isEmpty();
    }

    public static MiniMessage mini() {
        return MM;
    }

    public static String legacyToMini(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        if (!needsWork(input)) {
            return input;
        }
        int len = input.length();
        StringBuilder out = new StringBuilder(len + 16);
        boolean decorated = false;
        for (int i = 0; i < len; ++i) {
            char c = input.charAt(i);
            if ((c == '&' || c == '§') && i + 1 < len) {
                char code = Character.toLowerCase(input.charAt(i + 1));
                if (code == '#' && i + 8 <= len && isHex(input, i + 2, 6)) {
                    decorated = color(out, decorated, "<#" + input.substring(i + 2, i + 8) + ">");
                    i += 7;
                    continue;
                }
                if (code == 'x' && i + 14 <= len) {
                    String hex = bungeeHex(input, i);
                    if (hex != null) {
                        decorated = color(out, decorated, "<#" + hex + ">");
                        i += 13;
                        continue;
                    }
                }
                if (code < 128 && LEGACY[code] != null) {
                    if (code == 'r') {
                        out.append("<reset>");
                        decorated = false;
                    } else if (code >= 'k' && code <= 'o') {
                        out.append(LEGACY[code]);
                        decorated = true;
                    } else {
                        decorated = color(out, decorated, LEGACY[code]);
                    }
                    ++i;
                    continue;
                }
            }
            if (c == '{' && i + 9 <= len && input.charAt(i + 1) == '#' && input.charAt(i + 8) == '}' && isHex(input, i + 2, 6)) {
                decorated = color(out, decorated, "<#" + input.substring(i + 2, i + 8) + ">");
                i += 8;
                continue;
            }
            out.append(c);
        }
        return out.toString();
    }

    public static Component parse(String text, Player player) {
        return parse(text, player, TagResolver.empty());
    }

    public static Component parse(String text, Player player, TagResolver resolver) {
        if (text == null) {
            return Component.empty();
        }
        String s = text;
        if (papi && player != null && s.indexOf('%') >= 0) {
            s = PapiHook.apply(player, s);
        }
        try {
            return MM.deserialize(legacyToMini(s), resolver);
        } catch (Exception e) {
            return Component.text(s);
        }
    }

    public static Component item(String text, Player player) {
        return parse(text, player).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public static List<Component> items(List<String> lines, Player player) {
        ArrayList<Component> out = new ArrayList<>(lines.size());
        for (String line : lines) {
            out.add(item(line, player));
        }
        return out;
    }

    public static String replace(String text, String... pairs) {
        if (text == null) {
            return "";
        }
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            String key = pairs[i];
            String value = pairs[i + 1] == null ? "" : pairs[i + 1];
            text = text.replace(key, value);
            if (key.length() > 2 && key.charAt(0) == '%' && key.charAt(key.length() - 1) == '%') {
                text = text.replace("{" + key.substring(1, key.length() - 1) + "}", value);
            }
        }
        return text;
    }

    public static List<String> replace(List<String> lines, String... pairs) {
        ArrayList<String> out = new ArrayList<>(lines.size());
        for (String line : lines) {
            out.add(replace(line, pairs));
        }
        return out;
    }

    public static String toMini(Component component) {
        if (component == null) {
            return "";
        }
        String s = MM.serialize(component);
        return s.replace("<!italic>", "").replace("<italic:false>", "").replace("</!italic>", "");
    }

    public static String formatAmount(double d) {
        if (d == Math.rint(d) && Math.abs(d) < 1.0E15) {
            return String.format(Locale.US, "%,d", (long) d);
        }
        return String.format(Locale.US, "%,.2f", d);
    }

    public static List<Component> raw(String text, int width) {
        ArrayList<Component> out = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            out.add(Component.text("-", NamedTextColor.DARK_GRAY).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
            return out;
        }
        for (int i = 0; i < text.length(); i += width) {
            String part = text.substring(i, Math.min(text.length(), i + width));
            out.add(Component.text(part, NamedTextColor.GRAY).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
        }
        return out;
    }

    private static boolean needsWork(String s) {
        for (int i = 0, n = s.length(); i < n; ++i) {
            char c = s.charAt(i);
            if (c == '&' || c == '§' || c == '{') {
                return true;
            }
        }
        return false;
    }

    private static boolean color(StringBuilder out, boolean decorated, String tag) {
        if (decorated) {
            out.append("<reset>");
        }
        out.append(tag);
        return false;
    }

    private static String bungeeHex(String s, int start) {
        StringBuilder hex = new StringBuilder(6);
        for (int k = 0; k < 6; ++k) {
            int p = start + 2 + k * 2;
            char amp = s.charAt(p);
            char h = s.charAt(p + 1);
            if (amp != '&' && amp != '§' || Character.digit(h, 16) < 0) {
                return null;
            }
            hex.append(h);
        }
        return hex.toString();
    }

    private static boolean isHex(String s, int from, int count) {
        for (int i = from; i < from + count; ++i) {
            if (Character.digit(s.charAt(i), 16) < 0) {
                return false;
            }
        }
        return true;
    }

    static {
        LEGACY['0'] = "<black>";
        LEGACY['1'] = "<dark_blue>";
        LEGACY['2'] = "<dark_green>";
        LEGACY['3'] = "<dark_aqua>";
        LEGACY['4'] = "<dark_red>";
        LEGACY['5'] = "<dark_purple>";
        LEGACY['6'] = "<gold>";
        LEGACY['7'] = "<gray>";
        LEGACY['8'] = "<dark_gray>";
        LEGACY['9'] = "<blue>";
        LEGACY['a'] = "<green>";
        LEGACY['b'] = "<aqua>";
        LEGACY['c'] = "<red>";
        LEGACY['d'] = "<light_purple>";
        LEGACY['e'] = "<yellow>";
        LEGACY['f'] = "<white>";
        LEGACY['k'] = "<obfuscated>";
        LEGACY['l'] = "<bold>";
        LEGACY['m'] = "<strikethrough>";
        LEGACY['n'] = "<underlined>";
        LEGACY['o'] = "<italic>";
        LEGACY['r'] = "<reset>";
    }
}
