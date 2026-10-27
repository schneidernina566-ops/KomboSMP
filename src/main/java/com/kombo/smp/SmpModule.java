package com.kombo.smp;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;

import java.util.List;
import java.util.Locale;

/**
 * Basis für alle Feature-Module. Jedes Modul hat:
 *  - einen Namen (an/aus in config.yml unter "modules")
 *  - eine eigene Config: plugins/KomboSMP/modules/<name>.yml
 *  - eigene Befehle und Listener
 */
public abstract class SmpModule implements Listener {

    protected final KomboSMP plugin;
    private final String name;
    private final String[] commands;
    private YamlConfiguration config = new YamlConfiguration();
    private boolean enabled;

    protected SmpModule(KomboSMP plugin, String name, String... commands) {
        this.plugin = plugin;
        this.name = name;
        this.commands = commands;
    }

    public String name() { return name; }
    public String[] commands() { return commands; }
    public boolean isEnabled() { return enabled; }

    /** Eigene Konfiguration dieses Moduls. */
    protected YamlConfiguration config() { return config; }

    final void start() {
        config = plugin.loadModuleConfig(name);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        enabled = true;
        onEnable();
    }

    final void stop() {
        if (!enabled) return;
        try {
            onDisable();
        } finally {
            HandlerList.unregisterAll(this);
            enabled = false;
        }
    }

    /** Beim Start und nach /admin -> Reload. */
    protected void onEnable() {}

    /** Beim Stoppen. Laufende Tasks hier abbrechen. */
    protected void onDisable() {}

    /** Speichert die Daten des Moduls. */
    public void save() {}

    /** Befehle dieses Moduls. */
    public boolean execute(CommandSender s, Command cmd, String[] args) { return false; }

    public List<String> complete(CommandSender s, Command cmd, String[] args) { return List.of(); }

    /* ---------- Hilfsmethoden ---------- */

    protected Player player(CommandSender s) {
        if (s instanceof Player p) return p;
        Ui.send(s, "<red>Dieser Befehl ist nur für Spieler.");
        return null;
    }

    protected boolean admin(CommandSender s) {
        if (s.hasPermission("smp.admin")) return true;
        Ui.send(s, "<red>Dafür hast du keine Berechtigung.");
        return false;
    }

    protected static Long parsePositive(String raw) {
        try {
            long v = Long.parseLong(raw);
            return v > 0 ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    protected static long num(Object o) {
        return o instanceof Number n ? n.longValue() : 0L;
    }

    protected static List<String> startsWith(List<String> options, String input) {
        String in = input.toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(in)).toList();
    }

    protected static String lower(String s) {
        return s.toLowerCase(Locale.ROOT);
    }
}
