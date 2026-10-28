package dev.tmdevelopment.flaremessages.util;

import dev.tmdevelopment.flaremessages.libs.kyori.adventure.text.Component;
import dev.tmdevelopment.flaremessages.libs.kyori.adventure.text.TextComponent;
import dev.tmdevelopment.flaremessages.libs.kyori.adventure.text.format.TextDecoration;

/** Centers a chat line using the real pixel width of the Minecraft font. */
public final class Center {
    private static final int CENTER_PX = 154;
    private static final int SPACE_PX = 4;

    private Center() {
    }

    public static Component apply(Component line) {
        int width = width(line, false);
        if (width <= 0) {
            return line;
        }
        int spaces = (CENTER_PX - width / 2) / SPACE_PX;
        return spaces <= 0 ? line : Component.text(Text.repeat(" ", spaces)).append(line);
    }

    private static int width(Component c, boolean parentBold) {
        TextDecoration.State st = c.decoration(TextDecoration.BOLD);
        boolean bold = st == TextDecoration.State.TRUE || st == TextDecoration.State.NOT_SET && parentBold;
        int w = 0;
        if (c instanceof TextComponent) {
            String s = ((TextComponent) c).content();
            for (int i = 0, n = s.length(); i < n; ++i) {
                char ch = s.charAt(i);
                w += charWidth(ch) + (bold && ch != ' ' ? 2 : 1);
            }
        }
        for (Component child : c.children()) {
            w += width(child, bold);
        }
        return w;
    }

    private static int charWidth(char c) {
        switch (c) {
            case 'i': case '!': case ',': case '.': case ':': case ';': case '|': case '\'':
                return 1;
            case 'l': case '`':
                return 2;
            case 'I': case '[': case ']': case 't': case ' ':
                return 3;
            case 'f': case 'k': case '<': case '>': case '"': case '(': case ')': case '*': case '{': case '}':
                return 4;
            case '@': case '~':
                return 6;
            case '█': case '■':
                return 7;
            default:
                return 5;
        }
    }
}
