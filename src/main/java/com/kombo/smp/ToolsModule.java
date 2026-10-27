package com.kombo.smp;

import org.bukkit.attribute.Attribute;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Modul "tools": Hilfsbefehle heal, feed, fly, ec. Kosten und Cooldowns in modules/tools.yml. */
public final class ToolsModule extends SmpModule {

    private final Map<String, Map<UUID, Long>> cooldowns = new HashMap<>();

    public ToolsModule(KomboSMP plugin) {
        super(plugin, "tools", "heal", "feed", "fly", "ec");
    }

    @Override
    protected void onEnable() {
        cooldowns.clear();
    }

    @Override
    public boolean execute(CommandSender s, Command cmd, String[] a) {
        Player p = player(s);
        if (p == null) return true;
        String name = lower(cmd.getName());

        if (!name.equals("fly") && !check(p, name)) return true;

        switch (name) {
            case "heal" -> {
                var attr = p.getAttribute(Attribute.MAX_HEALTH);
                p.setHealth(attr != null ? attr.getValue() : 20.0);
                Ui.send(p, "<green>Geheilt.");
            }
            case "feed" -> {
                p.setFoodLevel(20);
                p.setSaturation(20f);
                Ui.send(p, "<green>Satt.");
            }
            case "fly" -> {
                if (!p.getAllowFlight() && !check(p, name)) return true;
                boolean now = !p.getAllowFlight();
                p.setAllowFlight(now);
                if (!now) p.setFlying(false);
                Ui.send(p, now ? "<green>Fliegen aktiviert." : "<yellow>Fliegen deaktiviert.");
            }
            case "ec" -> p.openInventory(p.getEnderChest());
            default -> { return false; }
        }
        return true;
    }

    /** Prüft Cooldown und Kosten. Bucht die Kosten ab und setzt den Cooldown, wenn alles passt. */
    private boolean check(Player p, String name) {
        UUID id = p.getUniqueId();
        long cooldownSec = config().getLong("cooldowns." + name, 0);
        long now = System.currentTimeMillis();
        long until = cooldowns.computeIfAbsent(name, k -> new HashMap<>()).getOrDefault(id, 0L);
        if (now < until) {
            Ui.send(p, "<red>Warte noch <white>" + ((until - now + 999) / 1000) + "</white> Sekunden.");
            return false;
        }
        long cost = config().getLong("costs." + name, 0);
        if (cost > 0 && !plugin.economy().remove(id, cost)) {
            Ui.send(p, "<red>Du brauchst <yellow>" + plugin.fmt(cost) + "</yellow> dafür.");
            return false;
        }
        if (cooldownSec > 0) {
            cooldowns.get(name).put(id, now + cooldownSec * 1000L);
        }
        return true;
    }
}
