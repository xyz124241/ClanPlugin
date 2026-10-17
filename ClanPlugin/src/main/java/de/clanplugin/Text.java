package de.clanplugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;

/** Hilfsfunktionen fuer MiniMessage. */
public final class Text {

    /** Voller MiniMessage-Parser fuer Plugin-eigene Nachrichten (vertrauenswuerdig). */
    public static final MiniMessage FULL = MiniMessage.miniMessage();

    /**
     * Eingeschraenkter Parser fuer Clan-Namen, die Spieler eingeben:
     * nur Farben, Verlaeufe, Regenbogen und Textstile - keine Klick-/Hover-Events.
     */
    public static final MiniMessage TAG = MiniMessage.builder()
            .tags(TagResolver.resolver(
                    StandardTags.color(),
                    StandardTags.decorations(),
                    StandardTags.gradient(),
                    StandardTags.rainbow(),
                    StandardTags.reset()))
            .build();

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private static final Component PREFIX = FULL.deserialize("<dark_gray>[<gold>Clan</gold>]</dark_gray> ");

    private Text() {
    }

    /** MiniMessage-String (vertrauenswuerdig) in Component umwandeln. */
    public static Component c(String mini, TagResolver... resolvers) {
        return FULL.deserialize(mini, resolvers);
    }

    /** Clan-Name (rohes MiniMessage) als farbige Component. */
    public static Component tag(String raw) {
        return TAG.deserialize(raw);
    }

    /** Klarname ohne Farben. */
    public static String plain(String raw) {
        return PLAIN.serialize(TAG.deserialize(raw));
    }

    /** Nachricht mit [Clan]-Prefix. */
    public static Component msg(String mini, TagResolver... resolvers) {
        return PREFIX.append(FULL.deserialize(mini, resolvers));
    }

    public static void send(CommandSender to, String mini, TagResolver... resolvers) {
        to.sendMessage(msg(mini, resolvers));
    }
}
