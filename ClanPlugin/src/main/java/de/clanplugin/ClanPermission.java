package de.clanplugin;

import org.bukkit.Material;

/**
 * Berechtigungen, die der Owner im Einstellungs-Menue pro Mindestrang festlegen kann.
 * Der Owner darf immer alles.
 */
public enum ClanPermission {
    MANAGE_MEMBERS("Spieler verwalten", Material.PLAYER_HEAD, Role.ADMIN,
            "Spieler einladen und rauswerfen"),
    OPEN_ENDERCHEST("Ender Chest öffnen", Material.ENDER_CHEST, Role.MEMBER,
            "Die Clan Ender Chest benutzen"),
    SET_HOME("Clan Home setzen", Material.RED_BED, Role.ADMIN,
            "Das Clan Home neu setzen"),
    TELEPORT_HOME("Zum Clan Home teleportieren", Material.ENDER_PEARL, Role.MEMBER,
            "Sich zum Clan Home teleportieren"),
    CHANGE_NAME("Clan-Namen ändern", Material.NAME_TAG, Role.OWNER,
            "Namen und Farben des Clans ändern");

    private final String label;
    private final Material icon;
    private final Role defaultRole;
    private final String description;

    ClanPermission(String label, Material icon, Role defaultRole, String description) {
        this.label = label;
        this.icon = icon;
        this.defaultRole = defaultRole;
        this.description = description;
    }

    public String label() {
        return label;
    }

    public Material icon() {
        return icon;
    }

    public Role defaultRole() {
        return defaultRole;
    }

    public String description() {
        return description;
    }
}
