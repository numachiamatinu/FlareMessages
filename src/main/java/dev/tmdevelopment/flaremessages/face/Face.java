package dev.tmdevelopment.flaremessages.face;

import dev.tmdevelopment.flaremessages.libs.kyori.adventure.text.Component;
import dev.tmdevelopment.flaremessages.util.Text;
import dev.tmdevelopment.flaremessages.libs.kyori.adventure.text.TextComponent;
import dev.tmdevelopment.flaremessages.libs.kyori.adventure.text.format.TextColor;
import dev.tmdevelopment.flaremessages.libs.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import dev.tmdevelopment.flaremessages.libs.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

/**
 * 8x8 skin face (RGB pixels) + the 8 rows already built as Components.
 * Neighbour pixels with the same color are merged so the chat packets stay small.
 */
public final class Face {

    /** Used when a line contains {@code <face_N>} but no face is available. */
    public static final TagResolver EMPTY_RESOLVER;

    static {
        TagResolver.Builder b = TagResolver.builder();
        for (int i = 1; i <= 8; i++) b.resolver(Placeholder.component("face_" + i, Component.empty()));
        EMPTY_RESOLVER = b.build();
    }

    private final String source;
    private final int[] pixels;
    private final long createdAt;
    private volatile Component[] rows;
    private volatile String[] legacyRows;
    private volatile TagResolver resolver;

    public Face(String source, int[] pixels, long createdAt) {
        this.source = source;
        this.pixels = pixels;
        this.createdAt = createdAt;
    }

    void build(String symbol) {
        Component[] built = new Component[8];
        TagResolver.Builder rb = TagResolver.builder();
        for (int y = 0; y < 8; y++) {
            TextComponent.Builder row = Component.text();
            int x = 0;
            while (x < 8) {
                int rgb = pixels[y * 8 + x];
                int run = 1;
                while (x + run < 8 && pixels[y * 8 + x + run] == rgb) run++;
                row.append(Component.text(Text.repeat(symbol, run), TextColor.color(rgb)));
                x += run;
            }
            built[y] = row.build();
            rb.resolver(Placeholder.component("face_" + (y + 1), built[y]));
        }
        String[] legacy = new String[8];
        for (int y = 0; y < 8; y++) {
            StringBuilder sb = new StringBuilder();
            for (int x = 0; x < 8; x++) {
                sb.append(String.format("&#%06x", pixels[y * 8 + x])).append(symbol);
            }
            legacy[y] = sb.toString();
        }
        this.legacyRows = legacy;
        this.rows = built;
        this.resolver = rb.build();
    }

    public Component row(int index) {
        return rows[index];
    }

    /** Row as "&#rrggbb█..." text, used by the PlaceholderAPI expansion. */
    public String legacyRow(int index) {
        return legacyRows[index];
    }

    public Component[] rows() {
        return rows;
    }

    public TagResolver resolver() {
        return resolver;
    }

    public String source() {
        return source;
    }

    public int[] pixels() {
        return pixels;
    }

    public long createdAt() {
        return createdAt;
    }
}
