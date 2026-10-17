package de.clanplugin;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Verwaltet alle Clans: Speicherung, Mitglieder, Einladungen, Ender Chest. */
public final class ClanManager {

    private final ClanPlugin plugin;
    private final File file;
    private final Map<UUID, Clan> clans = new LinkedHashMap<>();
    private final Map<UUID, UUID> playerClan = new HashMap<>();
    /** Eingeladener Spieler -> (Clan-ID -> Ablaufzeit in Millisekunden). */
    private final Map<UUID, Map<UUID, Long>> invites = new HashMap<>();
    private boolean saveScheduled;

    public ClanManager(ClanPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "clans.yml");
    }

    // ------------------------------------------------------------------ Lookup

    public Clan getClan(UUID player) {
        UUID id = playerClan.get(player);
        return id == null ? null : clans.get(id);
    }

    public Clan getClanById(UUID id) {
        return clans.get(id);
    }

    public Clan findByPlainName(String plain) {
        for (Clan clan : clans.values()) {
            if (clan.plainName().equalsIgnoreCase(plain)) {
                return clan;
            }
        }
        return null;
    }

    public int maxMembers() {
        return Math.max(1, Math.min(45, plugin.getConfig().getInt("max-members", 30)));
    }

    /** Gibt eine Fehlermeldung zurueck oder null, wenn der Name gueltig ist. */
    public String validateName(String raw, Clan ignore) {
        if (raw == null || raw.isBlank()) {
            return "Der Name darf nicht leer sein.";
        }
        if (raw.length() > 150) {
            return "Der Name ist zu lang.";
        }
        String plain = Text.plain(raw);
        if (plain.isEmpty() || plain.length() > 7) {
            return "Der Name muss 1 bis 7 Zeichen lang sein (ohne Farbcodes gezählt).";
        }
        if (!plain.matches("[\\p{L}\\p{N}_]+")) {
            return "Erlaubt sind nur Buchstaben, Zahlen und Unterstrich.";
        }
        Clan other = findByPlainName(plain);
        if (other != null && other != ignore) {
            return "Dieser Clan-Name ist bereits vergeben.";
        }
        return null;
    }

    // ------------------------------------------------------------------ Aktionen

    public Clan create(Player owner, String raw) {
        Clan clan = new Clan(UUID.randomUUID(), raw, owner.getUniqueId());
        clan.members().put(owner.getUniqueId(), Role.OWNER);
        clan.names().put(owner.getUniqueId(), owner.getName());
        clans.put(clan.id(), clan);
        playerClan.put(owner.getUniqueId(), clan.id());
        markDirty();
        plugin.nametags().refresh(owner);
        return clan;
    }

    public void addMember(Clan clan, Player player) {
        clan.members().put(player.getUniqueId(), Role.MEMBER);
        clan.names().put(player.getUniqueId(), player.getName());
        playerClan.put(player.getUniqueId(), clan.id());
        invites.remove(player.getUniqueId());
        markDirty();
        plugin.nametags().refresh(player);
    }

    public void removeMember(Clan clan, UUID player) {
        clan.members().remove(player);
        playerClan.remove(player);
        markDirty();
        Player online = Bukkit.getPlayer(player);
        if (online != null) {
            plugin.menus().closeFor(online);
            plugin.nametags().refresh(online);
        }
    }

    public void kick(Clan clan, UUID target, Player actor) {
        String name = clan.names().getOrDefault(target, "?");
        removeMember(clan, target);
        Player online = Bukkit.getPlayer(target);
        if (online != null) {
            Text.send(online, "<red>Du wurdest aus dem Clan <clan> <red>geworfen.",
                    Placeholder.component("clan", clan.tag()));
        }
        broadcast(clan, "<yellow><player> <gray>wurde von <yellow><actor> <gray>aus dem Clan geworfen.",
                Placeholder.unparsed("player", name), Placeholder.unparsed("actor", actor.getName()));
    }

    public void setRole(Clan clan, UUID target, Role role) {
        clan.members().put(target, role);
        markDirty();
        Player online = Bukkit.getPlayer(target);
        if (online != null) {
            Text.send(online, "<gray>Dein Rang im Clan ist jetzt: " + role.colored());
        }
    }

    public void rename(Clan clan, String raw) {
        clan.setName(raw);
        markDirty();
        refreshMembers(clan);
    }

    public void setHome(Clan clan, Location location) {
        clan.setHome(Clan.Home.of(location));
        markDirty();
    }

    public void delete(Clan clan, Player actor) {
        // Offene Menues und die Ender Chest schliessen
        Inventory ender = clan.enderInventory();
        if (ender != null) {
            for (HumanEntity viewer : new ArrayList<>(ender.getViewers())) {
                viewer.closeInventory();
            }
        }
        List<ItemStack> items = new ArrayList<>();
        Collection<ItemStack> source = ender != null ? Arrays.asList(ender.getContents()) : clan.enderItems();
        for (ItemStack item : source) {
            if (item != null && item.getType() != Material.AIR) {
                items.add(item);
            }
        }

        List<UUID> memberIds = new ArrayList<>(clan.members().keySet());
        for (UUID id : memberIds) {
            Player online = Bukkit.getPlayer(id);
            if (online != null) {
                plugin.menus().closeFor(online);
            }
            playerClan.remove(id);
        }
        clans.remove(clan.id());
        markDirty();

        for (UUID id : memberIds) {
            Player online = Bukkit.getPlayer(id);
            if (online != null) {
                plugin.nametags().refresh(online);
                if (!id.equals(actor.getUniqueId())) {
                    Text.send(online, "<red>Der Clan <clan> <red>wurde vom Owner aufgelöst.",
                            Placeholder.component("clan", clan.tag()));
                }
            }
        }

        // Inhalt der Ender Chest geht an den Owner, damit nichts verloren geht
        for (ItemStack item : items) {
            Map<Integer, ItemStack> leftover = actor.getInventory().addItem(item);
            for (ItemStack rest : leftover.values()) {
                actor.getWorld().dropItemNaturally(actor.getLocation(), rest);
            }
        }
    }

    public void touchName(Player player) {
        Clan clan = getClan(player.getUniqueId());
        if (clan != null && !player.getName().equals(clan.names().get(player.getUniqueId()))) {
            clan.names().put(player.getUniqueId(), player.getName());
            markDirty();
        }
    }

    public void refreshMembers(Clan clan) {
        for (UUID id : clan.members().keySet()) {
            Player online = Bukkit.getPlayer(id);
            if (online != null) {
                plugin.nametags().refresh(online);
            }
        }
    }

    public void broadcast(Clan clan, String mini, TagResolver... resolvers) {
        for (UUID id : clan.members().keySet()) {
            Player online = Bukkit.getPlayer(id);
            if (online != null) {
                Text.send(online, mini, resolvers);
            }
        }
    }

    // ------------------------------------------------------------------ Einladungen

    public void invite(Clan clan, Player target) {
        long seconds = plugin.getConfig().getLong("invite-expire-seconds", 120L);
        invites.computeIfAbsent(target.getUniqueId(), k -> new HashMap<>())
                .put(clan.id(), System.currentTimeMillis() + seconds * 1000L);
    }

    /** Alle noch gueltigen Einladungen eines Spielers. */
    public List<Clan> activeInvites(UUID player) {
        List<Clan> result = new ArrayList<>();
        Map<UUID, Long> map = invites.get(player);
        if (map == null) {
            return result;
        }
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Long>> it = map.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Long> entry = it.next();
            Clan clan = clans.get(entry.getKey());
            if (clan == null || entry.getValue() < now) {
                it.remove();
            } else {
                result.add(clan);
            }
        }
        if (map.isEmpty()) {
            invites.remove(player);
        }
        return result;
    }

    public void forgetInvites(UUID player) {
        invites.remove(player);
    }

    // ------------------------------------------------------------------ Ender Chest

    public Inventory enderInventory(Clan clan) {
        Inventory inventory = clan.enderInventory();
        if (inventory != null) {
            return inventory;
        }
        int rows = Math.max(1, Math.min(6, plugin.getConfig().getInt("enderchest-rows", 3)));
        ClanHolder holder = new ClanHolder(ClanHolder.Type.ENDER, clan.id(), null);
        inventory = Bukkit.createInventory(holder, rows * 9,
                Text.c("<dark_purple>Clan Ender Chest"));
        holder.setInventory(inventory);
        List<ItemStack> stored = clan.enderItems();
        for (int i = 0; i < Math.min(stored.size(), inventory.getSize()); i++) {
            inventory.setItem(i, stored.get(i));
        }
        clan.setEnderInventory(inventory);
        return inventory;
    }

    // ------------------------------------------------------------------ Speichern / Laden

    public void markDirty() {
        if (saveScheduled || !plugin.isEnabled()) {
            return;
        }
        saveScheduled = true;
        Bukkit.getScheduler().runTaskLater(plugin, this::saveNow, 20L);
    }

    public void saveNow() {
        saveScheduled = false;
        YamlConfiguration yaml = new YamlConfiguration();
        for (Clan clan : clans.values()) {
            String p = "clans." + clan.id() + ".";
            yaml.set(p + "name", clan.name());
            yaml.set(p + "owner", clan.owner().toString());
            yaml.set(p + "friendly-fire", clan.friendlyFire());
            for (Map.Entry<UUID, Role> entry : clan.members().entrySet()) {
                String mp = p + "members." + entry.getKey() + ".";
                yaml.set(mp + "role", entry.getValue().name());
                yaml.set(mp + "name", clan.names().getOrDefault(entry.getKey(), "?"));
            }
            for (ClanPermission permission : ClanPermission.values()) {
                yaml.set(p + "permissions." + permission.name(), clan.permission(permission).name());
            }
            Clan.Home home = clan.home();
            if (home != null) {
                yaml.set(p + "home.world", home.world());
                yaml.set(p + "home.x", home.x());
                yaml.set(p + "home.y", home.y());
                yaml.set(p + "home.z", home.z());
                yaml.set(p + "home.yaw", (double) home.yaw());
                yaml.set(p + "home.pitch", (double) home.pitch());
            }
            Inventory ender = clan.enderInventory();
            if (ender != null) {
                clan.setEnderItems(Arrays.asList(ender.getContents()));
            }
            yaml.set(p + "ender", clan.enderItems());
        }

        try {
            File folder = file.getParentFile();
            if (folder != null && !folder.exists()) {
                folder.mkdirs();
            }
            File tmp = new File(file.getPath() + ".tmp");
            yaml.save(tmp);
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            plugin.getLogger().severe("Konnte clans.yml nicht speichern: " + ex.getMessage());
        }
    }

    public void load() {
        clans.clear();
        playerClan.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("clans");
        if (root == null) {
            return;
        }
        for (String key : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            try {
                Clan clan = new Clan(UUID.fromString(key), section.getString("name", "Clan"),
                        UUID.fromString(section.getString("owner", "")));
                clan.setFriendlyFire(section.getBoolean("friendly-fire", false));

                ConfigurationSection members = section.getConfigurationSection("members");
                if (members != null) {
                    for (String memberKey : members.getKeys(false)) {
                        UUID memberId = UUID.fromString(memberKey);
                        clan.members().put(memberId, Role.parse(members.getString(memberKey + ".role")));
                        clan.names().put(memberId, members.getString(memberKey + ".name", "?"));
                    }
                }
                clan.members().put(clan.owner(), Role.OWNER);

                ConfigurationSection permissions = section.getConfigurationSection("permissions");
                if (permissions != null) {
                    for (ClanPermission permission : ClanPermission.values()) {
                        String value = permissions.getString(permission.name());
                        if (value != null) {
                            clan.setPermission(permission, Role.parse(value));
                        }
                    }
                }

                ConfigurationSection home = section.getConfigurationSection("home");
                if (home != null && home.getString("world") != null) {
                    clan.setHome(new Clan.Home(home.getString("world"), home.getDouble("x"),
                            home.getDouble("y"), home.getDouble("z"),
                            (float) home.getDouble("yaw"), (float) home.getDouble("pitch")));
                }

                List<?> ender = section.getList("ender");
                if (ender != null) {
                    List<ItemStack> items = new ArrayList<>();
                    for (Object object : ender) {
                        items.add(object instanceof ItemStack stack ? stack : null);
                    }
                    clan.setEnderItems(items);
                }

                clans.put(clan.id(), clan);
                for (UUID memberId : clan.members().keySet()) {
                    playerClan.put(memberId, clan.id());
                }
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Clan-Eintrag " + key + " ist kaputt und wird übersprungen: "
                        + ex.getMessage());
            }
        }
        plugin.getLogger().info(clans.size() + " Clan(s) geladen.");
    }
}
