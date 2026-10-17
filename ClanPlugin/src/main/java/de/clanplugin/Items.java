package de.clanplugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Kleine Helfer zum Bauen von Menue-Items. */
public final class Items {

    private Items() {
    }

    private static Component plain(Component component) {
        return Component.empty().decoration(TextDecoration.ITALIC, false).append(component);
    }

    public static ItemStack of(Material material, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(plain(name));
        List<Component> lines = new ArrayList<>();
        for (Component line : lore) {
            lines.add(plain(line));
        }
        meta.lore(lines);
        item.setItemMeta(meta);
        return item;
    }

    /** Item aus reinen MiniMessage-Strings (nur fuer vertrauenswuerdige Texte). */
    public static ItemStack simple(Material material, String name, String... lore) {
        List<Component> lines = new ArrayList<>();
        for (String line : lore) {
            lines.add(Text.c(line));
        }
        return of(material, Text.c(name), lines);
    }

    public static ItemStack head(UUID owner, Component name, List<Component> lore) {
        ItemStack item = of(Material.PLAYER_HEAD, name, lore);
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof SkullMeta skull) {
            skull.setOwningPlayer(Bukkit.getOfflinePlayer(owner));
            item.setItemMeta(skull);
        }
        return item;
    }

    public static ItemStack filler() {
        return of(Material.GRAY_STAINED_GLASS_PANE, Component.text(" "), List.of());
    }
}
