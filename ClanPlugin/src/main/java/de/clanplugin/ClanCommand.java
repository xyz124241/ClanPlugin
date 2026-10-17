package de.clanplugin;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class ClanCommand implements TabExecutor {

    private static final List<String> SUBCOMMANDS = List.of("create", "invite", "accept", "delete", "leave");

    private final ClanPlugin plugin;

    public ClanCommand(ClanPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            Text.send(sender, "<red>Dieser Befehl ist nur für Spieler.");
            return true;
        }
        ClanManager clans = plugin.clans();

        if (args.length == 0) {
            if (clans.getClan(player.getUniqueId()) != null) {
                plugin.menus().openMain(player);
            } else {
                sendHelp(player);
            }
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "create" -> create(player, args);
            case "invite" -> invite(player, args);
            case "accept" -> accept(player, args);
            case "delete" -> delete(player, args);
            case "leave" -> leave(player);
            default -> sendHelp(player);
        }
        return true;
    }

    private void sendHelp(Player player) {
        Text.send(player, "<gray>Du bist in keinem Clan. Befehle:");
        player.sendMessage(Text.c("<yellow>/clan create <name> <gray>- Clan gründen (1-7 Zeichen)"));
        player.sendMessage(Text.c("<yellow>/clan accept <gray>- Einladung annehmen"));
        player.sendMessage(Text.c("<gray>In einem Clan öffnet <yellow>/clan <gray>das Clan-Menü."));
    }

    private void create(Player player, String[] args) {
        ClanManager clans = plugin.clans();
        if (clans.getClan(player.getUniqueId()) != null) {
            Text.send(player, "<red>Du bist bereits in einem Clan.");
            return;
        }
        if (args.length < 2) {
            Text.send(player, "<red>Benutzung: <yellow>/clan create <name>");
            return;
        }
        String raw = args[1];
        String error = clans.validateName(raw, null);
        if (error != null) {
            Text.send(player, "<red>" + error);
            return;
        }
        Clan clan = clans.create(player, raw);
        Text.send(player, "<green>Du hast den Clan <clan> <green>gegründet! Öffne das Menü mit <yellow>/clan<green>.",
                Placeholder.component("clan", clan.tag()));
    }

    private void invite(Player player, String[] args) {
        ClanManager clans = plugin.clans();
        Clan clan = clans.getClan(player.getUniqueId());
        if (clan == null) {
            Text.send(player, "<red>Du bist in keinem Clan.");
            return;
        }
        if (!clan.can(player.getUniqueId(), ClanPermission.MANAGE_MEMBERS)) {
            Text.send(player, "<red>Du darfst keine Spieler einladen.");
            return;
        }
        if (args.length < 2) {
            Text.send(player, "<red>Benutzung: <yellow>/clan invite <spieler>");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            Text.send(player, "<red>Dieser Spieler ist nicht online.");
            return;
        }
        if (clans.getClan(target.getUniqueId()) != null) {
            Text.send(player, "<red>Dieser Spieler ist bereits in einem Clan.");
            return;
        }
        if (clan.members().size() >= clans.maxMembers()) {
            Text.send(player, "<red>Dein Clan ist voll.");
            return;
        }
        clans.invite(clan, target);
        Text.send(player, "<green>Einladung an <yellow><player> <green>gesendet.",
                Placeholder.unparsed("player", target.getName()));
        Text.send(target, "<yellow><player> <gray>hat dich in den Clan <clan> <gray>eingeladen. "
                        + "<click:run_command:'/clan accept " + clan.plainName() + "'>"
                        + "<green><bold>[Annehmen]</bold></green></click>",
                Placeholder.unparsed("player", player.getName()),
                Placeholder.component("clan", clan.tag()));
    }

    private void accept(Player player, String[] args) {
        ClanManager clans = plugin.clans();
        if (clans.getClan(player.getUniqueId()) != null) {
            Text.send(player, "<red>Du bist bereits in einem Clan.");
            return;
        }
        List<Clan> invites = clans.activeInvites(player.getUniqueId());
        if (invites.isEmpty()) {
            Text.send(player, "<red>Du hast keine offenen Einladungen.");
            return;
        }
        Clan chosen = null;
        if (args.length >= 2) {
            for (Clan clan : invites) {
                if (clan.plainName().equalsIgnoreCase(args[1])) {
                    chosen = clan;
                }
            }
            if (chosen == null) {
                Text.send(player, "<red>Von diesem Clan hast du keine Einladung.");
                return;
            }
        } else if (invites.size() == 1) {
            chosen = invites.get(0);
        } else {
            Text.send(player, "<gray>Du hast mehrere Einladungen. Nutze <yellow>/clan accept <name><gray>:");
            for (Clan clan : invites) {
                player.sendMessage(Text.c("<dark_gray> - <clan>", Placeholder.component("clan", clan.tag())));
            }
            return;
        }
        if (chosen.members().size() >= clans.maxMembers()) {
            Text.send(player, "<red>Dieser Clan ist inzwischen voll.");
            return;
        }
        clans.addMember(chosen, player);
        Text.send(player, "<green>Du bist dem Clan <clan> <green>beigetreten!",
                Placeholder.component("clan", chosen.tag()));
        clans.broadcast(chosen, "<yellow><player> <gray>ist dem Clan beigetreten.",
                Placeholder.unparsed("player", player.getName()));
    }

    private void delete(Player player, String[] args) {
        ClanManager clans = plugin.clans();
        Clan clan = clans.getClan(player.getUniqueId());
        if (clan == null) {
            Text.send(player, "<red>Du bist in keinem Clan.");
            return;
        }
        if (!clan.isOwner(player.getUniqueId())) {
            Text.send(player, "<red>Nur der Owner kann den Clan löschen.");
            return;
        }
        if (args.length < 2 || !args[1].equalsIgnoreCase("confirm")) {
            Text.send(player, "<red>Willst du den Clan wirklich löschen? Das kann nicht rückgängig gemacht werden. "
                    + "<click:run_command:'/clan delete confirm'><yellow><bold>[Ja, löschen]</bold></yellow></click>");
            return;
        }
        clans.delete(clan, player);
        Text.send(player, "<green>Dein Clan wurde gelöscht. Der Inhalt der Ender Chest liegt in deinem Inventar.");
    }

    private void leave(Player player) {
        ClanManager clans = plugin.clans();
        Clan clan = clans.getClan(player.getUniqueId());
        if (clan == null) {
            Text.send(player, "<red>Du bist in keinem Clan.");
            return;
        }
        if (clan.isOwner(player.getUniqueId())) {
            Text.send(player, "<red>Als Owner kannst du den Clan nicht verlassen. Nutze <yellow>/clan delete<red>.");
            return;
        }
        clans.removeMember(clan, player.getUniqueId());
        Text.send(player, "<green>Du hast den Clan <clan> <green>verlassen.",
                Placeholder.component("clan", clan.tag()));
        clans.broadcast(clan, "<yellow><player> <gray>hat den Clan verlassen.",
                Placeholder.unparsed("player", player.getName()));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> result = new ArrayList<>();
        if (!(sender instanceof Player player)) {
            return result;
        }
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            for (String sub : SUBCOMMANDS) {
                if (sub.startsWith(prefix)) {
                    result.add(sub);
                }
            }
        } else if (args.length == 2) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "invite" -> {
                    for (Player online : Bukkit.getOnlinePlayers()) {
                        if (online.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                            result.add(online.getName());
                        }
                    }
                }
                case "accept" -> {
                    UUID id = player.getUniqueId();
                    for (Clan clan : plugin.clans().activeInvites(id)) {
                        if (clan.plainName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                            result.add(clan.plainName());
                        }
                    }
                }
                case "delete" -> {
                    if ("confirm".startsWith(prefix)) {
                        result.add("confirm");
                    }
                }
                default -> {
                }
            }
        }
        return result;
    }
}
