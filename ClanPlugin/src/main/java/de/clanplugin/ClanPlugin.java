package de.clanplugin;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

public final class ClanPlugin extends JavaPlugin {

    private ClanManager clans;
    private NametagManager nametags;
    private MenuManager menus;
    private LuckPermsHook luckPermsHook;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        clans = new ClanManager(this);
        clans.load();

        nametags = new NametagManager(this);
        if (getServer().getPluginManager().isPluginEnabled("LuckPerms")) {
            try {
                luckPermsHook = new LuckPermsHook(this, this::refreshLater);
                nametags.setLuckPerms(luckPermsHook);
                getLogger().info("LuckPerms gefunden - Rang-Prefix wird hinter dem Clan angezeigt.");
            } catch (Throwable ex) {
                getLogger().warning("LuckPerms konnte nicht angebunden werden: " + ex.getMessage());
            }
        } else {
            getLogger().warning("LuckPerms nicht gefunden - es wird nur der Clan-Prefix angezeigt.");
        }

        menus = new MenuManager(this);
        getServer().getPluginManager().registerEvents(menus, this);
        getServer().getPluginManager().registerEvents(new ClanListener(this), this);

        PluginCommand command = getCommand("clan");
        if (command != null) {
            ClanCommand executor = new ClanCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }

        // Alle 5 Minuten zwischenspeichern (z.B. wegen offener Ender Chests)
        Bukkit.getScheduler().runTaskTimer(this, () -> clans.saveNow(), 20L * 300, 20L * 300);

        nametags.refreshAll();
    }

    @Override
    public void onDisable() {
        if (menus != null) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                menus.closeFor(player);
            }
        }
        if (clans != null) {
            clans.saveNow();
        }
        if (nametags != null) {
            nametags.shutdown();
        }
        if (luckPermsHook != null) {
            luckPermsHook.close();
        }
    }

    /** Wird von LuckPerms (evtl. asynchron) aufgerufen -> auf dem Hauptthread neu zeichnen. */
    private void refreshLater(UUID uuid) {
        if (!isEnabled()) {
            return;
        }
        Bukkit.getScheduler().runTask(this, () -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                nametags.refresh(player);
            }
        });
    }

    public ClanManager clans() {
        return clans;
    }

    public NametagManager nametags() {
        return nametags;
    }

    public MenuManager menus() {
        return menus;
    }
}
