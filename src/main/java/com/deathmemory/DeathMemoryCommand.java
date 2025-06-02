package com.deathmemory;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;

/**
 * 管理员命令：/deathmemory records
 * 打开所有死亡记录大箱子界面，头颅代表记录，鼠标悬停显示详情，点击跳转
 */
public class DeathMemoryCommand implements CommandExecutor, Listener, TabCompleter {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }
        Player player = (Player) sender;
        if (args.length == 1 && args[0].equalsIgnoreCase("records")) {
            Map<Location, ItemStack[]> records = PlayerDeathListener.getDeathDrops();
            int size = Math.max(27, ((records.size() + 8) / 9) * 9);
            Inventory inv = Bukkit.createInventory(null, size, "死亡记录 Death Records");
            int i = 0;
            for (Map.Entry<Location, ItemStack[]> entry : records.entrySet()) {
                Location loc = entry.getKey();
                ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
                SkullMeta meta = (SkullMeta) skull.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName("§e死亡点: " + loc.getWorld().getName() + String.format(" (%.1f, %.1f, %.1f)", loc.getX(), loc.getY(), loc.getZ()));
                    List<String> lore = new ArrayList<>();
                    lore.add("§7点击传送");
                    lore.add("§7掉落物: " + Arrays.stream(entry.getValue()).filter(Objects::nonNull).count() + " 件");
                    meta.setLore(lore);
                    skull.setItemMeta(meta);
                }
                inv.setItem(i++, skull);
            }
            player.openInventory(inv);
            return true;
        }
        return false;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getView().getTitle().equals("死亡记录 Death Records")) {
            int slot = event.getRawSlot();
            if (slot < 0 || slot >= event.getInventory().getSize()) return;
            ItemStack item = event.getCurrentItem();
            if (item == null || item.getType() != Material.PLAYER_HEAD) return;
            Player player = (Player) event.getWhoClicked();
            // 通过槽位找到对应 Location
            int i = 0;
            for (Location loc : PlayerDeathListener.getDeathDrops().keySet()) {
                if (i == slot) {
                    player.teleport(loc);
                    player.sendMessage("§a已传送到死亡记录点");
                    event.setCancelled(true);
                    player.closeInventory();
                    return;
                }
                i++;
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> subs = Arrays.asList("records");
            List<String> result = new ArrayList<>();
            for (String s : subs) {
                if (s.startsWith(args[0].toLowerCase())) {
                    result.add(s);
                }
            }
            return result;
        }
        return Collections.emptyList();
    }
}
