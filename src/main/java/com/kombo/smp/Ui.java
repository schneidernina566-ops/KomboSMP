package com.kombo.smp;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;

/** Hilfsfunktionen für Nachrichten und GUI-Items. */
public final class Ui {

    private Ui() {}

    public static void send(CommandSender to, String miniMessage) {
        to.sendMessage(KomboSMP.mm(miniMessage));
    }

    public static ItemStack icon(Material material, String name, String... lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(KomboSMP.mm(name));
        meta.lore(Arrays.stream(lore).map(KomboSMP::mm).toList());
        stack.setItemMeta(meta);
        return stack;
    }

    public static String pretty(Material m) {
        return m.name().replace('_', ' ').toLowerCase();
    }

    /** Verhindert, dass Benutzereingaben als MiniMessage-Tags interpretiert werden. */
    public static String esc(String text) {
        return MiniMessage.miniMessage().escapeTags(text == null ? "" : text);
    }
}
