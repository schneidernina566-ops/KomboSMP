package com.kombo.smp;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.ArrayList;
import java.util.List;

/**
 * Modul "admin": Admin-Panel mit Reload, Speichern, Statistik und
 * An/Aus-Schaltern für jedes Modul.
 */
public final class AdminModule extends SmpModule {

    private static final int SLOT_RELOAD = 10;
    private static final int SLOT_SAVE = 12;
    private static final int SLOT_STATS = 14;
    private static final int TOGGLE_START = 27;
    private static final String PROTECTED = "admin";

    public AdminModule(KomboSMP plugin) {
        super(plugin, "admin", "admin");
    }

    private static final class Holder implements InventoryHolder {
        final List<String> moduleNames = new ArrayList<>();
        Inventory inventory;

        @Override
        public Inventory getInventory() { return inventory; }
    }

    @Override
    public boolean execute(CommandSender s, Command cmd, String[] a) {
        Player p = player(s);
        if (p == null) return true;
        if (!admin(p)) return true;
        open(p);
        return true;
    }

    private void open(Player p) {
        Holder holder = new Holder();
        holder.inventory = Bukkit.createInventory(holder, 54, KomboSMP.mm("<red><bold>Admin-Panel</bold></red>"));

        holder.inventory.setItem(SLOT_RELOAD, Ui.icon(Material.COMPARATOR, "<yellow>Reload",
                "<gray>Speichert, lädt config.yml und alle Module neu"));
        holder.inventory.setItem(SLOT_SAVE, Ui.icon(Material.HOPPER, "<green>Alles speichern"));
        holder.inventory.setItem(SLOT_STATS, Ui.icon(Material.BOOK, "<aqua>Statistik",
                "<gray>Online: <white>" + Bukkit.getOnlinePlayers().size(),
                "<gray>Aktive Module: <white>"
                        + plugin.moduleNames().stream().filter(plugin::isModuleEnabled).count()
                        + " / " + plugin.moduleNames().size()));

        for (String name : plugin.moduleNames()) {
            int slot = TOGGLE_START + holder.moduleNames.size();
            if (slot >= 54) break;
            holder.moduleNames.add(name);
            boolean on = plugin.isModuleEnabled(name);
            holder.inventory.setItem(slot, Ui.icon(on ? Material.LIME_DYE : Material.GRAY_DYE,
                    (on ? "<green>AN  " : "<red>AUS ") + "<white>" + name,
                    "<gray>Klick zum Umschalten",
                    name.equals(PROTECTED) ? "<dark_gray>Dieses Modul kann nicht ausgeschaltet werden." : ""));
        }
        p.openInventory(holder.inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Holder holder)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!p.hasPermission("smp.admin")) {
            Ui.send(p, "<red>Keine Berechtigung.");
            p.closeInventory();
            return;
        }
        if (e.getClickedInventory() == null || !e.getClickedInventory().equals(e.getView().getTopInventory())) return;

        int slot = e.getSlot();
        if (slot == SLOT_RELOAD) {
            plugin.reloadAll();
            Ui.send(p, "<green>Reload abgeschlossen.");
            open(p);
            return;
        }
        if (slot == SLOT_SAVE) {
            plugin.saveAll();
            Ui.send(p, "<green>Alle Daten gespeichert.");
            return;
        }
        int idx = slot - TOGGLE_START;
        if (idx < 0 || idx >= holder.moduleNames.size()) return;

        String name = holder.moduleNames.get(idx);
        if (name.equals(PROTECTED)) {
            Ui.send(p, "<red>Das Admin-Panel bleibt immer an.");
            return;
        }
        boolean nowOn = !plugin.isModuleEnabled(name);
        plugin.getConfig().set("modules." + name, nowOn);
        plugin.saveConfig();
        plugin.reloadAll();
        Ui.send(p, "<gray>Modul <white>" + name + "</white> ist jetzt " + (nowOn ? "<green>AN" : "<red>AUS") + "</green>.");
        open(p);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }
}
