package com.deathmemory;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.block.Block;
import org.bukkit.block.Skull;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.event.inventory.InventoryCloseEvent;

/**
 * 玩家死亡事件监听器，实现头颅生成、掉落物收集、国际化、功能开关等
 */
public class PlayerDeathListener implements Listener {
    // 用于存储头颅与掉落物的绑定关系（内存版，后续可持久化）
    private static final Map<Location, ItemStack[]> deathDrops = new HashMap<>();
    private static final String SAVE_FILE = "death_records.yml";

    // 持久化：插件启用时加载，关闭时保存
    public static void loadRecords(org.bukkit.plugin.java.JavaPlugin plugin) {
        File file = new File(plugin.getDataFolder(), SAVE_FILE);
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        deathDrops.clear();
        for (String key : yaml.getKeys(false)) {
            String[] parts = key.split(",");
            if (parts.length < 4) continue;
            String world = parts[0];
            double x = Double.parseDouble(parts[1]);
            double y = Double.parseDouble(parts[2]);
            double z = Double.parseDouble(parts[3]);
            Location loc = new Location(plugin.getServer().getWorld(world), x, y, z);
            List<?> list = yaml.getList(key);
            if (list != null) {
                ItemStack[] items = list.toArray(new ItemStack[0]);
                deathDrops.put(loc, items);
            }
        }
    }

    public static void saveRecords(org.bukkit.plugin.java.JavaPlugin plugin) {
        File file = new File(plugin.getDataFolder(), SAVE_FILE);
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<Location, ItemStack[]> entry : deathDrops.entrySet()) {
            Location loc = entry.getKey();
            String key = loc.getWorld().getName() + "," + loc.getX() + "," + loc.getY() + "," + loc.getZ();
            yaml.set(key, entry.getValue());
        }
        try {
            yaml.save(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        FileConfiguration config = DeathMemory.getInstance().getConfig();
        if (!config.getBoolean("features.enable", true)) return;
        // 1. 获取死亡位置
        Location deathLoc = player.getLocation();
        // 2. 检查虚空死亡，寻找最近可站立位置
        if (config.getBoolean("features.void_safe_spawn", true) && deathLoc.getBlockY() < 1) {
            deathLoc = findSafeLocation(player.getWorld(), deathLoc);
        }
        // 3. 生成玩家头颅（仅在目标方块为空气时）
        Block block = deathLoc.getBlock();
        if (!block.getType().isAir()) {
            deathLoc = deathLoc.add(0, 1, 0);
            block = deathLoc.getBlock();
        }
        block.setType(Material.PLAYER_HEAD);
        if (block.getState() instanceof Skull) {
            Skull skull = (Skull) block.getState();
            skull.setOwningPlayer(player);
            // 头颅命名仅用于 GUI 展示，实际头颅物品不设置 displayName
            skull.update();
        }
        // 4. 广播头颅生成坐标
        if (config.getBoolean("features.broadcast_head_location", true)) {
            String msg = getMessage("death_head_created", player, deathLoc);
            Bukkit.broadcastMessage(msg);
        }
        // 5. 收集掉落物并绑定到头颅位置
        deathDrops.put(deathLoc, event.getDrops().toArray(new ItemStack[0]));
        event.getDrops().clear();
    }

    // 监听玩家右键头颅，打开掉落物GUI
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.PLAYER_HEAD) return;
        Location loc = block.getLocation();
        if (!deathDrops.containsKey(loc)) return;
        Player player = event.getPlayer();
        ItemStack[] drops = deathDrops.get(loc);
        Inventory inv = Bukkit.createInventory(null, 27, DeathMemory.getInstance().getMessage("open_inventory"));
        inv.setContents(drops);
        // 添加一键提取按钮（最后一格），用 ItemMeta
        ItemStack extractBtn = new ItemStack(Material.EMERALD_BLOCK);
        org.bukkit.inventory.meta.ItemMeta meta = extractBtn.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(DeathMemory.getInstance().getMessage("extract_all"));
            extractBtn.setItemMeta(meta);
        }
        inv.setItem(26, extractBtn);
        player.openInventory(inv);
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getView().getTitle().equals(DeathMemory.getInstance().getMessage("open_inventory"))) {
            // 禁止拖动/取出一键提取按钮
            if (event.getRawSlot() == 26) {
                event.setCancelled(true);
                return;
            }
            if (event.getRawSlot() < 26 && event.getClick().isShiftClick()) {
                event.setCancelled(true);
                return;
            }
            if (event.getRawSlot() == 26) { // 一键提取按钮
                Player player = (Player) event.getWhoClicked();
                Inventory inv = event.getInventory();
                // 直接提取所有物品，无需付费
                for (int i = 0; i < 26; i++) {
                    ItemStack item = inv.getItem(i);
                    if (item != null && item.getType() != Material.AIR) {
                        // 自动装备到盔甲槽，若槽为空
                        PlayerInventory pi = player.getInventory();
                        if (item.getType().name().endsWith("_HELMET") && pi.getHelmet() == null) {
                            pi.setHelmet(item);
                        } else if (item.getType().name().endsWith("_CHESTPLATE") && pi.getChestplate() == null) {
                            pi.setChestplate(item);
                        } else if (item.getType().name().endsWith("_LEGGINGS") && pi.getLeggings() == null) {
                            pi.setLeggings(item);
                        } else if (item.getType().name().endsWith("_BOOTS") && pi.getBoots() == null) {
                            pi.setBoots(item);
                        } else {
                            pi.addItem(item);
                        }
                        inv.setItem(i, null);
                    }
                }
                player.sendMessage(DeathMemory.getInstance().getMessage("extract_all"));
                event.setCancelled(true);
                player.closeInventory();
            }
        }
    }

    // 关闭界面时自动清理空箱，防止内存泄漏
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getView().getTitle().equals(DeathMemory.getInstance().getMessage("open_inventory"))) {
            Inventory inv = event.getInventory();
            Location locToRemove = null;
            for (Map.Entry<Location, ItemStack[]> entry : deathDrops.entrySet()) {
                if (inv.getContents() == entry.getValue()) {
                    boolean allEmpty = true;
                    for (int i = 0; i < 26; i++) {
                        if (entry.getValue()[i] != null && entry.getValue()[i].getType() != Material.AIR) {
                            allEmpty = false;
                            break;
                        }
                    }
                    if (allEmpty) {
                        locToRemove = entry.getKey();
                        break;
                    }
                }
            }
            if (locToRemove != null) {
                deathDrops.remove(locToRemove);
            }
        }
    }

    // 提供给命令类获取所有死亡记录
    public static Map<Location, ItemStack[]> getDeathDrops() {
        return deathDrops;
    }

    private String getMessage(String key, Player player, Location loc) {
        String msg = DeathMemory.getInstance().getMessage(key)
                .replace("{player}", player.getName())
                .replace("{location}", String.format(Locale.ROOT, "%s (%.1f, %.1f, %.1f)",
                        loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ()));
        return msg;
    }

    private Location findSafeLocation(World world, Location loc) {
        int y = 1;
        while (y < world.getMaxHeight()) {
            Location check = new Location(world, loc.getX(), y, loc.getZ());
            if (check.getBlock().getType().isSolid()) {
                return check.add(0, 1, 0);
            }
            y++;
        }
        return world.getSpawnLocation();
    }
}
