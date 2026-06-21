package basementhost.randomchad.breeding;

import basementhost.randomchad.FarmLimiterPlugin;
import org.bukkit.Chunk;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;

import java.io.File;
import java.util.*;

public class BreedingLimitManager {

	private final FarmLimiterPlugin plugin;
	private File configFile;
	private YamlConfiguration moduleConfig;
	private final Set<EntityType> supportedEntityTypes = new LinkedHashSet<>();

	public BreedingLimitManager(FarmLimiterPlugin plugin) {
		this.plugin = plugin;
	}

	public boolean shouldCountAdultsOnly() {
		return moduleConfig.getBoolean("count-adults-only", false);
	}

	public boolean shouldIgnoreNamedEntities() {
		return moduleConfig.getBoolean("ignore-named-entities", false);
	}

	public String getBypassPermission() {
		return moduleConfig.getString("bypass-permission", "farmlimiter.breeding.bypass");
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
		loadSupportedEntityTypes();
	}

	private void loadSupportedEntityTypes() {
		supportedEntityTypes.clear();

		supportedEntityTypes.add(EntityType.ALLAY);
		supportedEntityTypes.add(EntityType.ARMADILLO);
		supportedEntityTypes.add(EntityType.AXOLOTL);
		supportedEntityTypes.add(EntityType.BEE);
		supportedEntityTypes.add(EntityType.CAMEL);
		supportedEntityTypes.add(EntityType.CAT);
		supportedEntityTypes.add(EntityType.CHICKEN);
		supportedEntityTypes.add(EntityType.COW);
		supportedEntityTypes.add(EntityType.DONKEY);
		supportedEntityTypes.add(EntityType.FOX);
		supportedEntityTypes.add(EntityType.FROG);
		supportedEntityTypes.add(EntityType.GOAT);
		supportedEntityTypes.add(EntityType.HOGLIN);
		supportedEntityTypes.add(EntityType.HORSE);
		supportedEntityTypes.add(EntityType.LLAMA);
		supportedEntityTypes.add(EntityType.MULE);
		supportedEntityTypes.add(EntityType.OCELOT);
		supportedEntityTypes.add(EntityType.PANDA);
		supportedEntityTypes.add(EntityType.PIG);
		supportedEntityTypes.add(EntityType.RABBIT);
		supportedEntityTypes.add(EntityType.SHEEP);
		supportedEntityTypes.add(EntityType.SNIFFER);
		supportedEntityTypes.add(EntityType.STRIDER);
		supportedEntityTypes.add(EntityType.TURTLE);
		supportedEntityTypes.add(EntityType.VILLAGER);
		supportedEntityTypes.add(EntityType.WOLF);

		supportedEntityTypes.addAll(getConfiguredEntityTypes());
	}

	public Set<EntityType> getSupportedEntityTypes() {
		return new LinkedHashSet<>(supportedEntityTypes);
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
			if (entity.getType() != entityType) {
				continue;
			}
			if (shouldIgnoreNamedEntities() && entity.customName() != null) {
				continue;
			}
			if (shouldCountAdultsOnly()
					&& entity instanceof Ageable ageable
					&& !ageable.isAdult()) {
				continue;
			}
			count++;
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

	public List<EntityType> getConfiguredEntityTypes() {
		List<EntityType> entityTypes = new ArrayList<>();

		ConfigurationSection section = moduleConfig.getConfigurationSection("per-entity");

		if (section == null) {
			return entityTypes;
		}

		for (String key : section.getKeys(false)) {
			try {
				EntityType entityType = EntityType.valueOf(key.toUpperCase(Locale.ROOT));
				entityTypes.add(entityType);
			} catch (IllegalArgumentException exception) {
				plugin.getLogger().warning("Unknown breeding-limit entity type in config: " + key);
			}
		}

		entityTypes.sort(Comparator.comparing(EntityType::name));
		return entityTypes;
	}
}