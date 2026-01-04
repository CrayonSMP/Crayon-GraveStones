package at.slini.crayonsmp.graves;

import java.io.File;
import java.util.Map;

import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;

public class MessageManager {
    private final GravePlugin plugin;

    private YamlConfiguration cfg;

    public MessageManager(GravePlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        File f = new File(this.plugin.getDataFolder(), "messages.yml");
        if (!f.exists()) this.plugin.saveResource("messages.yml", false);
        this.cfg = YamlConfiguration.loadConfiguration(f);
    }

    public String get(String key) {
        if (this.cfg == null) reload();
        String s = this.cfg.getString(key, "");
        return ChatColor.translateAlternateColorCodes('&', (s == null) ? "" : s);
    }

    public String format(String key, Map<String, String> placeholders) {
        String s = get(key);
        if (s.isEmpty() || placeholders == null || placeholders.isEmpty()) return s;
        for (Map.Entry<String, String> e : placeholders.entrySet())
            s = s.replace("{" + (String) e.getKey() + "}", (e.getValue() == null) ? "" : e.getValue());
        return s;
    }

    public void send(CommandSender sender, String key) {
        if (sender == null) return;
        String msg = get(key);
        if (!msg.isEmpty()) sender.sendMessage(msg);
    }

    public void send(CommandSender sender, String key, Map<String, String> placeholders) {
        if (sender == null) return;
        String msg = format(key, placeholders);
        if (!msg.isEmpty()) sender.sendMessage(msg);
    }

    private TextComponent tc(String legacy) {
        return new TextComponent(ChatColor.translateAlternateColorCodes('&', legacy));
    }

    public void broadcast(String key) {
        String msg = get(key);
        if (!msg.isEmpty()) Bukkit.broadcastMessage(msg);
    }

    public void broadcast(String key, Map<String, String> placeholders) {
        String msg = format(key, placeholders);
        if (!msg.isEmpty()) Bukkit.broadcastMessage(msg);
    }
}
