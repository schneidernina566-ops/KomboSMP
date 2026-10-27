package com.kombo.smp;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Modul "baltop": Rangliste der reichsten Spieler. */
public final class BaltopModule extends SmpModule {

    public BaltopModule(KomboSMP plugin) {
        super(plugin, "baltop", "baltop");
    }

    @Override
    public boolean execute(CommandSender s, Command cmd, String[] a) {
        List<Map.Entry<UUID, Long>> top = plugin.economy().top(10);
        Ui.send(s, "<gold><bold>Top Shards</bold></gold>");
        if (top.isEmpty()) {
            Ui.send(s, "<gray>Noch keine Einträge.");
            return true;
        }
        int rank = 1;
        for (Map.Entry<UUID, Long> e : top) {
            String name = Bukkit.getOfflinePlayer(e.getKey()).getName();
            Ui.send(s, "<gray>" + rank++ + ". <white>" + Ui.esc(name == null ? "Unbekannt" : name)
                    + "</white> <yellow>" + plugin.fmt(e.getValue()));
        }
        return true;
    }
}
