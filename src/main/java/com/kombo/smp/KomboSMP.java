package com.kombo.smp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * KomboSMP - Hauptklasse. Verwaltet die Module und leitet Befehle an das richtige Modul weiter.
 */
public final class KomboSMP extends JavaPlugin implements CommandExecutor, TabCompleter {

    private Economy economy;
    private Mailbox mailbox;
    private final Map<String, SmpModule> modules = new LinkedHashMap<>();
    private final Map<String, SmpModule> commandOwners = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        economy = new Economy(this);
        mailbox = new Mailbox(this);

        register(new EconomyModule(this));
        register(new ShopModule(this));
        register(new EarningModule(this));
        register(new HomesModule(this));
        register(new WarpsModule(this));
        register(new RtpModule(this));
        register(new RulesModule(this));
        register(new AuctionModule(this));
        register(new OrdersModule(this));
        register(new BaltopModule(this));
        register(new CratesModule(this));
        register(new ToolsModule(this));
        register(new AdminModule(this));

        for (String cmd : commandOwners.keySet()) {
            PluginCommand pc = getCommand(cmd);
            if (pc == null) {
                getLogger().warning("Befehl fehlt in plugin.yml: " + cmd);
            } else {
                pc.setExecutor(this);
                pc.setTabCompleter(this);
            }
        }

        startModules();
        getServer().getScheduler().runTaskTimer(this, this::saveAll, 6000L, 6000L);
        getLogger().info("KomboSMP aktiviert.");
    }

    @Override
    public void onDisable() {
        saveAll();
        for (SmpModule m : modules.values()) m.stop();
    }

    private void register(SmpModule module) {
        modules.put(module.name(), module);
        for (String cmd : module.commands()) commandOwners.put(cmd, module);
    }

    private void startModules() {
        for (SmpModule m : modules.values()) {
            if (getConfig().getBoolean("modules." + m.name(), true)) m.start();
        }
    }

    /** Speichert alles, stoppt alle Module, lädt die Config neu und startet die aktiven Module. */
    public void reloadAll() {
        saveAll();
        for (SmpModule m : modules.values()) m.stop();
        reloadConfig();
        startModules();
    }

    public void saveAll() {
        economy.save();
        mailbox.save();
        for (SmpModule m : modules.values()) {
            if (m.isEnabled()) m.save();
        }
    }

    /* ---------- Befehls-Router ---------- */

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] args) {
        SmpModule owner = commandOwners.get(cmd.getName().toLowerCase(Locale.ROOT));
        if (owner == null) return false;
        if (!owner.isEnabled()) {
            Ui.send(s, "<red>Dieses Feature ist deaktiviert.");
            return true;
        }
        return owner.execute(s, cmd, args);
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] args) {
        SmpModule owner = commandOwners.get(cmd.getName().toLowerCase(Locale.ROOT));
        if (owner == null || !owner.isEnabled()) return List.of();
        return owner.complete(s, cmd, args);
    }

    /* ---------- Zugriff für Module ---------- */

    public Economy economy() { return economy; }
    public Mailbox mailbox() { return mailbox; }
    public Set<String> moduleNames() { return modules.keySet(); }

    public boolean isModuleEnabled(String name) {
        SmpModule m = modules.get(name);
        return m != null && m.isEnabled();
    }

    /** Lädt modules/<name>.yml. Fehlt die Datei, wird sie aus der JAR kopiert (falls vorhanden). */
    public YamlConfiguration loadModuleConfig(String name) {
        String path = "modules/" + name + ".yml";
        File file = new File(getDataFolder(), path);
        if (!file.exists()) {
            try {
                saveResource(path, false);
            } catch (IllegalArgumentException ignored) {
                return new YamlConfiguration(); // Modul hat keine Config
            }
        }
        return YamlConfiguration.loadConfiguration(file);
    }

    /** Datei im Ordner data/ (für gespeicherte Spieldaten). */
    public File dataFile(String name) {
        File dir = new File(getDataFolder(), "data");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, name);
    }

    public static Component mm(String miniMessage) {
        return MiniMessage.miniMessage().deserialize(miniMessage);
    }

    public String fmt(long amount) {
        return String.format(Locale.GERMANY, "%,d", amount) + " " + getConfig().getString("currency.symbol", "◆");
    }
}
