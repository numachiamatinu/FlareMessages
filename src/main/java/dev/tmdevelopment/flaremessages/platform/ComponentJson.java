package dev.tmdevelopment.flaremessages.platform;

import dev.tmdevelopment.flaremessages.libs.kyori.adventure.text.Component;
import dev.tmdevelopment.flaremessages.libs.kyori.adventure.text.KeybindComponent;
import dev.tmdevelopment.flaremessages.libs.kyori.adventure.text.TextComponent;
import dev.tmdevelopment.flaremessages.libs.kyori.adventure.text.TranslatableComponent;
import dev.tmdevelopment.flaremessages.libs.kyori.adventure.text.TranslationArgument;
import dev.tmdevelopment.flaremessages.libs.kyori.adventure.text.event.ClickEvent;
import dev.tmdevelopment.flaremessages.libs.kyori.adventure.text.event.HoverEvent;
import dev.tmdevelopment.flaremessages.libs.kyori.adventure.text.format.NamedTextColor;
import dev.tmdevelopment.flaremessages.libs.kyori.adventure.text.format.Style;
import dev.tmdevelopment.flaremessages.libs.kyori.adventure.text.format.TextColor;
import dev.tmdevelopment.flaremessages.libs.kyori.adventure.text.format.TextDecoration;
import java.util.List;

/**
 * Writes a component as the classic chat JSON ("clickEvent", "hoverEvent" with "contents").
 * The JSON is never sent to the client as it is: the server reads it with its own parser
 * (Paper's Adventure or Spigot's BungeeCord chat), so it always ends up in the right format
 * for that Minecraft version.
 */
public final class ComponentJson {
    private static final TextDecoration[] DECORATIONS = TextDecoration.values();

    private ComponentJson() {
    }

    public static String write(Component component) {
        StringBuilder sb = new StringBuilder(256);
        write(sb, component);
        return sb.toString();
    }

    private static void write(StringBuilder sb, Component c) {
        sb.append('{');
        if (c instanceof TextComponent) {
            key(sb, "text").append(quote(((TextComponent) c).content()));
        } else if (c instanceof TranslatableComponent) {
            TranslatableComponent t = (TranslatableComponent) c;
            key(sb, "translate").append(quote(t.key()));
            if (t.fallback() != null) {
                sb.append(',');
                key(sb, "fallback").append(quote(t.fallback()));
            }
            List<TranslationArgument> args = t.arguments();
            if (!args.isEmpty()) {
                sb.append(',');
                key(sb, "with").append('[');
                for (int i = 0; i < args.size(); ++i) {
                    if (i > 0) {
                        sb.append(',');
                    }
                    write(sb, args.get(i).asComponent());
                }
                sb.append(']');
            }
        } else if (c instanceof KeybindComponent) {
            key(sb, "keybind").append(quote(((KeybindComponent) c).keybind()));
        } else {
            // score / selector / nbt components are never created by this plugin
            key(sb, "text").append("\"\"");
        }
        style(sb, c.style());
        List<Component> children = c.children();
        if (!children.isEmpty()) {
            sb.append(',');
            key(sb, "extra").append('[');
            for (int i = 0; i < children.size(); ++i) {
                if (i > 0) {
                    sb.append(',');
                }
                write(sb, children.get(i));
            }
            sb.append(']');
        }
        sb.append('}');
    }

    private static void style(StringBuilder sb, Style style) {
        TextColor color = style.color();
        if (color != null) {
            sb.append(',');
            key(sb, "color").append(quote(color instanceof NamedTextColor ? NamedTextColor.NAMES.key((NamedTextColor) color) : color.asHexString()));
        }
        for (TextDecoration d : DECORATIONS) {
            TextDecoration.State state = style.decoration(d);
            if (state != TextDecoration.State.NOT_SET) {
                sb.append(',');
                key(sb, decorationName(d)).append(state == TextDecoration.State.TRUE);
            }
        }
        if (style.font() != null) {
            sb.append(',');
            key(sb, "font").append(quote(style.font().asString()));
        }
        if (style.insertion() != null) {
            sb.append(',');
            key(sb, "insertion").append(quote(style.insertion()));
        }
        ClickEvent click = style.clickEvent();
        if (click != null && click.action() != ClickEvent.Action.OPEN_FILE) {
            sb.append(',');
            key(sb, "clickEvent").append('{');
            key(sb, "action").append(quote(click.action().toString())).append(',');
            key(sb, "value").append(quote(click.value())).append('}');
        }
        HoverEvent<?> hover = style.hoverEvent();
        if (hover != null && hover.action() == HoverEvent.Action.SHOW_TEXT) {
            sb.append(',');
            key(sb, "hoverEvent").append('{');
            key(sb, "action").append("\"show_text\",");
            key(sb, "contents");
            write(sb, (Component) hover.value());
            sb.append('}');
        }
    }

    private static String decorationName(TextDecoration d) {
        switch (d) {
            case OBFUSCATED:
                return "obfuscated";
            case BOLD:
                return "bold";
            case STRIKETHROUGH:
                return "strikethrough";
            case UNDERLINED:
                return "underlined";
            default:
                return "italic";
        }
    }

    private static StringBuilder key(StringBuilder sb, String key) {
        return sb.append('"').append(key).append("\":");
    }

    static String quote(String s) {
        StringBuilder b = new StringBuilder(s.length() + 2).append('"');
        for (int i = 0; i < s.length(); ++i) {
            char ch = s.charAt(i);
            switch (ch) {
                case '"':
                    b.append("\\\"");
                    break;
                case '\\':
                    b.append("\\\\");
                    break;
                case '\n':
                    b.append("\\n");
                    break;
                case '\r':
                    b.append("\\r");
                    break;
                case '\t':
                    b.append("\\t");
                    break;
                default:
                    if (ch < 0x20) {
                        b.append(String.format("\\u%04x", (int) ch));
                    } else {
                        b.append(ch);
                    }
            }
        }
        return b.append('"').toString();
    }
}
