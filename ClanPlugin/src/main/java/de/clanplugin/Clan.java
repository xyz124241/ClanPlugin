package de.clanplugin;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class Clan {

    public record Home(String world, double x, double y, double z, float yaw, float pitch) {
        public static Home of(Location location) {
            return new Home(location.getWorld().getName(), location.getX(), location.getY(),
                    location.getZ(), location.getYaw(), location.getPitch());
        }

        /** Liefert null, wenn die Welt nicht (mehr) existiert. */
        public Location toLocation() {
            World w = Bukkit.getWorld(world);
            return w == null ? null : new Location(w, x, y, z, yaw, pitch);
        }
    }

    private final UUID id;
    private String name; // rohes MiniMessage, z.B. "<gradient:red:blue>Wolf</gradient>"
    private UUID owner;
    private final Map<UUID, Role> members = new LinkedHashMap<>();
    private final Map<UUID, String> names = new HashMap<>();
    private final EnumMap<ClanPermission, Role> permissions = new EnumMap<>(ClanPermission.class);
    private Home home;
    private boolean friendlyFire;
    private List<ItemStack> enderItems = new ArrayList<>();
    private Inventory enderInventory;

    public Clan(UUID id, String name, UUID owner) {
        this.id = id;
        this.name = name;
        this.owner = owner;
        for (ClanPermission permission : ClanPermission.values()) {
            permissions.put(permission, permission.defaultRole());
        }
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /** Farbiger Clan-Name. */
    public Component tag() {
        return Text.tag(name);
    }

    /** Klarname ohne Farben. */
    public String plainName() {
        return Text.plain(name);
    }

    public UUID owner() {
        return owner;
    }

    public boolean isOwner(UUID player) {
        return owner.equals(player);
    }

    public Map<UUID, Role> members() {
        return members;
    }

    public Map<UUID, String> names() {
        return names;
    }

    public Role roleOf(UUID player) {
        return members.get(player);
    }

    public Role permission(ClanPermission permission) {
        return permissions.get(permission);
    }

    public void setPermission(ClanPermission permission, Role minimum) {
        permissions.put(permission, minimum);
    }

    /** Darf der Spieler das tun? Der Owner darf immer. */
    public boolean can(UUID player, ClanPermission permission) {
        Role role = members.get(player);
        if (role == null) {
            return false;
        }
        if (role == Role.OWNER) {
            return true;
        }
        return role.atLeast(permissions.get(permission));
    }

    public Home home() {
        return home;
    }

    public void setHome(Home home) {
        this.home = home;
    }

    public boolean friendlyFire() {
        return friendlyFire;
    }

    public void setFriendlyFire(boolean friendlyFire) {
        this.friendlyFire = friendlyFire;
    }

    public List<ItemStack> enderItems() {
        return enderItems;
    }

    public void setEnderItems(List<ItemStack> items) {
        this.enderItems = new ArrayList<>(items);
    }

    /** Geteiltes Ender-Chest-Inventar (null, solange es noch nie geoeffnet wurde). */
    public Inventory enderInventory() {
        return enderInventory;
    }

    public void setEnderInventory(Inventory enderInventory) {
        this.enderInventory = enderInventory;
    }
}
