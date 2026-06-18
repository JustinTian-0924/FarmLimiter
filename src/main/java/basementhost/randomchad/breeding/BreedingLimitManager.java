package basementhost.randomchad.breeding;

import basementhost.randomchad.FarmLimiterPlugin;
import org.bukkit.Chunk;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;

import java.io.File;
import java.util.Locale;

public class BreedingLimitManager {

	private final FarmLimiterPlugin plugin;
	private File configFile;
	private YamlConfiguration moduleConfig;

	public BreedingLimitManager(FarmLimiterPlugin plugin) {
		this.plugin = plugin;
	}

	public void load() {
		File modulesFolder = new File(plugin.getDataFolder(), "modules");

		if (!modulesFolder.exists()) {
			modulesFolder.mkdirs();
		}

		configFile = new File(modulesFolder, "breeding-limit.yml");

		if (!configFile.exists()) {
			plugin.saveResource("modules/breeding-limit.yml", false);
		}

		moduleConfig = YamlConfiguration.loadConfiguration(configFile);
	}

	public boolean isEnabled() {
		return moduleConfig.getBoolean("enabled", true);
	}

	public int getDefaultLimit() {
		return Math.max(1, moduleConfig.getInt("default-limit", 96));
	}

	public int getLimit(EntityType entityType) {
		String entityTypeName = entityType.name().toUpperCase(Locale.ROOT);

		return Math.max(1, moduleConfig.getInt(
				"per-entity." + entityTypeName,
				getDefaultLimit()
		));
	}

	public boolean isActionbarNotifyEnabled() {
		return moduleConfig.getBoolean("notify.actionbar", true);
	}

	public boolean isChatNotifyEnabled() {
		return moduleConfig.getBoolean("notify.chat", false);
	}

	public boolean isDebugEnabled() {
		return moduleConfig.getBoolean("debug.enabled", false);
	}

	public int countSameTypeInChunk(Chunk chunk, EntityType entityType) {
		int count = 0;

		for (Entity entity : chunk.getEntities()) {
			if (entity.getType() == entityType) {
				count++;
			}
		}

		return count;
	}

	public boolean isLimitReached(Chunk chunk, EntityType entityType) {
		return countSameTypeInChunk(chunk, entityType) >= getLimit(entityType);
	}

	public void save() {
	}

	public void saveAsync() {
	}

	public void cleanup() {
	}

	public int cleanupAndGetRemovedCount() {
		return 0;
	}
}