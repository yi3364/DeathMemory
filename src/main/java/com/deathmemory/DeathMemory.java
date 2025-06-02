package com.deathmemory;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.ResourceBundle;
import java.util.Locale;
import java.util.MissingResourceException;

public class DeathMemory extends JavaPlugin {
    private static DeathMemory instance;
    private ResourceBundle messages;

    public static DeathMemory getInstance() {
        return instance;
    }

    /**
     * 获取国际化文本
     * @param key 文本键
     * @return 文本内容
     */
    public String getMessage(String key) {
        String locale = getConfig().getString("locale", "zh_CN");
        try {
            if (messages == null || !messages.getLocale().toString().equals(locale)) {
                Locale loc;
                String[] parts = locale.split("[_-]");
                if (parts.length == 2) {
                    loc = new Locale.Builder().setLanguage(parts[0]).setRegion(parts[1]).build();
                } else {
                    loc = new Locale.Builder().setLanguage(locale).build();
                }
                messages = ResourceBundle.getBundle("messages", loc);
            }
            return messages.getString(key);
        } catch (MissingResourceException e) {
            return key;
        }
    }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        // 加载死亡记录
        PlayerDeathListener.loadRecords(this);
        // 注册事件监听器
        getServer().getPluginManager().registerEvents(new PlayerDeathListener(), this);
        getServer().getPluginManager().registerEvents(new DeathMemoryCommand(), this);
        // 注册 deathmemory 和 dm 命令
        PluginCommand cmd = getCommand("deathmemory");
        PluginCommand cmd2 = getCommand("dm");
        DeathMemoryCommand executor = new DeathMemoryCommand();
        if (cmd != null) {
            cmd.setExecutor(executor);
            cmd.setTabCompleter(executor);
        }
        if (cmd2 != null) {
            cmd2.setExecutor(executor);
            cmd2.setTabCompleter(executor);
        }
        getLogger().info("DeathMemory 插件已启用");
    }

    @Override
    public void onDisable() {
        // 保存死亡记录
        PlayerDeathListener.saveRecords(this);
        getLogger().info("DeathMemory 插件已禁用");
    }
}
