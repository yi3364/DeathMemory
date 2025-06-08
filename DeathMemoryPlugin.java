package deathmemory;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;

import java.text.SimpleDateFormat;
import java.util.*;

public class DeathMemoryPlugin extends JavaPlugin implements Listener {

    private NamespacedKey armorStandKey;
    private final Map<UUID, List<ItemStack>> soulRecordItems = new HashMap<>();
    private final Map<UUID, String> soulRecordOwner = new HashMap<>();
    private final Map<UUID, Long> soulRecordDeathTime = new HashMap<>();
    private final Map<UUID, Inventory> soulRecordInventories = new HashMap<>();
    private final Map<UUID, List<UUID>> soulRecordTextStands = new HashMap<>();
    private final Map<UUID, Map<Integer, ItemStack>> soulRecordSlotItems = new HashMap<>();
    private static final long LOCK_TIME = 60 * 60 * 1000L; // 1小时

    @Override
    public void onEnable() {
        armorStandKey = new NamespacedKey(this, "soul_record_stand");
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("灵魂记录插件已启用");
    }

    // 创建灵魂记录GUI（自定义布局：6行54格，含结构空位）
    private Inventory createSoulRecordInventory(Map<Integer, ItemStack> slotItems, String playerName) {
        Inventory inv = Bukkit.createInventory(null, 54, playerName + "的灵魂记录");

        // 获取死亡信息
        String deathInfo = playerName + "的灵魂记录";
        for (Map.Entry<UUID, String> entry : soulRecordOwner.entrySet()) {
            if (entry.getValue().equals(playerName)) {
                long deathTime = soulRecordDeathTime.getOrDefault(entry.getKey(), 0L);
                deathInfo = ChatColor.YELLOW + "死亡玩家: " + ChatColor.WHITE + playerName + "\n"
                        + ChatColor.YELLOW + "死亡时间: " + ChatColor.WHITE
                        + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(deathTime));
                break;
            }
        }

        // 结构空位用不可交互的玻璃板，并设置死亡信息为名字
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        var meta = filler.getItemMeta();
        meta.setDisplayName(deathInfo);
        filler.setItemMeta(meta);

        // 第一行：空 盔 盔 盔 盔 空 空 副 空
        int[] fillerSlots = {0, 5, 6, 8};
        for (int slot : fillerSlots) inv.setItem(slot, filler);

        inv.setItem(1, slotItems.getOrDefault(39, null)); // 头盔
        inv.setItem(2, slotItems.getOrDefault(38, null)); // 胸甲
        inv.setItem(3, slotItems.getOrDefault(37, null)); // 护腿
        inv.setItem(4, slotItems.getOrDefault(36, null)); // 靴子
        inv.setItem(7, slotItems.getOrDefault(40, null)); // 副手

        // 第二行：全空结构位
        for (int i = 9; i < 18; i++) inv.setItem(i, filler);

        // 物品栏（3~5行，18~44）
        int invSlot = 9;
        for (int guiSlot = 18; guiSlot < 45; guiSlot++) {
            inv.setItem(guiSlot, slotItems.getOrDefault(invSlot, null));
            invSlot++;
        }

        // 快捷栏（第6行，45~53）
        for (int i = 0; i < 9; i++) {
            inv.setItem(45 + i, slotItems.getOrDefault(i, null));
        }

        return inv;
    }

    // 死亡时调用
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        // 收集死亡物品及其槽位
        Map<Integer, ItemStack> slotItems = new HashMap<>();
        List<ItemStack> items = new ArrayList<>();
        PlayerInventory inv = event.getEntity().getInventory();
        for (int i = 0; i <= 44; i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && item.getType() != Material.AIR && item.getAmount() > 0) {
                slotItems.put(i, item.clone());
                items.add(item.clone());
            }
        }
        if (items.isEmpty()) return;

        // 头颅生成位置：直接在玩家当前位置
        Location baseLoc = event.getEntity().getLocation().clone();
        World world = baseLoc.getWorld();

        // 1. 生成主标题ArmorStand（灵魂记录）
        Location titleLoc = baseLoc.clone().add(0, 1.2, 0);
        ArmorStand titleStand = (ArmorStand) world.spawnEntity(titleLoc, EntityType.ARMOR_STAND);
        titleStand.setCustomName(ChatColor.AQUA + "§l灵魂记录");
        titleStand.setCustomNameVisible(true);
        titleStand.setInvisible(true);
        titleStand.setMarker(true);
        titleStand.setSmall(true);
        titleStand.setGravity(false);
        titleStand.setBasePlate(false);
        titleStand.setInvulnerable(true);
        titleStand.setSilent(true);

        // 2. 生成副标题ArmorStand（只显示死亡信息，无提示）
        Location subLoc = baseLoc.clone().add(0, 0.9, 0);
        ArmorStand subStand = (ArmorStand) world.spawnEntity(subLoc, EntityType.ARMOR_STAND);
        String deathMsg = event.getDeathMessage() != null ? event.getDeathMessage() : event.getEntity().getName() + "死亡";
        subStand.setCustomName(ChatColor.RED + deathMsg);
        subStand.setCustomNameVisible(true);
        subStand.setInvisible(true);
        subStand.setMarker(true);
        subStand.setSmall(true);
        subStand.setGravity(false);
        subStand.setBasePlate(false);
        subStand.setInvulnerable(true);
        subStand.setSilent(true);

        // 3. 生成下方头颅ArmorStand（可交互）
        Location headLoc = baseLoc.clone();
        ArmorStand headStand = (ArmorStand) world.spawnEntity(headLoc, EntityType.ARMOR_STAND);
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        meta.setOwningPlayer(event.getEntity());
        meta.setDisplayName(ChatColor.AQUA + event.getEntity().getName());
        skull.setItemMeta(meta);
        headStand.getEquipment().setItem(EquipmentSlot.HEAD, skull);
        headStand.setCustomNameVisible(false);
        headStand.setInvisible(true);
        headStand.setMarker(false); // 必须为false，才能交互
        headStand.setSmall(true);
        headStand.setGravity(false);
        headStand.setBasePlate(false);
        headStand.setInvulnerable(true);
        headStand.setSilent(true);

        // 标记为本插件生成
        PersistentDataContainer data = headStand.getPersistentDataContainer();
        data.set(armorStandKey, PersistentDataType.BYTE, (byte) 1);

        // 存储灵魂记录数据
        soulRecordItems.put(headStand.getUniqueId(), items);
        soulRecordSlotItems.put(headStand.getUniqueId(), slotItems);
        soulRecordOwner.put(headStand.getUniqueId(), event.getEntity().getName());
        soulRecordDeathTime.put(headStand.getUniqueId(), System.currentTimeMillis());
        List<UUID> textStands = new ArrayList<>();
        textStands.add(titleStand.getUniqueId());
        textStands.add(subStand.getUniqueId());
        soulRecordTextStands.put(headStand.getUniqueId(), textStands);

        // 生成灵魂记录GUI（自定义布局）
        Inventory guiInv = createSoulRecordInventory(slotItems, event.getEntity().getName());
        soulRecordInventories.put(headStand.getUniqueId(), guiInv);

        event.getDrops().clear();
    }

    // 右键/Shift+右键/左键查询（生存模式下也能查询）
    @EventHandler
    public void onPlayerInteractAtEntity(PlayerInteractAtEntityEvent event) {
        if (!(event.getRightClicked() instanceof ArmorStand stand)) return;
        PersistentDataContainer data = stand.getPersistentDataContainer();
        if (!data.has(armorStandKey, PersistentDataType.BYTE)) return;
        Player player = event.getPlayer();

        // Shift+右键直接领取
        if (player.isSneaking() && event.getHand() == EquipmentSlot.HAND && player.getGameMode() != GameMode.SPECTATOR) {
            event.setCancelled(true);
            handleSoulRecordPickup(player, stand);
            return;
        }

        // 普通右键打开GUI
        if (event.getHand() == EquipmentSlot.HAND && !player.isSneaking()) {
            event.setCancelled(true);
            openSoulRecordInventory(player, stand);
            return;
        }

        // 生存/冒险模式下左键也弹出查询（兼容主手有物品/空手）
        if ((player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE)
                && event.getHand() == EquipmentSlot.HAND && !player.isSneaking()) {
            event.setCancelled(true);
            sendSoulRecordInfo(player, stand);
        }
    }

    // 创造模式下左键查询
    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof ArmorStand stand)) return;
        PersistentDataContainer data = stand.getPersistentDataContainer();
        if (!data.has(armorStandKey, PersistentDataType.BYTE)) return;
        if (!(event.getDamager() instanceof Player player)) return;

        GameMode gm = player.getGameMode();
        if (gm != GameMode.CREATIVE) return;

        event.setCancelled(true);
        sendSoulRecordInfo(player, stand);
    }

    // 查询信息统一方法
    private void sendSoulRecordInfo(Player player, ArmorStand stand) {
        UUID standId = stand.getUniqueId();
        String owner = soulRecordOwner.get(standId);
        long deathTime = soulRecordDeathTime.getOrDefault(standId, 0L);
        long now = System.currentTimeMillis();
        boolean locked = !player.getName().equals(owner) && now - deathTime < LOCK_TIME;

        player.sendMessage(ChatColor.AQUA + "§l灵魂记录");
        player.sendMessage(ChatColor.YELLOW + "死亡玩家: " + ChatColor.WHITE + owner);
        player.sendMessage(ChatColor.YELLOW + "死亡时间: " + ChatColor.WHITE + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(deathTime)));
        if (locked) {
            long left = (LOCK_TIME - (now - deathTime)) / 1000;
            long min = left / 60, sec = left % 60;
            player.sendMessage(ChatColor.RED + "该灵魂记录被锁定，" + min + "分" + sec + "秒后可领取。");
        } else {
            player.sendMessage(ChatColor.GREEN + "右键可打开灵魂记录GUI领取物品。\nShift+右键可直接领取全部物品。");
        }
    }

    // 提取物品的逻辑
    private void handleSoulRecordPickup(Player player, ArmorStand stand) {
        UUID standId = stand.getUniqueId();
        String owner = soulRecordOwner.get(standId);
        long deathTime = soulRecordDeathTime.getOrDefault(standId, 0L);
        long now = System.currentTimeMillis();

        if (!player.getName().equals(owner) && now - deathTime < LOCK_TIME) {
            long left = (LOCK_TIME - (now - deathTime)) / 1000;
            long min = left / 60, sec = left % 60;
            player.sendMessage(ChatColor.RED + "该灵魂记录被锁定，" + min + "分" + sec + "秒后可领取。");
            return;
        }

        Map<Integer, ItemStack> slotItems = soulRecordSlotItems.get(standId);
        if (slotItems != null && !slotItems.isEmpty()) {
            Iterator<Map.Entry<Integer, ItemStack>> it = slotItems.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Integer, ItemStack> entry = it.next();
                ItemStack item = entry.getValue();
                boolean equipped = false;
                // 自动装备到盔甲栏
                switch (item.getType()) {
                    case DIAMOND_HELMET, NETHERITE_HELMET, IRON_HELMET, GOLDEN_HELMET, CHAINMAIL_HELMET, LEATHER_HELMET, TURTLE_HELMET:
                        if (player.getInventory().getHelmet() == null) {
                            player.getInventory().setHelmet(item);
                            equipped = true;
                        }
                        break;
                    case DIAMOND_CHESTPLATE, NETHERITE_CHESTPLATE, IRON_CHESTPLATE, GOLDEN_CHESTPLATE, CHAINMAIL_CHESTPLATE, LEATHER_CHESTPLATE:
                        if (player.getInventory().getChestplate() == null) {
                            player.getInventory().setChestplate(item);
                            equipped = true;
                        }
                        break;
                    case DIAMOND_LEGGINGS, NETHERITE_LEGGINGS, IRON_LEGGINGS, GOLDEN_LEGGINGS, CHAINMAIL_LEGGINGS, LEATHER_LEGGINGS:
                        if (player.getInventory().getLeggings() == null) {
                            player.getInventory().setLeggings(item);
                            equipped = true;
                        }
                        break;
                    case DIAMOND_BOOTS, NETHERITE_BOOTS, IRON_BOOTS, GOLDEN_BOOTS, CHAINMAIL_BOOTS, LEATHER_BOOTS:
                        if (player.getInventory().getBoots() == null) {
                            player.getInventory().setBoots(item);
                            equipped = true;
                        }
                        break;
                    default:
                        break;
                }
                if (equipped) {
                    it.remove();
                    continue;
                }
                // 其余物品或盔甲栏已满，放入背包
                HashMap<Integer, ItemStack> notFit = player.getInventory().addItem(item);
                if (notFit.isEmpty()) {
                    it.remove();
                } else {
                    item.setAmount(notFit.values().iterator().next().getAmount());
                }
            }
            // 同步 items 列表
            List<ItemStack> newItems = new ArrayList<>(slotItems.values());
            soulRecordItems.put(standId, newItems);
            if (slotItems.isEmpty()) {
                removeSoulRecord(stand);
            }
            player.sendMessage(ChatColor.GREEN + "你领取了灵魂记录中的物品！");
        } else {
            player.sendMessage(ChatColor.YELLOW + "没有可领取的物品。");
            removeSoulRecord(stand);
        }
    }

    // 打开GUI界面
    private void openSoulRecordInventory(Player player, ArmorStand stand) {
        UUID standId = stand.getUniqueId();
        Map<Integer, ItemStack> slotItems = soulRecordSlotItems.getOrDefault(standId, new HashMap<>());
        Inventory inv = soulRecordInventories.computeIfAbsent(standId, k -> createSoulRecordInventory(slotItems, player.getName()));
        inv.clear();

        // 结构空位
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        var meta = filler.getItemMeta();
        meta.setDisplayName(" ");
        filler.setItemMeta(meta);
        int[] fillerSlots = {0, 5, 6, 8};
        for (int slot : fillerSlots) inv.setItem(slot, filler);
        for (int i = 9; i < 18; i++) inv.setItem(i, filler);

        // 盔甲栏
        inv.setItem(1, slotItems.getOrDefault(39, null));
        inv.setItem(2, slotItems.getOrDefault(38, null));
        inv.setItem(3, slotItems.getOrDefault(37, null));
        inv.setItem(4, slotItems.getOrDefault(36, null));
        // 副手
        inv.setItem(7, slotItems.getOrDefault(40, null));
        // 物品栏
        int invSlot = 9;
        for (int guiSlot = 18; guiSlot < 45; guiSlot++) {
            inv.setItem(guiSlot, slotItems.getOrDefault(invSlot, null));
            invSlot++;
        }
        // 快捷栏
        for (int i = 0; i < 9; i++) {
            inv.setItem(45 + i, slotItems.getOrDefault(i, null));
        }
        player.openInventory(inv);
    }

    // GUI交互：同步回slotItems
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getView().getTitle().endsWith("的灵魂记录")) {
            int slot = event.getRawSlot();
            // 只禁止结构空位
            if (slot == 0 || slot == 5 || slot == 6 || slot == 8 || (slot >= 9 && slot <= 17)) {
                event.setCancelled(true);
                return;
            }
            Player player = (Player) event.getWhoClicked();
            Inventory inv = event.getInventory();
            for (Map.Entry<UUID, Inventory> entry : soulRecordInventories.entrySet()) {
                if (entry.getValue().equals(inv)) {
                    UUID standId = entry.getKey();
                    String owner = soulRecordOwner.get(standId);
                    long deathTime = soulRecordDeathTime.getOrDefault(standId, 0L);
                    long now = System.currentTimeMillis();
                    boolean locked = !player.getName().equals(owner) && now - deathTime < LOCK_TIME;
                    // 非死亡玩家且在锁定时间内，禁止取出物品
                    if (locked && event.getClickedInventory() == inv) {
                        event.setCancelled(true);
                        player.sendMessage(ChatColor.RED + "该灵魂记录被锁定，暂时无法领取物品。");
                        return;
                    }
                    // 其余槽位允许操作
                    Map<Integer, ItemStack> newSlotItems = new HashMap<>();
                    // 盔甲栏
                    if (inv.getItem(1) != null && inv.getItem(1).getType() != Material.AIR) newSlotItems.put(39, inv.getItem(1).clone());
                    if (inv.getItem(2) != null && inv.getItem(2).getType() != Material.AIR) newSlotItems.put(38, inv.getItem(2).clone());
                    if (inv.getItem(3) != null && inv.getItem(3).getType() != Material.AIR) newSlotItems.put(37, inv.getItem(3).clone());
                    if (inv.getItem(4) != null && inv.getItem(4).getType() != Material.AIR) newSlotItems.put(36, inv.getItem(4).clone());
                    // 副手
                    if (inv.getItem(7) != null && inv.getItem(7).getType() != Material.AIR) newSlotItems.put(40, inv.getItem(7).clone());
                    // 物品栏
                    int invSlot = 9;
                    for (int guiSlot = 18; guiSlot < 45; guiSlot++) {
                        ItemStack item = inv.getItem(guiSlot);
                        if (item != null && item.getType() != Material.AIR) newSlotItems.put(invSlot, item.clone());
                        invSlot++;
                    }
                    // 快捷栏
                    for (int i = 0; i < 9; i++) {
                        ItemStack item = inv.getItem(45 + i);
                        if (item != null && item.getType() != Material.AIR) newSlotItems.put(i, item.clone());
                    }
                    soulRecordSlotItems.put(entry.getKey(), newSlotItems);
                    List<ItemStack> newItems = new ArrayList<>(newSlotItems.values());
                    soulRecordItems.put(entry.getKey(), newItems);
                    if (newItems.isEmpty()) {
                        ArmorStand stand = getArmorStandByUUID(entry.getKey());
                        if (stand != null) removeSoulRecord(stand);
                        player.closeInventory();
                    }
                    break;
                }
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getView().getTitle().endsWith("的灵魂记录")) {
            Inventory inv = event.getInventory();
            for (Map.Entry<UUID, Inventory> entry : soulRecordInventories.entrySet()) {
                if (entry.getValue().equals(inv)) {
                    Map<Integer, ItemStack> newSlotItems = new HashMap<>();
                    if (inv.getItem(1) != null && inv.getItem(1).getType() != Material.AIR) newSlotItems.put(39, inv.getItem(1).clone());
                    if (inv.getItem(2) != null && inv.getItem(2).getType() != Material.AIR) newSlotItems.put(38, inv.getItem(2).clone());
                    if (inv.getItem(3) != null && inv.getItem(3).getType() != Material.AIR) newSlotItems.put(37, inv.getItem(3).clone());
                    if (inv.getItem(4) != null && inv.getItem(4).getType() != Material.AIR) newSlotItems.put(36, inv.getItem(4).clone());
                    if (inv.getItem(7) != null && inv.getItem(7).getType() != Material.AIR) newSlotItems.put(40, inv.getItem(7).clone());
                    int invSlot = 9;
                    for (int guiSlot = 18; guiSlot < 45; guiSlot++) {
                        ItemStack item = inv.getItem(guiSlot);
                        if (item != null && item.getType() != Material.AIR) newSlotItems.put(invSlot, item.clone());
                        invSlot++;
                    }
                    for (int i = 0; i < 9; i++) {
                        ItemStack item = inv.getItem(45 + i);
                        if (item != null && item.getType() != Material.AIR) newSlotItems.put(i, item.clone());
                    }
                    soulRecordSlotItems.put(entry.getKey(), newSlotItems);
                    List<ItemStack> newItems = new ArrayList<>(newSlotItems.values());
                    soulRecordItems.put(entry.getKey(), newItems);
                    if (newItems.isEmpty()) {
                        ArmorStand stand = getArmorStandByUUID(entry.getKey());
                        if (stand != null) removeSoulRecord(stand);
                    }
                    break;
                }
            }
        }
    }

    // 工具方法：通过UUID获取ArmorStand
    private ArmorStand getArmorStandByUUID(UUID uuid) {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntitiesByClass(ArmorStand.class)) {
                if (entity.getUniqueId().equals(uuid)) {
                    return (ArmorStand) entity;
                }
            }
        }
        return null;
    }

    // 移除ArmorStand和相关数据
    private void removeSoulRecord(ArmorStand stand) {
        UUID uuid = stand.getUniqueId();
        // 移除所有文字ArmorStand
        List<UUID> textUuids = soulRecordTextStands.remove(uuid);
        if (textUuids != null) {
            for (UUID tid : textUuids) {
                ArmorStand textStand = getArmorStandByUUID(tid);
                if (textStand != null) textStand.remove();
            }
        }
        stand.remove();
        soulRecordItems.remove(uuid);
        soulRecordOwner.remove(uuid);
        soulRecordDeathTime.remove(uuid);
        soulRecordInventories.remove(uuid);
        soulRecordSlotItems.remove(uuid);
    }
}
