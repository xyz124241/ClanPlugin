package de.clanplugin;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class ClanListener implements Listener {

    private final ClanPlugin plugin;

    public ClanListener(ClanPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.clans().touchName(player);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                plugin.nametags().refresh(player);
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        plugin.nametags().remove(player);
        plugin.menus().forget(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = null;
        if (event.getDamager() instanceof Player direct) {
            attacker = direct;
        } else if (event.getDamager() instanceof Projectile projectile
                && projectile.getShooter() instanceof Player shooter) {
            attacker = shooter;
        }
        if (attacker == null || attacker.equals(victim)) {
            return;
        }
        Clan clan = plugin.clans().getClan(attacker.getUniqueId());
        if (clan == null || clan != plugin.clans().getClan(victim.getUniqueId())) {
            return;
        }
        if (!clan.friendlyFire()) {
            event.setCancelled(true);
            attacker.sendActionBar(Text.c("<red>Friendly Fire ist in deinem Clan deaktiviert."));
        }
    }
}
