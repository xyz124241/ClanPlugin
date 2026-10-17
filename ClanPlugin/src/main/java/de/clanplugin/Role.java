package de.clanplugin;

import java.util.Locale;

public enum Role {
    MEMBER(0, "<gray>", "Member"),
    MOD(1, "<yellow>", "Mod"),
    ADMIN(2, "<gold>", "Admin"),
    OWNER(3, "<red>", "Owner");

    private final int level;
    private final String color;
    private final String label;

    Role(int level, String color, String label) {
        this.level = level;
        this.color = color;
        this.label = label;
    }

    public int level() {
        return level;
    }

    /** MiniMessage-Farbtag, z.B. "<red>". */
    public String color() {
        return color;
    }

    public String label() {
        return label;
    }

    /** MiniMessage-String mit Farbe und Name, z.B. "<red>Owner". */
    public String colored() {
        return color + label;
    }

    public boolean atLeast(Role other) {
        return level >= other.level;
    }

    /** Naechster Rang im Kreis MEMBER -> MOD -> ADMIN -> OWNER -> MEMBER. */
    public Role next() {
        Role[] all = values();
        return all[(ordinal() + 1) % all.length];
    }

    public static Role parse(String value) {
        if (value == null) {
            return MEMBER;
        }
        try {
            return valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return MEMBER;
        }
    }
}
