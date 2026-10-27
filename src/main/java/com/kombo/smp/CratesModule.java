package com.kombo.smp;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Modul "crates": Kisten mit gewichteten Belohnungen aus modules/crates.yml.
 * Schlüssel werden mit /key buy gekauft (Shards) oder vom Team mit /key give vergeben.
 * Öffnen über /crates mit Roll-Animation.
 */
public final class CratesModule extends SmpModule {

    private static final Random RNG = new Random();
    private static final int ROLL_SLOT = 13;
    private static final int MENU_START = 10;
    private static final int MENU_MAX = 7;

    private record Reward(int chance, Material material, int amount, String name, List<String> commands) {}
    private record Crate(String id, String display, long keyPrice, int rollSteps, List<Reward> rewards) {}

    private final Map<String, Crate> crates = new LinkedHashMap<>();
    private final Set<UUID> busy = new HashSet<>();
    private NamespacedKey keyTag;

    public CratesModule(KomboSMP plugin) {
        super(plugin, "crates", "crates", "key");
    }

    @Override
    protected void onEnable() {
        keyTag = new NamespacedKey(plugin, "crate");
        crates.clear();
        busy.clear();

        ConfigurationSection root = config().getConfigurationSection("crates");
        if (root == null) return;
        for (String id : root.getKeys(false)) {
            ConfigurationSection c = root.getConfigurationSection(id);
            if (c == null) continue;

            List<Reward> rewards = new ArrayList<>();
            for (Map<?, ?> raw : c.getMapList("rewards")) {
                int chance = (int) num(raw.get("chance"));
                if (chance <= 0) continue;
                Material mat = raw.get("material") == null ? null : Material.matchMaterial(String.valueOf(raw.get("material")));
                int amount = Math.max(1, (int) num(raw.get("amount")));
                String name = raw.get("name") == null ? null : String.valueOf(raw.get("name"));
                List<String> commands = new ArrayList<>();
                if (raw.get("commands") instanceof List<?> list) {
                    for (Object o : list) commands.add(String.valueOf(o));
                }
                rewards.add(new Reward(chance, mat, amount, name, commands));
            }
            if (rewards.isEmpty()) continue;

            crates.put(id, new Crate(id, c.getString("display", id), c.getLong("key-price", 0),
                    Math.max(5, c.getInt("roll-steps", 20)), rewards));
        }
    }

    /* ---------- Befehle ---------- */

    @Override
    public boolean execute(CommandSender s, Command cmd, String[] a) {
        if (lower(cmd.getName()).equals("crates")) {
            Player p = player(s);
            if (p != null) openMenu(p);
            return true;
        }

        // /key buy <kiste>
        if (a.length == 2 && lower(a[0]).equals("buy")) {
            Player p = player(s);
            if (p == null) return true;
            Crate c = crates.get(lower(a[1]));
            if (c == null) {
                Ui.send(p, "<red>Diese Kiste gibt es nicht.");
                return true;
            }
            if (!plugin.economy().remove(p.getUniqueId(), c.keyPrice())) {
                Ui.send(p, "<red>Du brauchst <yellow>" + plugin.fmt(c.keyPrice()) + "</yellow> für einen Schlüssel.");
                return true;
            }
            plugin.mailbox().giveOrMail(p, keyItem(c));
            Ui.send(p, "<green>Schlüssel für <white>" + c.id() + "</white> gekauft.");
            return true;
        }

        // /key give <spieler> <kiste> <menge>
        if (a.length == 4 && lower(a[0]).equals("give")) {
            if (!admin(s)) return true;
            Player target = Bukkit.getPlayerExact(a[1]);
            Crate c = crates.get(lower(a[2]));
            Long amount = parsePositive(a[3]);
            if (target == null || c == null || amount == null || amount > 64) {
                Ui.send(s, "<red>Ungültige Eingabe (Spieler online? Kiste vorhanden? Menge 1-64?).");
                return true;
            }
            for (long i = 0; i < amount; i++) plugin.mailbox().giveOrMail(target, keyItem(c));
            Ui.send(s, "<green>" + amount + " Schlüssel für " + c.id() + " an " + Ui.esc(target.getName()) + " gegeben.");
            return true;
        }

        Ui.send(s, "<red>Benutzung: /key buy <kiste> | /key give <spieler> <kiste> <menge>");
        return true;
    }

    @Override
    public List<String> complete(CommandSender s, Command cmd, String[] a) {
        if (lower(cmd.getName()).equals("key")) {
            if (a.length == 1) return startsWith(List.of("buy", "give"), a[0]);
            if (a.length == 2 && lower(a[0]).equals("buy")) return startsWith(new ArrayList<>(crates.keySet()), a[1]);
        }
        return List.of();
    }

    /* ---------- Schlüssel ---------- */

    private ItemStack keyItem(Crate c) {
        ItemStack stack = Ui.icon(Material.TRIPWIRE_HOOK, c.display() + " <gray>Schlüssel",
                "<gray>Öffnet die Kiste im /crates-Menü");
        ItemMeta meta = stack.getItemMeta();
        meta.getPersistentDataContainer().set(keyTag, PersistentDataType.STRING, c.id());
        stack.setItemMeta(meta);
        return stack;
    }

    private boolean isKey(ItemStack s, String crateId) {
        if (s == null || !s.hasItemMeta()) return false;
        return crateId.equals(s.getItemMeta().getPersistentDataContainer().get(keyTag, PersistentDataType.STRING));
    }

    private int countKeys(Player p, Crate c) {
        int total = 0;
        for (ItemStack s : p.getInventory().getStorageContents()) {
            if (isKey(s, c.id())) total += s.getAmount();
        }
        return total;
    }

    private boolean takeKey(Player p, Crate c) {
        ItemStack[] contents = p.getInventory().getStorageContents();
        for (int i = 0; i < contents.length; i++) {
            if (isKey(contents[i], c.id())) {
                if (contents[i].getAmount() > 1) contents[i].setAmount(contents[i].getAmount() - 1);
                else contents[i] = null;
                p.getInventory().setStorageContents(contents);
                return true;
            }
        }
        return false;
    }

    /* ---------- Menü ---------- */

    private static final class MenuHolder implements InventoryHolder {
        Inventory inventory;

        @Override
        public Inventory getInventory() { return inventory; }
    }

    private static final class RollHolder implements InventoryHolder {
        Inventory inventory;

        @Override
        public Inventory getInventory() { return inventory; }
    }

    private void openMenu(Player p) {
        MenuHolder holder = new MenuHolder();
        holder.inventory = Bukkit.createInventory(holder, 27, KomboSMP.mm("<gold><bold>Kisten</bold></gold>"));
        int i = 0;
        for (Crate c : crates.values()) {
            if (i >= MENU_MAX) break;
            holder.inventory.setItem(MENU_START + i, Ui.icon(Material.CHEST, c.display(),
                    "<gray>Deine Schlüssel: <white>" + countKeys(p, c),
                    "<gray>Schlüssel kaufen: <white>/key buy " + c.id(),
                    "<green>Klick: Kiste öffnen"));
            i++;
        }
        p.openInventory(holder.inventory);
    }

    /* ---------- Öffnen mit Roll-Animation ---------- */

    private void startRoll(Player p, Crate crate) {
        UUID id = p.getUniqueId();
        if (busy.contains(id)) {
            Ui.send(p, "<gray>Deine Kiste öffnet sich schon.");
            return;
        }
        if (!takeKey(p, crate)) {
            Ui.send(p, "<red>Dir fehlt ein Schlüssel. Kaufe einen mit <white>/key buy " + crate.id() + "</white>.");
            return;
        }
        busy.add(id);

        Reward result = pick(crate.rewards());
        List<Reward> pool = crate.rewards();

        RollHolder holder = new RollHolder();
        holder.inventory = Bukkit.createInventory(holder, 27, KomboSMP.mm(crate.display()));
        ItemStack filler = Ui.icon(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) holder.inventory.setItem(i, filler);
        p.openInventory(holder.inventory);

        new BukkitRunnable() {
            int step = 0;

            @Override
            public void run() {
                step++;
                if (step < crate.rollSteps()) {
                    holder.inventory.setItem(ROLL_SLOT, rewardIcon(pool.get(RNG.nextInt(pool.size()))));
                    return;
                }
                holder.inventory.setItem(ROLL_SLOT, rewardIcon(result));
                grant(p, result);
                busy.remove(id);
                cancel();
            }
        }.runTaskTimer(plugin, 2L, 3L);
    }

    private static Reward pick(List<Reward> rewards) {
        int total = rewards.stream().mapToInt(Reward::chance).sum();
        int roll = RNG.nextInt(total);
        for (Reward r : rewards) {
            roll -= r.chance();
            if (roll < 0) return r;
        }
        return rewards.get(rewards.size() - 1);
    }

    private static ItemStack rewardIcon(Reward r) {
        return Ui.icon(r.material() != null ? r.material() : Material.NETHER_STAR,
                r.name() != null ? r.name() : "<white>Belohnung");
    }

    private void grant(Player p, Reward r) {
        if (r.material() != null) {
            ItemStack stack = new ItemStack(r.material(), r.amount());
            if (r.name() != null) {
                ItemMeta meta = stack.getItemMeta();
                meta.displayName(KomboSMP.mm(r.name()));
                stack.setItemMeta(meta);
            }
            if (p.isOnline()) plugin.mailbox().giveOrMail(p, stack);
            else plugin.mailbox().deliver(p.getUniqueId(), stack);
        }
        for (String cmd : r.commands()) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("{player}", p.getName()));
        }
        if (p.isOnline()) Ui.send(p, "<green>Belohnung erhalten!");
    }

    /* ---------- Events ---------- */

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        InventoryHolder holder = e.getView().getTopInventory().getHolder();
        if (holder instanceof RollHolder) {
            e.setCancelled(true);
            return;
        }
        if (!(holder instanceof MenuHolder)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() == null || !e.getClickedInventory().equals(e.getView().getTopInventory())) return;

        int idx = e.getSlot() - MENU_START;
        List<Crate> list = new ArrayList<>(crates.values());
        if (idx < 0 || idx >= list.size() || idx >= MENU_MAX) return;
        startRoll(p, list.get(idx));
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        InventoryHolder holder = e.getView().getTopInventory().getHolder();
        if (holder instanceof MenuHolder || holder instanceof RollHolder) e.setCancelled(true);
    }
}
