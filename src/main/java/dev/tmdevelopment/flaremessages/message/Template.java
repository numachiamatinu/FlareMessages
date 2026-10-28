package dev.tmdevelopment.flaremessages.message;

import dev.tmdevelopment.flaremessages.libs.kyori.adventure.text.Component;

/**
 * A pre-compiled text. Without placeholders ({..}, %..%, face) the Component is built once on load
 * and reused for every player.
 */
public final class Template {
    public static final Template EMPTY = new Template("", false, false, Component.empty());

    private final String raw;
    private final boolean center;
    private final boolean dynamic;
    private final Component cached;

    public Template(String raw, boolean center, boolean dynamic, Component cached) {
        this.raw = raw;
        this.center = center;
        this.dynamic = dynamic;
        this.cached = cached;
    }

    public String raw() {
        return this.raw;
    }

    public boolean center() {
        return this.center;
    }

    public boolean dynamic() {
        return this.dynamic;
    }

    public Component cached() {
        return this.cached;
    }

    public boolean isEmpty() {
        return this.raw.isEmpty();
    }
}
