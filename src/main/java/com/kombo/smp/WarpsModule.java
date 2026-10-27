package com.kombo.smp;

import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.regex.Pattern;

/** Modul "warps": öffentliche Orte, Spawn und Admin-Verwaltung. */
public final class WarpsModule extends SmpModule {

    private static final Pattern NAME = Pattern.compile("[a-z0-9_-]{1,16}");
    private static final String SPAWN = "spawn";
    private LocationStore warps;

    public WarpsModule(KomboSMP plugin) {
        super(plugin, "warps", "warp", "setwarp", "delwarp", "spawn", "setspawn");
    }

    @Override
    protected void onEnable() {
        warps = new LocationStore(plugin, plugin.dataFile("warps.yml"));
    }

    @Override
    public void save() {
        if (warps != null) warps.save();
    }

    @Override
    public boolean execute(CommandSender s, Command cmd, String[] a) {
        switch (lower(cmd.getName())) {
            case "warp" -> {
                Player p = player(s);
                if (p == null) return true;
                if (a.length != 1) {
                    Ui.send(p, "<red>Benutzung: /warp <name>");
                    return true;
                }
                teleport(p, lower(a[0]));
            }
            case "spawn" -> {
                Player p = player(s);
                if (p != null) teleport(p, SPAWN);
            }
            case "setwarp" -> {
                if (!admin(s)) return true;
                Player p = player(s);
                if (p == null) return true;
                if (a.length != 1 || !NAME.matcher(lower(a[0])).matches()) {
                    Ui.send(p, "<red>Benutzung: /setwarp <name> (a-z, 0-9, _ und -)");
                    return true;
                }
                warps.set(lower(a[0]), p.getLocation());
                Ui.send(p, "<green>Warp <white>" + Ui.esc(lower(a[0])) + "</white> gesetzt.");
            }
            case "setspawn" -> {
                if (!admin(s)) return true;
                Player p = player(s);
                if (p == null) return true;
                warps.set(SPAWN, p.getLocation());
                Ui.send(p, "<green>Spawn gesetzt.");
            }
            case "delwarp" -> {
                if (!admin(s)) return true;
                if (a.length != 1) {
                    Ui.send(s, "<red>Benutzung: /delwarp <name>");
                    return true;
                }
                String name = lower(a[0]);
                Ui.send(s, warps.remove(name)
                        ? "<green>Warp <white>" + Ui.esc(name) + "</white> gelöscht."
                        : "<red>Warp <white>" + Ui.esc(name) + "</white> existiert nicht.");
            }
            default -> { return false; }
        }
        return true;
    }

    private void teleport(Player p, String name) {
        Location loc = warps.get(name);
        if (loc == null) {
            Ui.send(p, "<red>Der Ort <white>" + Ui.esc(name) + "</white> existiert nicht.");
            return;
        }
        p.teleportAsync(loc);
        Ui.send(p, "<green>Teleportiert zu <white>" + Ui.esc(name) + "</white>.");
    }

    @Override
    public List<String> complete(CommandSender s, Command cmd, String[] a) {
        if (a.length != 1) return List.of();
        String name = lower(cmd.getName());
        if (name.equals("warp") || name.equals("delwarp")) {
            return startsWith(warps.keys(""), a[0]);
        }
        return List.of();
    }
}
