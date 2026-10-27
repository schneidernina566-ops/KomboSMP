package com.kombo.smp;

import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Modul "homes": persönliche Teleportpunkte mit Limit aus modules/homes.yml. */
public final class HomesModule extends SmpModule {

    private static final Pattern NAME = Pattern.compile("[a-z0-9_-]{1,16}");
    private LocationStore homes;

    public HomesModule(KomboSMP plugin) {
        super(plugin, "homes", "sethome", "home", "delhome", "homes");
    }

    @Override
    protected void onEnable() {
        homes = new LocationStore(plugin, plugin.dataFile("homes.yml"));
    }

    @Override
    public void save() {
        if (homes != null) homes.save();
    }

    @Override
    public boolean execute(CommandSender s, Command cmd, String[] a) {
        Player p = player(s);
        if (p == null) return true;
        switch (lower(cmd.getName())) {
            case "sethome" -> set(p, a);
            case "home" -> go(p, a);
            case "delhome" -> del(p, a);
            case "homes" -> list(p);
            default -> { return false; }
        }
        return true;
    }

    private static String prefix(Player p) {
        return p.getUniqueId() + ":";
    }

    private void set(Player p, String[] a) {
        String name = a.length > 0 ? lower(a[0]) : "home";
        if (!NAME.matcher(name).matches()) {
            Ui.send(p, "<red>Ungültiger Name (a-z, 0-9, _ und -, max. 16 Zeichen).");
            return;
        }
        String key = prefix(p) + name;
        if (homes.get(key) == null) {
            int max = config().getInt("max", 3);
            if (homes.keys(prefix(p)).size() >= max) {
                Ui.send(p, "<red>Limit von " + max + " Homes erreicht. Lösche eins mit /delhome.");
                return;
            }
        }
        homes.set(key, p.getLocation());
        Ui.send(p, "<green>Home <white>" + name + "</white> gesetzt.");
    }

    private void go(Player p, String[] a) {
        String name = a.length > 0 ? lower(a[0]) : "home";
        Location loc = homes.get(prefix(p) + name);
        if (loc == null) {
            Ui.send(p, "<red>Home <white>" + Ui.esc(name) + "</white> existiert nicht.");
            return;
        }
        p.teleportAsync(loc);
        Ui.send(p, "<green>Teleportiert zu <white>" + Ui.esc(name) + "</white>.");
    }

    private void del(Player p, String[] a) {
        if (a.length != 1) {
            Ui.send(p, "<red>Benutzung: /delhome <name>");
            return;
        }
        String name = lower(a[0]);
        if (homes.remove(prefix(p) + name)) {
            Ui.send(p, "<green>Home <white>" + Ui.esc(name) + "</white> gelöscht.");
        } else {
            Ui.send(p, "<red>Home <white>" + Ui.esc(name) + "</white> existiert nicht.");
        }
    }

    private void list(Player p) {
        String pre = prefix(p);
        List<String> names = homes.keys(pre).stream().map(k -> k.substring(pre.length())).toList();
        if (names.isEmpty()) {
            Ui.send(p, "<gray>Du hast noch keine Homes. Setze eins mit /sethome.");
        } else {
            Ui.send(p, "<gray>Deine Homes: <white>" + String.join(", ", names));
        }
    }

    @Override
    public List<String> complete(CommandSender s, Command cmd, String[] a) {
        if (a.length != 1 || !(s instanceof Player p)) return List.of();
        if (!List.of("home", "delhome").contains(lower(cmd.getName()))) return List.of();
        String pre = prefix(p);
        return startsWith(homes.keys(pre).stream().map(k -> k.substring(pre.length())).toList(), a[0]);
    }
}
