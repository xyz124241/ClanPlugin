package de.clanplugin;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.event.EventSubscription;
import net.luckperms.api.event.user.UserDataRecalculateEvent;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.function.Consumer;

/** Duenne Anbindung an die LuckPerms-API (wird nur geladen, wenn LuckPerms vorhanden ist). */
public final class LuckPermsHook {

    private final LuckPerms luckPerms;
    private final EventSubscription<UserDataRecalculateEvent> subscription;

    /** @param onChange wird (ggf. asynchron) aufgerufen, wenn sich Daten eines Spielers aendern */
    public LuckPermsHook(ClanPlugin plugin, Consumer<UUID> onChange) {
        this.luckPerms = LuckPermsProvider.get();
        this.subscription = luckPerms.getEventBus().subscribe(plugin, UserDataRecalculateEvent.class,
                event -> onChange.accept(event.getUser().getUniqueId()));
    }

    /** Rohes Prefix des Spielers (z.B. "&c[Admin]") oder leerer String. */
    public String prefix(Player player) {
        User user = luckPerms.getPlayerAdapter(Player.class).getUser(player);
        String prefix = user.getCachedData().getMetaData().getPrefix();
        return prefix == null ? "" : prefix;
    }

    /** Gewicht der Primaergruppe (fuer die Sortierung in der Tab-Liste). */
    public int weight(Player player) {
        User user = luckPerms.getPlayerAdapter(Player.class).getUser(player);
        Group group = luckPerms.getGroupManager().getGroup(user.getPrimaryGroup());
        if (group == null) {
            return 0;
        }
        return group.getWeight().orElse(0);
    }

    public void close() {
        subscription.close();
    }
}
