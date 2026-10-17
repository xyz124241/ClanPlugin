package de.clanplugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Zeigt [Clan] + LuckPerms-Rang vor dem Spielernamen an - in der Tab-Liste und ueber dem Kopf.
 * Dafuer bekommt jeder Spieler ein eigenes Scoreboard-Team mit Prefix. Der Chat bleibt unberuehrt.
 */
public final class NametagManager {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .build();

    private final ClanPlugin plugin;
    private final Map<UUID, String> teamNames = new HashMap<>();
    private LuckPermsHook luckPerms;

    public NametagManager(ClanPlugin plugin) {
        this.plugin = plugin;
    }

    public void setLuckPerms(LuckPermsHook hook) {
        this.luckPerms = hook;
    }

    private Scoreboard board() {
        return Bukkit.getScoreboardManager().getMainScoreboard();
    }

    public void refreshAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            refresh(player);
        }
    }

    public void refresh(Player player) {
        Component prefix = Component.empty();
        boolean hasPrefix = false;

        Clan clan = plugin.clans().getClan(player.getUniqueId());
        if (clan != null) {
            String format = plugin.getConfig().getString("tab.clan-format",
                    "<dark_gray>[</dark_gray><clan><dark_gray>]</dark_gray> ");
            prefix = prefix.append(Text.FULL.deserialize(format, Placeholder.component("clan", clan.tag())));
            hasPrefix = true;
        }

        int weight = 0;
        if (luckPerms != null && plugin.getConfig().getBoolean("tab.show-luckperms-prefix", true)) {
            String raw = luckPerms.prefix(player);
            if (!raw.isBlank()) {
                prefix = prefix.append(parseLuckPerms(raw)).append(Component.space());
                hasPrefix = true;
            }
            weight = luckPerms.weight(player);
        }

        if (!hasPrefix) {
            remove(player);
            return;
        }

        // Teamname bestimmt die Reihenfolge in der Tab-Liste: hoehere Gewichtung steht weiter oben.
        int sort = 999 - Math.max(0, Math.min(999, weight));
        String wanted = String.format("%03d", sort)
                + player.getUniqueId().toString().replace("-", "").substring(0, 12);

        Scoreboard board = board();
        String old = teamNames.put(player.getUniqueId(), wanted);
        if (old != null && !old.equals(wanted)) {
            Team oldTeam = board.getTeam(old);
            if (oldTeam != null) {
                oldTeam.unregister();
            }
        }

        Team team = board.getTeam(wanted);
        if (team == null) {
            team = board.registerNewTeam(wanted);
        }
        team.prefix(prefix);
        if (!team.hasEntry(player.getName())) {
            team.addEntry(player.getName());
        }
    }

    public void remove(Player player) {
        String name = teamNames.remove(player.getUniqueId());
        if (name != null) {
            Team team = board().getTeam(name);
            if (team != null) {
                team.unregister();
            }
        }
    }

    public void shutdown() {
        Scoreboard board = board();
        for (String name : teamNames.values()) {
            Team team = board.getTeam(name);
            if (team != null) {
                team.unregister();
            }
        }
        teamNames.clear();
    }

    private Component parseLuckPerms(String raw) {
        String format = plugin.getConfig().getString("tab.luckperms-prefix-format", "LEGACY");
        if (format.equalsIgnoreCase("MINIMESSAGE")) {
            return Text.FULL.deserialize(raw);
        }
        return LEGACY.deserialize(raw.replace('§', '&'));
    }
}
