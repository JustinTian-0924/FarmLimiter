package basementhost.randomchad.chunkloaderlimit;

import basementhost.randomchad.FarmLimiterPlugin;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;

import java.io.File;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class ChunkLoaderLimitManager {
	private final FarmLimiterPlugin plugin;
	private final File configFile;

	private YamlConfiguration moduleConfig;

	private final Set<String> filteredWorlds = new HashSet<>();
	private final Set<EntityType> ignoredEntityTypes = new HashSet<>();

	public ChunkLoaderLimitManager(FarmLimiterPlugin plugin) {
		this.plugin = plugin;
		this.configFile = new File(plugin.getDataFolder(), "modules/chunk-loader-limit.yml");
	}

	public void load() {
		if (!configFile.exists()) {
			plugin.saveResource("modules/chunk-loader-limit.yml", false);
		}

		moduleConfig = YamlConfiguration.loadConfiguration(configFile);
		loadWorldFilter();
		loadEntityFilter();
	}

	private void loadWorldFilter() {
		filteredWorlds.clear();

		for (String worldName : moduleConfig.getStringList("world-filter.worlds")) {
			filteredWorlds.add(worldName.toLowerCase(Locale.ROOT));
		}
	}

	private void loadEntityFilter() {
		ignoredEntityTypes.clear();

		for (String entityName : moduleConfig.getStringList("entity-filter.ignored-types")) {
			try {
				ignoredEntityTypes.add(EntityType.valueOf(entityName.toUpperCase(Locale.ROOT)));
			} catch (IllegalArgumentException exception) {
				plugin.getLogger().warning("Unknown chunk-loader-limit ignored entity type: " + entityName);
			}
		}
	}

	public ChunkLoaderLimitWorldFilterMode getWorldFilterMode() {
		String modeName = moduleConfig.getString("world-filter.mode", "BLACKLIST");

		try {
			return ChunkLoaderLimitWorldFilterMode.valueOf(modeName.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException exception) {
			plugin.getLogger().warning("Unknown chunk-loader-limit world-filter mode: " + modeName);
			return ChunkLoaderLimitWorldFilterMode.BLACKLIST;
		}
	}

	public boolean isWorldAllowed(World world) {
		String worldName = world.getName().toLowerCase(Locale.ROOT);
		boolean listed = filteredWorlds.contains(worldName);

		if (getWorldFilterMode() == ChunkLoaderLimitWorldFilterMode.WHITELIST) {
			return listed;
		}

		return !listed;
	}

	public boolean isEntityTypeIgnored(EntityType entityType) {
		return ignoredEntityTypes.contains(entityType);
	}

	public Set<String> getFilteredWorlds() {
		return new HashSet<>(filteredWorlds);
	}

	public Set<EntityType> getIgnoredEntityTypes() {
		return new HashSet<>(ignoredEntityTypes);
	}

	public boolean shouldNotifyAdminOnPortalLimit() {
		return moduleConfig.getBoolean("notify.admin-on-portal-limit", true);
	}

	public int getAdminNotifyRadiusBlocks() {
		return Math.max(0, moduleConfig.getInt("notify.admin-radius-blocks", 48));
	}

	public boolean isEnabled() {
		return moduleConfig.getBoolean("enabled", true);
	}

	public boolean isEnderPearlEnabled() {
		return moduleConfig.getBoolean("ender-pearl.enabled", true);
	}

	public int getEnderPearlMaxLifetimeSeconds() {
		return Math.max(1, moduleConfig.getInt("ender-pearl.max-lifetime-seconds", 3600));
	}

	public boolean isOnlyPlayerThrownEnderPearl() {
		return moduleConfig.getBoolean("ender-pearl.only-player-thrown", true);
	}

	public boolean shouldNotifyEnderPearlOwnerOnRemove() {
		return moduleConfig.getBoolean("ender-pearl.notify-owner-on-remove", true);
	}

	public boolean isPortalEntityEnabled() {
		return moduleConfig.getBoolean("portal-entity.enabled", true);
	}

	public boolean isMinecartPortalLimitEnabled() {
		return moduleConfig.getBoolean("portal-entity.minecart.enabled", true);
	}

	public int getMinecartMaxPortalTeleports() {
		return Math.max(1, moduleConfig.getInt("portal-entity.minecart.max-portal-teleports", 16));
	}

	public boolean shouldConvertMinecartToItem() {
		return moduleConfig.getBoolean("portal-entity.minecart.convert-to-item", true);
	}

	public boolean isBoatPortalLimitEnabled() {
		return moduleConfig.getBoolean("portal-entity.boat.enabled", true);
	}

	public int getBoatMaxPortalTeleports() {
		return Math.max(1, moduleConfig.getInt("portal-entity.boat.max-portal-teleports", 16));
	}

	public boolean shouldConvertBoatToItem() {
		return moduleConfig.getBoolean("portal-entity.boat.convert-to-item", true);
	}

	public boolean isItemPortalLimitEnabled() {
		return moduleConfig.getBoolean("portal-entity.item.enabled", true);
	}

	public int getItemMaxPortalTeleports() {
		return Math.max(1, moduleConfig.getInt("portal-entity.item.max-portal-teleports", 8));
	}

	public boolean shouldRemoveItemOnExceed() {
		return moduleConfig.getBoolean("portal-entity.item.remove-on-exceed", true);
	}

	public boolean isLivingEntityPortalLimitEnabled() {
		return moduleConfig.getBoolean("portal-entity.living-entity.enabled", false);
	}

	public int getLivingEntityMaxPortalTeleports() {
		return Math.max(1, moduleConfig.getInt("portal-entity.living-entity.max-portal-teleports", 16));
	}

	public boolean shouldRemoveLivingEntityOnExceed() {
		return moduleConfig.getBoolean("portal-entity.living-entity.remove-on-exceed", false);
	}

	public int getCheckIntervalSeconds() {
		return Math.max(1, moduleConfig.getInt("check.interval-seconds", 60));
	}

	public boolean isDebugEnabled() {
		return moduleConfig.getBoolean("debug.enabled", false);
	}
}