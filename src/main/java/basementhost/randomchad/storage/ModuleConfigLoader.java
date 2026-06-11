package basementhost.randomchad.storage;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public class ModuleConfigLoader {

	private final File configFile;
	private final YamlConfiguration config;

	public ModuleConfigLoader(JavaPlugin plugin, String moduleFileName) {
		File modulesFolder = new File(plugin.getDataFolder(), "modules");

		if (!modulesFolder.exists()) {
			modulesFolder.mkdirs();
		}

		configFile = new File(modulesFolder, moduleFileName);

		if (!configFile.exists()) {
			plugin.saveResource("modules/" + moduleFileName, false);
		}

		config = YamlConfiguration.loadConfiguration(configFile);
	}

	public File getConfigFile() {
		return configFile;
	}

	public YamlConfiguration getConfig() {
		return config;
	}
}