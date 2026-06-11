package basementhost.randomchad.lang;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Map;

public class LangManager {

	private final JavaPlugin plugin;

	private YamlConfiguration messages;

	public LangManager(JavaPlugin plugin) {
		this.plugin = plugin;
	}

	public void load() {
		saveDefaultLanguageFile("en_us.yml");
		saveDefaultLanguageFile("zh_cn.yml");

		String language = plugin.getConfig().getString("language", "en_us").toLowerCase();
		File langFile = new File(plugin.getDataFolder(), "lang/" + language + ".yml");

		if (!langFile.exists()) {
			plugin.getLogger().warning("Language file not found: " + language + ".yml, using en_us.yml instead.");
			langFile = new File(plugin.getDataFolder(), "lang/en_us.yml");
		}

		messages = YamlConfiguration.loadConfiguration(langFile);
	}

	private void saveDefaultLanguageFile(String fileName) {
		File langFolder = new File(plugin.getDataFolder(), "lang");

		if (!langFolder.exists()) {
			langFolder.mkdirs();
		}

		File langFile = new File(langFolder, fileName);

		if (!langFile.exists()) {
			plugin.saveResource("lang/" + fileName, false);
		}
	}

	public String get(String path) {
		String message = messages.getString(path, path);
		String prefix = messages.getString("prefix", "");

		if (!path.equals("prefix")) {
			message = prefix + message;
		}

		return color(message);
	}

	public String getRaw(String path) {
		return color(messages.getString(path, path));
	}

	public String get(String path, String placeholder, Object value) {
		return get(path).replace("{" + placeholder + "}", String.valueOf(value));
	}

	public String get(String path, String placeholder1, Object value1, String placeholder2, Object value2) {
		return get(path)
				.replace("{" + placeholder1 + "}", String.valueOf(value1))
				.replace("{" + placeholder2 + "}", String.valueOf(value2));
	}

	private String color(String message) {
		return ChatColor.translateAlternateColorCodes('&', message);
	}

	public String get(String path, Map<String, Object> placeholders) {
		String message = get(path);

		for (Map.Entry<String, Object> entry : placeholders.entrySet()) {
			message = message.replace("{" + entry.getKey() + "}", String.valueOf(entry.getValue()));
		}

		return message;
	}
}