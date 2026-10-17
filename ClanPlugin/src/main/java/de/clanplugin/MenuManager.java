package de.clanplugin;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Alle Clan-Menues (Hauptmenue, Mitglieder, Einstellungen) und die Namenseingabe per Chat. */
public final class MenuManager implements Listener {

    private static final int SLOT_SET_HOME = 10;
    private static final int SLOT_TELEPORT = 11;
    private static final int SLOT_ENDER = 13;
    private static final int SLOT_NAME_TAG = 15;
    private static final int SLOT_MEMBERS = 16;
    private static final int SLOT_SETTINGS = 22;

    private final ClanPlugin plugin;
    private final ClanManager clans;
    /** Spieler, der gerade einen neuen Clan-Namen im Chat eintippt -> Clan-ID. */
    private final Map<UUID, UUID> pendingRename = new ConcurrentHashMap<>();

    public MenuManager(ClanPlugin plugin) {
        this.plugin = plugin;
        this.clans = plugin.clans();
    }

    // ------------------------------------------------------------------ Hilfen

    private void next(Runnable runnable) {
        Bukkit.getScheduler().runTask(plugin, runnable);
    }

    private Component title(Clan clan, String suffix) {
        return Component.text("Clan » ", NamedTextColor.DARK_GRAY)
                .append(clan.tag())
                .append(Component.text(" " + suffix, NamedTextColor.GRAY));
    }

    private Inventory create(ClanHolder holder, int size, Component title) {
        Inventory inventory = Bukkit.createInventory(holder, size, title);
        holder.setInventory(inventory);
        for (int i = 0; i < size; i++) {
            inventory.setItem(i, Items.filler());
        }
        return inventory;
    }

    private Clan clanOf(Player player) {
        Clan clan = clans.getClan(player.getUniqueId());
        if (clan == null) {
            Text.send(player, "<red>Du bist in keinem Clan.");
        }
        return clan;
    }

    private boolean denied(Player player, Clan clan, ClanPermission permission) {
        if (clan.can(player.getUniqueId(), permission)) {
            return false;
        }
        Text.send(player, "<red>Dafür hast du keine Berechtigung.");
        return true;
    }

    private boolean canKick(Clan clan, UUID actor, UUID target) {
        Role actorRole = clan.roleOf(actor);
        Role targetRole = clan.roleOf(target);
        return actorRole != null && targetRole != null
                && clan.can(actor, ClanPermission.MANAGE_MEMBERS)
                && actorRole.level() > targetRole.level();
    }

    private List<Component> permissionLore(Clan clan, UUID player, ClanPermission permission, String description) {
        List<Component> lore = new ArrayList<>();
        lore.add(Text.c("<gray>" + description));
        lore.add(Component.empty());
        lore.add(Text.c("<gray>Erlaubt ab: " + clan.permission(permission).colored()));
        lore.add(clan.can(player, permission)
                ? Text.c("<green>Klicke zum Ausführen")
                : Text.c("<red>Dafür fehlt dir die Berechtigung"));
        return lore;
    }

    public void closeFor(Player player) {
        if (player.getOpenInventory().getTopInventory().getHolder() instanceof ClanHolder) {
            player.closeInventory();
        }
    }

    public void forget(UUID player) {
        pendingRename.remove(player);
    }

    // ------------------------------------------------------------------ Hauptmenue

    public void openMain(Player player) {
        Clan clan = clanOf(player);
        if (clan == null) {
            return;
        }
        UUID id = player.getUniqueId();
        Inventory inventory = create(new ClanHolder(ClanHolder.Type.MAIN, clan.id(), null), 27, title(clan, "Menü"));

        inventory.setItem(SLOT_SET_HOME, Items.of(Material.RED_BED, Text.c("<green>Clan Home setzen"),
                permissionLore(clan, id, ClanPermission.SET_HOME, "Setzt das Clan Home an deine Position.")));
        inventory.setItem(SLOT_TELEPORT, Items.of(Material.ENDER_PEARL, Text.c("<aqua>Zum Clan Home"),
                permissionLore(clan, id, ClanPermission.TELEPORT_HOME,
                        clan.home() == null ? "Es ist noch kein Home gesetzt." : "Teleportiert dich zum Clan Home.")));
        inventory.setItem(SLOT_ENDER, Items.of(Material.ENDER_CHEST, Text.c("<dark_purple>Clan Ender Chest"),
                permissionLore(clan, id, ClanPermission.OPEN_ENDERCHEST, "Gemeinsamer Lagerplatz für den Clan.")));
        inventory.setItem(SLOT_NAME_TAG, Items.of(Material.NAME_TAG, Text.c("<yellow>Name Tag"), List.of(
                Text.c("<gray>Aktuell: ").append(clan.tag()),
                Component.empty(),
                Text.c("<gray>Erlaubt ab: " + clan.permission(ClanPermission.CHANGE_NAME).colored()),
                clan.can(id, ClanPermission.CHANGE_NAME)
                        ? Text.c("<green>Klicke zum Ändern")
                        : Text.c("<red>Dafür fehlt dir die Berechtigung"))));
        inventory.setItem(SLOT_MEMBERS, Items.of(Material.PLAYER_HEAD, Text.c("<gold>Mitglieder"), List.of(
                Text.c("<gray>Mitglieder: <white>" + clan.members().size() + "<gray>/<white>" + clans.maxMembers()),
                Text.c("<green>Klicke für die Übersicht"))));
        inventory.setItem(SLOT_SETTINGS, Items.of(Material.COMPARATOR, Text.c("<red>Einstellungen"), List.of(
                Text.c("<gray>Rechte und Friendly Fire"),
                clan.isOwner(id) ? Text.c("<green>Klicke zum Bearbeiten") : Text.c("<gray>Nur der Owner kann ändern"))));

        player.openInventory(inventory);
    }

    private void clickMain(Player player, Clan clan, int slot) {
        switch (slot) {
            case SLOT_SET_HOME -> {
                if (denied(player, clan, ClanPermission.SET_HOME)) {
                    return;
                }
                Location location = player.getLocation();
                clans.setHome(clan, location);
                next(player::closeInventory);
                Text.send(player, "<green>Das Clan Home wurde an deiner Position gesetzt.");
            }
            case SLOT_TELEPORT -> {
                if (denied(player, clan, ClanPermission.TELEPORT_HOME)) {
                    return;
                }
                Clan.Home home = clan.home();
                Location target = home == null ? null : home.toLocation();
                if (target == null) {
                    Text.send(player, "<red>Es ist noch kein Clan Home gesetzt.");
                    return;
                }
                next(() -> {
                    player.closeInventory();
                    player.teleport(target);
                    Text.send(player, "<green>Du wurdest zum Clan Home teleportiert.");
                });
            }
            case SLOT_ENDER -> {
                if (denied(player, clan, ClanPermission.OPEN_ENDERCHEST)) {
                    return;
                }
                next(() -> player.openInventory(clans.enderInventory(clan)));
            }
            case SLOT_NAME_TAG -> {
                if (denied(player, clan, ClanPermission.CHANGE_NAME)) {
                    return;
                }
                pendingRename.put(player.getUniqueId(), clan.id());
                next(player::closeInventory);
                Text.send(player, "<gray>Schreibe jetzt den neuen Clan-Namen in den Chat "
                                + "(1-7 Zeichen, Farben per MiniMessage, z.B. <example><gray>). "
                                + "Mit <yellow>cancel <gray>brichst du ab.",
                        Placeholder.unparsed("example", "<gradient:red:blue>Wolf</gradient>"));
            }
            case SLOT_MEMBERS -> next(() -> openMembers(player));
            case SLOT_SETTINGS -> next(() -> openSettings(player));
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------ Mitglieder

    public void openMembers(Player player) {
        Clan clan = clanOf(player);
        if (clan == null) {
            return;
        }
        UUID viewer = player.getUniqueId();
        ClanHolder holder = new ClanHolder(ClanHolder.Type.MEMBERS, clan.id(), null);
        Inventory inventory = create(holder, 54, title(clan, "Mitglieder"));

        List<UUID> sorted = new ArrayList<>(clan.members().keySet());
        sorted.sort(Comparator.<UUID>comparingInt(u -> -clan.roleOf(u).level())
                .thenComparing((UUID u) -> clan.names().getOrDefault(u, "?"), String.CASE_INSENSITIVE_ORDER));

        for (int i = 0; i < sorted.size() && i < 45; i++) {
            UUID member = sorted.get(i);
            Role role = clan.roleOf(member);
            List<Component> lore = new ArrayList<>();
            lore.add(Text.c("<gray>Rang: " + role.colored()));
            if (clan.isOwner(member)) {
                lore.add(Text.c("<gold>★ Hat den Clan erstellt"));
            }
            if (!member.equals(viewer) && (clan.isOwner(viewer) || canKick(clan, viewer, member))) {
                lore.add(Component.empty());
                lore.add(Text.c("<green>Klicke zum Verwalten"));
            }
            Component name = Text.c(role.color() + "<name>",
                    Placeholder.unparsed("name", clan.names().getOrDefault(member, "?")));
            inventory.setItem(i, Items.head(member, name, lore));
            holder.entries().add(member);
        }
        inventory.setItem(49, Items.simple(Material.ARROW, "<gray>Zurück"));
        player.openInventory(inventory);
    }

    private void clickMembers(Player player, Clan clan, ClanHolder holder, int slot) {
        if (slot == 49) {
            next(() -> openMain(player));
            return;
        }
        if (slot < 0 || slot >= holder.entries().size()) {
            return;
        }
        UUID viewer = player.getUniqueId();
        UUID target = holder.entries().get(slot);
        if (target.equals(viewer)) {
            return;
        }
        if (clan.isOwner(viewer) || canKick(clan, viewer, target)) {
            next(() -> openMember(player, target));
        }
    }

    // ------------------------------------------------------------------ Einzelnes Mitglied

    public void openMember(Player player, UUID target) {
        Clan clan = clanOf(player);
        if (clan == null) {
            return;
        }
        Role targetRole = clan.roleOf(target);
        if (targetRole == null) {
            openMembers(player);
            return;
        }
        UUID viewer = player.getUniqueId();
        String name = clan.names().getOrDefault(target, "?");
        Inventory inventory = create(new ClanHolder(ClanHolder.Type.MEMBER, clan.id(), target), 27,
                title(clan, "» " + name));

        inventory.setItem(4, Items.head(target,
                Text.c(targetRole.color() + "<name>", Placeholder.unparsed("name", name)),
                List.of(Text.c("<gray>Rang: " + targetRole.colored()))));

        boolean owner = clan.isOwner(viewer);
        inventory.setItem(10, roleButton(Material.NETHER_STAR, Role.ADMIN, targetRole, owner));
        inventory.setItem(12, roleButton(Material.GOLD_INGOT, Role.MOD, targetRole, owner));
        inventory.setItem(14, roleButton(Material.IRON_INGOT, Role.MEMBER, targetRole, owner));

        boolean kickable = canKick(clan, viewer, target);
        inventory.setItem(16, Items.of(Material.BARRIER, Text.c("<red>Aus dem Clan werfen"), List.of(
                kickable ? Text.c("<green>Klicke zum Rauswerfen") : Text.c("<red>Dafür fehlt dir die Berechtigung"))));
        inventory.setItem(22, Items.simple(Material.ARROW, "<gray>Zurück"));
        player.openInventory(inventory);
    }

    private ItemStack roleButton(Material material, Role role, Role current, boolean owner) {
        List<Component> lore = new ArrayList<>();
        if (role == current) {
            lore.add(Text.c("<green>Aktueller Rang"));
        } else if (owner) {
            lore.add(Text.c("<green>Klicke, um diesen Rang zu vergeben"));
        } else {
            lore.add(Text.c("<gray>Nur der Owner kann Ränge vergeben"));
        }
        return Items.of(material, Text.c("<gray>Rang setzen: " + role.colored()), lore);
    }

    private void clickMember(Player player, Clan clan, ClanHolder holder, int slot) {
        UUID viewer = player.getUniqueId();
        UUID target = holder.target();
        if (clan.roleOf(target) == null) {
            next(() -> openMembers(player));
            return;
        }
        switch (slot) {
            case 10, 12, 14 -> {
                if (!clan.isOwner(viewer)) {
                    Text.send(player, "<red>Nur der Owner kann Ränge vergeben.");
                    return;
                }
                if (clan.isOwner(target)) {
                    return;
                }
                Role role = slot == 10 ? Role.ADMIN : slot == 12 ? Role.MOD : Role.MEMBER;
                clans.setRole(clan, target, role);
                next(() -> openMember(player, target));
            }
            case 16 -> {
                if (!canKick(clan, viewer, target)) {
                    Text.send(player, "<red>Du kannst diesen Spieler nicht rauswerfen.");
                    return;
                }
                clans.kick(clan, target, player);
                next(() -> openMembers(player));
            }
            case 22 -> next(() -> openMembers(player));
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------ Einstellungen

    public void openSettings(Player player) {
        Clan clan = clanOf(player);
        if (clan == null) {
            return;
        }
        Inventory inventory = create(new ClanHolder(ClanHolder.Type.SETTINGS, clan.id(), null), 27,
                title(clan, "Einstellungen"));
        fillSettings(inventory, clan, player);
        player.openInventory(inventory);
    }

    private void fillSettings(Inventory inventory, Clan clan, Player player) {
        boolean owner = clan.isOwner(player.getUniqueId());
        ClanPermission[] permissions = ClanPermission.values();
        for (int i = 0; i < permissions.length; i++) {
            ClanPermission permission = permissions[i];
            List<Component> lore = new ArrayList<>();
            lore.add(Text.c("<gray>" + permission.description()));
            lore.add(Component.empty());
            lore.add(Text.c("<gray>Mindestrang: " + clan.permission(permission).colored()));
            lore.add(owner ? Text.c("<green>Klicke, um den Mindestrang zu ändern")
                    : Text.c("<gray>Nur der Owner kann ändern"));
            inventory.setItem(10 + i, Items.of(permission.icon(), Text.c("<yellow>" + permission.label()), lore));
        }
        boolean friendlyFire = clan.friendlyFire();
        inventory.setItem(16, Items.of(friendlyFire ? Material.LIME_DYE : Material.RED_DYE,
                Text.c("<yellow>Friendly Fire: " + (friendlyFire ? "<green>An" : "<red>Aus")), List.of(
                        Text.c("<gray>Dürfen sich Clanmitglieder"),
                        Text.c("<gray>gegenseitig angreifen?"),
                        Component.empty(),
                        owner ? Text.c("<green>Klicke zum Umschalten") : Text.c("<gray>Nur der Owner kann ändern"))));
        inventory.setItem(22, Items.simple(Material.ARROW, "<gray>Zurück"));
    }

    private void clickSettings(Player player, Clan clan, ClanHolder holder, int slot) {
        if (slot == 22) {
            next(() -> openMain(player));
            return;
        }
        ClanPermission[] permissions = ClanPermission.values();
        boolean permissionSlot = slot >= 10 && slot < 10 + permissions.length;
        if (!permissionSlot && slot != 16) {
            return;
        }
        if (!clan.isOwner(player.getUniqueId())) {
            Text.send(player, "<red>Nur der Owner kann Einstellungen ändern.");
            return;
        }
        if (permissionSlot) {
            ClanPermission permission = permissions[slot - 10];
            clan.setPermission(permission, clan.permission(permission).next());
        } else {
            clan.setFriendlyFire(!clan.friendlyFire());
        }
        clans.markDirty();
        fillSettings(holder.getInventory(), clan, player);
    }

    // ------------------------------------------------------------------ Ender Chest

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof ClanHolder holder
                && holder.type() == ClanHolder.Type.ENDER) {
            clans.markDirty();
        }
    }

    // ------------------------------------------------------------------ Klicks

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof ClanHolder holder)) {
            return;
        }
        if (holder.type() == ClanHolder.Type.ENDER) {
            return; // Ender Chest verhaelt sich wie eine normale Kiste
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClickedInventory() != top) {
            return;
        }
        Clan clan = clans.getClanById(holder.clanId());
        if (clan == null || clan.roleOf(player.getUniqueId()) == null) {
            next(player::closeInventory);
            return;
        }
        int slot = event.getSlot();
        switch (holder.type()) {
            case MAIN -> clickMain(player, clan, slot);
            case MEMBERS -> clickMembers(player, clan, holder, slot);
            case MEMBER -> clickMember(player, clan, holder, slot);
            case SETTINGS -> clickSettings(player, clan, holder, slot);
            default -> {
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof ClanHolder holder
                && holder.type() != ClanHolder.Type.ENDER) {
            event.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------ Namenseingabe im Chat

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        UUID clanId = pendingRename.get(player.getUniqueId());
        if (clanId == null) {
            return;
        }
        event.setCancelled(true);
        String input = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        next(() -> handleRename(player, clanId, input));
    }

    private void handleRename(Player player, UUID clanId, String input) {
        if (!player.isOnline() || !pendingRename.containsKey(player.getUniqueId())) {
            return;
        }
        if (input.equalsIgnoreCase("cancel") || input.equalsIgnoreCase("abbrechen")) {
            pendingRename.remove(player.getUniqueId());
            Text.send(player, "<gray>Namensänderung abgebrochen.");
            return;
        }
        Clan clan = clans.getClanById(clanId);
        if (clan == null || !clan.can(player.getUniqueId(), ClanPermission.CHANGE_NAME)) {
            pendingRename.remove(player.getUniqueId());
            Text.send(player, "<red>Du kannst den Clan-Namen nicht (mehr) ändern.");
            return;
        }
        String error = clans.validateName(input, clan);
        if (error != null) {
            Text.send(player, "<red>" + error + " <gray>Versuche es erneut oder schreibe <yellow>cancel<gray>.");
            return;
        }
        pendingRename.remove(player.getUniqueId());
        clans.rename(clan, input);
        Text.send(player, "<green>Der Clan heißt jetzt <clan><green>.",
                Placeholder.component("clan", clan.tag()));
    }
}
