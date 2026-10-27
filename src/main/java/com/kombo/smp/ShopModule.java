package com.kombo.smp;

import org.bukkit.Bukkit;
import org.bukkit.Material;
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
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Modul "shop": Kategorien und Artikel aus shop.yml. Links = 1 kaufen, Shift = 64, Rechts = alles verkaufen. */
public final class ShopModule extends SmpModule {

    private static final int SIZE = 54;
    private static final int BACK_SLOT = 49;

    private record Item(Material material, long buy, long sell) {}
    private record Category(String id, String display, Material icon, List<Item> items) {}

    private final List<Category> categories = new ArrayList<>();

    public ShopModule(KomboSMP plugin) {
        super(plugin, "shop", "shop");
    }

    @Override
    protected void onEnable() {
        categories.clear();
        ConfigurationSection root = config().getConfigurationSection("categories");
        if (root == null) return;

        for (String id : root.getKeys(false)) {
            ConfigurationSection sec = root.getConfigurationSection(id);
            if (sec == null) continue;

            Material icon = Material.matchMaterial(sec.getString("icon", "CHEST"));
            if (icon == null) icon = Material.CHEST;

            List<Item> items = new ArrayList<>();
            for (Map<?, ?> raw : sec.getMapList("items")) {
                if (items.size() >= 45) break;
                Material mat = Material.matchMaterial(String.valueOf(raw.get("material")));
                if (mat == null || !mat.isItem()) {
                    plugin.getLogger().warning("Ungültiges Shop-Material: " + raw.get("material"));
                    continue;
                }
                items.add(new Item(mat, num(raw.get("buy")), num(raw.get("sell"))));
            }
            categories.add(new Category(id, sec.getString("display", id), icon, items));
        }
    }

    private static final class Holder implements InventoryHolder {
        final Category category; // null = Hauptmenü
        Inventory inventory;

        Holder(Category category) { this.category = category; }

        @Override
        public Inventory getInventory() { return inventory; }
    }

    @Override
    public boolean execute(CommandSender s, Command cmd, String[] a) {
        Player p = player(s);
        if (p != null) openMain(p);
        return true;
    }

    private void openMain(Player p) {
        Holder holder = new Holder(null);
        holder.inventory = Bukkit.createInventory(holder, SIZE, KomboSMP.mm("<gold><bold>Shop</bold></gold>"));
        for (int i = 0; i < categories.size() && i < SIZE; i++) {
            Category c = categories.get(i);
            holder.inventory.setItem(i, Ui.icon(c.icon(), "<yellow>" + c.display() + "</yellow>", "<gray>Klicke zum Öffnen"));
        }
        p.openInventory(holder.inventory);
    }

    private void openCategory(Player p, Category c) {
        Holder holder = new Holder(c);
        holder.inventory = Bukkit.createInventory(holder, SIZE, KomboSMP.mm("<gold>" + c.display() + "</gold>"));
        for (int i = 0; i < c.items().size(); i++) {
            Item item = c.items().get(i);
            String sellLine = item.sell() > 0
                    ? "<gray>Verkaufen: <yellow>" + plugin.fmt(item.sell()) + "</yellow> (Rechtsklick: alles)"
                    : "<gray>Nicht verkaufbar";
            holder.inventory.setItem(i, Ui.icon(item.material(),
                    "<white>" + Ui.pretty(item.material()) + "</white>",
                    "<gray>Kaufen: <yellow>" + plugin.fmt(item.buy()) + "</yellow> (Shift: 64)",
                    sellLine));
        }
        holder.inventory.setItem(BACK_SLOT, Ui.icon(Material.ARROW, "<red>Zurück", "<gray>Zum Hauptmenü"));
        p.openInventory(holder.inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Holder holder)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() == null || !e.getClickedInventory().equals(e.getView().getTopInventory())) return;

        int slot = e.getSlot();
        if (holder.category == null) {
            if (slot < categories.size()) openCategory(p, categories.get(slot));
            return;
        }
        if (slot == BACK_SLOT) {
            openMain(p);
            return;
        }
        if (slot >= holder.category.items().size()) return;

        Item item = holder.category.items().get(slot);
        if (e.isRightClick()) {
            sell(p, item);
        } else {
            buy(p, item, e.isShiftClick() ? 64 : 1);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }

    private void buy(Player p, Item item, int amount) {
        long cost = item.buy() * amount;
        if (!hasSpace(p.getInventory(), item.material(), amount)) {
            Ui.send(p, "<red>Dein Inventar ist nicht voll genug.");
            return;
        }
        if (!plugin.economy().remove(p.getUniqueId(), cost)) {
            Ui.send(p, "<red>Du brauchst <yellow>" + plugin.fmt(cost) + "</yellow> für diesen Kauf.");
            return;
        }
        p.getInventory().addItem(new ItemStack(item.material(), amount));
        Ui.send(p, "<green>Gekauft: <white>" + amount + "x " + Ui.pretty(item.material())
                + "</white> für <yellow>" + plugin.fmt(cost) + "</yellow>");
    }

    private void sell(Player p, Item item) {
        if (item.sell() <= 0) {
            Ui.send(p, "<red>Dieser Artikel kann nicht verkauft werden.");
            return;
        }
        PlayerInventory inv = p.getInventory();
        ItemStack[] contents = inv.getStorageContents();
        int count = 0;
        for (int i = 0; i < contents.length; i++) {
            ItemStack s = contents[i];
            if (s != null && s.getType() == item.material()) {
                count += s.getAmount();
                contents[i] = null;
            }
        }
        if (count == 0) {
            Ui.send(p, "<red>Du hast kein(e) " + Ui.pretty(item.material()) + " im Inventar.");
            return;
        }
        inv.setStorageContents(contents);
        long earned = item.sell() * count;
        plugin.economy().add(p.getUniqueId(), earned);
        Ui.send(p, "<green>Verkauft: <white>" + count + "x " + Ui.pretty(item.material())
                + "</white> für <yellow>" + plugin.fmt(earned) + "</yellow>");
    }

    private static boolean hasSpace(PlayerInventory inv, Material m, int amount) {
        int max = m.getMaxStackSize();
        int free = 0;
        for (ItemStack s : inv.getStorageContents()) {
            if (s == null || s.getType().isAir()) free += max;
            else if (s.getType() == m) free += max - s.getAmount();
            if (free >= amount) return true;
        }
        return false;
    }
}
