package com.kombo.smp;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import java.util.List;

/** Modul "rules": zeigt die Regeln aus modules/rules.yml. */
public final class RulesModule extends SmpModule {

    public RulesModule(KomboSMP plugin) {
        super(plugin, "rules", "rules");
    }

    @Override
    public boolean execute(CommandSender s, Command cmd, String[] a) {
        List<String> rules = config().getStringList("rules");
        Ui.send(s, "<gold><bold>Serverregeln</bold></gold>");
        if (rules.isEmpty()) {
            Ui.send(s, "<gray>Keine Regeln konfiguriert.");
            return true;
        }
        for (int i = 0; i < rules.size(); i++) {
            Ui.send(s, (i + 1) + ". " + rules.get(i));
        }
        return true;
    }
}
