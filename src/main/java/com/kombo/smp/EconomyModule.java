package com.kombo.smp;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/** Modul "economy": Shards anzeigen, überweisen, verwalten und Postfach leeren. */
public final class EconomyModule extends SmpModule {

    public EconomyModule(KomboSMP plugin) {
        super(plugin, "economy", "shards", "pay", "collect");
    }

    @Override
    public boolean execute(CommandSender s, Command cmd, String[] a) {
        switch (lower(cmd.getName())) {
            case "shards" -> shards(s, a);
            case "pay" -> pay(s, a);
            case "collect" -> {
                Player p = player(s);
                if (p != null) plugin.mailbox().collect(p);
            }
            default -> { return false; }
        }
        return true;
    }

    private void shards(CommandSender s, String[] a) {
        Economy eco = plugin.economy();

        if (a.length == 0) {
            Player p = player(s);
            if (p == null) return;
            Ui.send(p, "Dein Kontostand: <yellow>" + plugin.fmt(eco.balance(p.getUniqueId())));
            return;
        }

        if (a.length == 1) {
            OfflinePlayer t = Bukkit.getOfflinePlayerIfCached(a[0]);
            if (t == null) {
                Ui.send(s, "<red>Spieler nicht gefunden.");
                return;
            }
            Ui.send(s, "<white>" + Ui.esc(a[0]) + "</white> hat <yellow>" + plugin.fmt(eco.balance(t.getUniqueId())));
            return;
        }

        if (!admin(s)) return;
        if (a.length != 3) {
            Ui.send(s, "<red>Benutzung: /shards <give|take|set> <spieler> <betrag>");
            return;
        }
        OfflinePlayer t = Bukkit.getOfflinePlayerIfCached(a[1]);
        Long amount = parsePositive(a[2]);
        if (t == null) {
            Ui.send(s, "<red>Spieler nicht gefunden.");
            return;
        }
        if (amount == null) {
            Ui.send(s, "<red>Ungültiger Betrag.");
            return;
        }
        switch (lower(a[0])) {
            case "give" -> eco.add(t.getUniqueId(), amount);
            case "take" -> {
                if (!eco.remove(t.getUniqueId(), amount)) {
                    Ui.send(s, "<red>Der Spieler hat nicht genug Shards.");
                    return;
                }
            }
            case "set" -> eco.set(t.getUniqueId(), amount);
            default -> {
                Ui.send(s, "<red>Unbekannte Aktion. Nutze give, take oder set.");
                return;
            }
        }
        Ui.send(s, "<green>Neuer Kontostand von <white>" + Ui.esc(a[1]) + "</white>: <yellow>"
                + plugin.fmt(eco.balance(t.getUniqueId())));
    }

    private void pay(CommandSender s, String[] a) {
        Player p = player(s);
        if (p == null) return;
        if (a.length != 2) {
            Ui.send(p, "<red>Benutzung: /pay <spieler> <betrag>");
            return;
        }
        Player target = Bukkit.getPlayerExact(a[0]);
        if (target == null) {
            Ui.send(p, "<red>Dieser Spieler ist nicht online.");
            return;
        }
        if (target.equals(p)) {
            Ui.send(p, "<red>Du kannst dir selbst nichts senden.");
            return;
        }
        Long amount = parsePositive(a[1]);
        if (amount == null) {
            Ui.send(p, "<red>Ungültiger Betrag.");
            return;
        }
        if (!plugin.economy().remove(p.getUniqueId(), amount)) {
            Ui.send(p, "<red>Du hast nicht genug Shards.");
            return;
        }
        plugin.economy().add(target.getUniqueId(), amount);
        Ui.send(p, "Du hast <yellow>" + plugin.fmt(amount) + "</yellow> an <white>" + Ui.esc(target.getName()) + "</white> gesendet.");
        Ui.send(target, "Du hast <yellow>" + plugin.fmt(amount) + "</yellow> von <white>" + Ui.esc(p.getName()) + "</white> erhalten.");
    }

    @Override
    public List<String> complete(CommandSender s, Command cmd, String[] a) {
        if (a.length != 1) return List.of();
        String name = lower(cmd.getName());
        List<String> options = Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        if (name.equals("shards") && s.hasPermission("smp.admin")) {
            options = Stream.concat(options.stream(), Stream.of("give", "take", "set")).toList();
        }
        return startsWith(options, a[0]);
    }
}
