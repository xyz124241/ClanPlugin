package de.clanplugin;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Markiert ein Inventar als Clan-Menue (oder Ender Chest) und merkt sich den Kontext. */
public final class ClanHolder implements InventoryHolder {

    public enum Type {
        MAIN, MEMBERS, MEMBER, SETTINGS, ENDER
    }

    private final Type type;
    private final UUID clanId;
    private final UUID target;
    private final List<UUID> entries = new ArrayList<>();
    private Inventory inventory;

    public ClanHolder(Type type, UUID clanId, UUID target) {
        this.type = type;
        this.clanId = clanId;
        this.target = target;
    }

    public Type type() {
        return type;
    }

    public UUID clanId() {
        return clanId;
    }

    /** Ziel-Spieler im Mitglieder-Detailmenue (sonst null). */
    public UUID target() {
        return target;
    }

    /** Spieler pro Slot im Mitglieder-Menue. */
    public List<UUID> entries() {
        return entries;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
