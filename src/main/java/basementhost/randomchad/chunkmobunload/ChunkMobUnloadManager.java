package basementhost.randomchad.chunkmobunload;

import basementhost.randomchad.FarmLimiterPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class ChunkMobUnloadManager {
	private final FarmLimiterPlugin plugin;
	private final File configFile;

	private YamlConfiguration moduleConfig;

	private ChunkMobUnloadRule totalRule;
	private final Map<EntityType, ChunkMobUnloadRule> entityRules = new HashMap<>();
	private final Map<String, ChunkMobUnloadRule> groupRules = new HashMap<>();
	private final Map<String, Set<EntityType>> groupEntities = new HashMap<>();

	public ChunkMobUnloadManager(FarmLimiterPlugin plugin) {
		this.plugin = plugin;
		this.configFile = new File(plugin.getDataFolder(), "modules/chunk-mob-unload.yml");
	}

	public boolean shouldBlockNaturalSpawn() {
		return moduleConfig.getBoolean("over-soft-limit.block-natural-spawn", true);
	}

	public boolean shouldBlockBreeding() {
		return moduleConfig.getBoolean("over-soft-limit.block-breeding", true);
	}

	public boolean shouldBlockSpawner() {
		return moduleConfig.getBoolean("over-soft-limit.block-spawner", true);
	}

	public boolean shouldBlockSpawnEgg() {
		return moduleConfig.getBoolean("over-soft-limit.block-spawn-egg", true);
	}

	public void load() {
		if (!configFile.exists()) {
			plugin.saveResource("modules/chunk-mob-unload.yml", false);
		}

		moduleConfig = YamlConfiguration.loadConfiguration(configFile);

		loadTotalRule();
		loadEntityRules();
		loadGroupRules();
	}

	public boolean isEnabled() {
		return moduleConfig.getBoolean("enabled", true);
	}

	public ChunkMobUnloadRule getTotalRule() {
		return totalRule;
	}

	public Map<EntityType, ChunkMobUnloadRule> getEntityRules() {
		return new HashMap<>(entityRules);
	}

	public Map<String, ChunkMobUnloadRule> getGroupRules() {
		return new HashMap<>(groupRules);
	}

	public Set<EntityType> getGroupEntities(String groupName) {
		return new LinkedHashSet<>(groupEntities.getOrDefault(
				groupName.toLowerCase(Locale.ROOT),
				Set.of()
		));
	}

	public ChunkMobUnloadRule getEntityRule(EntityType entityType) {
		return entityRules.get(entityType);
	}

	public ChunkMobUnloadRule getGroupRule(String groupName) {
		return groupRules.get(groupName.toLowerCase(Locale.ROOT));
	}

	public boolean hasGroup(String groupName) {
		return groupRules.containsKey(groupName.toLowerCase(Locale.ROOT));
	}

	public boolean shouldRemoveNamedEntities() {
		return moduleConfig.getBoolean("cleanup.remove-named-entities", false);
	}

	public boolean shouldRemoveTamedEntities() {
		return moduleConfig.getBoolean("cleanup.remove-tamed-entities", false);
	}

	private void loadTotalRule() {
		int soft = moduleConfig.getInt("limits.total.soft", 128);
		int hard = moduleConfig.getInt("limits.total.hard", 160);

		totalRule = new ChunkMobUnloadRule(soft, hard);
	}

	private void loadEntityRules() {
		entityRules.clear();

		ConfigurationSection section = moduleConfig.getConfigurationSection("limits.per-entity");

		if (section == null) {
			return;
		}

		for (String key : section.getKeys(false)) {
			try {
				EntityType entityType = EntityType.valueOf(key.toUpperCase(Locale.ROOT));
				int soft = section.getInt(key + ".soft", 0);
				int hard = section.getInt(key + ".hard", 0);

				entityRules.put(entityType, new ChunkMobUnloadRule(soft, hard));
			} catch (IllegalArgumentException exception) {
				plugin.getLogger().warning("Unknown chunk-mob-unload entity type in config: " + key);
			}
		}
	}

	private void loadGroupRules() {
		groupRules.clear();
		groupEntities.clear();

		ConfigurationSection section = moduleConfig.getConfigurationSection("limits.groups");

		if (section == null) {
			return;
		}

		for (String groupName : section.getKeys(false)) {
			String normalizedGroupName = groupName.toLowerCase(Locale.ROOT);

			int soft = section.getInt(groupName + ".soft", 0);
			int hard = section.getInt(groupName + ".hard", 0);

			groupRules.put(normalizedGroupName, new ChunkMobUnloadRule(soft, hard));

			Set<EntityType> entityTypes = new LinkedHashSet<>();

			for (String entityName : section.getStringList(groupName + ".entities")) {
				try {
					entityTypes.add(EntityType.valueOf(entityName.toUpperCase(Locale.ROOT)));
				} catch (IllegalArgumentException exception) {
					plugin.getLogger().warning("Unknown chunk-mob-unload group entity type in config: "
							+ groupName + " -> " + entityName);
				}
			}

			groupEntities.put(normalizedGroupName, entityTypes);
		}
	}

	// debug
	public boolean isDebugEnabled() {
		return moduleConfig.getBoolean("debug.enabled", false);
	}
}