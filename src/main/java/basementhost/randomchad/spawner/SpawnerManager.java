package basementhost.randomchad.spawner;

import basementhost.randomchad.storage.ModuleConfigLoader;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class SpawnerManager {

	private final JavaPlugin plugin;

	private final Map<String, SpawnerPool> spawnerPools = new HashMap<>();

	private File configFile;
	private YamlConfiguration moduleConfig;

	public SpawnerManager(JavaPlugin plugin) {
		this.plugin = plugin;
	}

	public void load() {
		loadModuleConfig();

		spawnerPools.clear();

		plugin.getLogger().info("Spawner output limit module initialized.");
	}

	private void loadModuleConfig() {
		ModuleConfigLoader configLoader = new ModuleConfigLoader(plugin, "spawner-output-limit.yml");

		configFile = configLoader.getConfigFile();
		moduleConfig = configLoader.getConfig();
	}

	public boolean isEnabled() {
		return moduleConfig.getBoolean("enabled", true);
	}

	public boolean tryConsumeSpawnerResource(Location location, EntityType entityType) {
		String spawnerKey = getSpawnerKey(location);
		String entityTypeName = entityType.name();

		SpawnerPool pool = spawnerPools.computeIfAbsent(
				spawnerKey,
				key -> createSpawnerPool(entityTypeName)
		);

		pool.entityTypeName = entityTypeName;
		pool.lastAccessTime = System.currentTimeMillis();

		applyRegen(pool);

		if (pool.resource <= 0) {
			return false;
		}

		pool.resource--;

		return true;
	}

	public int getRemainingResource(Location location, EntityType entityType) {
		String spawnerKey = getSpawnerKey(location);
		String entityTypeName = entityType.name();

		SpawnerPool pool = spawnerPools.computeIfAbsent(
				spawnerKey,
				key -> createSpawnerPool(entityTypeName)
		);

		pool.entityTypeName = entityTypeName;
		pool.lastAccessTime = System.currentTimeMillis();

		applyRegen(pool);

		return pool.resource;
	}

	public void removeSpawner(Location location) {
		spawnerPools.remove(getSpawnerKey(location));
	}

	public void cleanup() {
		long now = System.currentTimeMillis();

		Iterator<Map.Entry<String, SpawnerPool>> iterator = spawnerPools.entrySet().iterator();

		while (iterator.hasNext()) {
			SpawnerPool pool = iterator.next().getValue();

			applyRegen(pool);

			if (shouldRemovePool(pool, now)) {
				iterator.remove();
			}
		}
	}

	public int getTrackedSpawnerCount() {
		return spawnerPools.size();
	}

	private SpawnerPool createSpawnerPool(String entityTypeName) {
		long now = System.currentTimeMillis();

		return new SpawnerPool(
				entityTypeName,
				getInitialResource(entityTypeName),
				now,
				now
		);
	}

	private void applyRegen(SpawnerPool pool) {
		int maxResource = getMaxResource(pool.entityTypeName);
		int regenAmount = getRegenAmount(pool.entityTypeName);
		int regenIntervalSeconds = getRegenIntervalSeconds(pool.entityTypeName);

		if (regenIntervalSeconds <= 0) {
			return;
		}

		long intervalMillis = regenIntervalSeconds * 1000L;
		long now = System.currentTimeMillis();

		if (pool.resource >= maxResource) {
			pool.resource = maxResource;
			pool.lastRegenTime = now;
			return;
		}

		long passedTime = now - pool.lastRegenTime;

		if (passedTime < intervalMillis) {
			return;
		}

		long regenTimes = passedTime / intervalMillis;
		int totalRegen = (int) regenTimes * regenAmount;

		pool.resource = Math.min(maxResource, pool.resource + totalRegen);
		pool.lastRegenTime += regenTimes * intervalMillis;

		if (pool.resource >= maxResource) {
			pool.resource = maxResource;
			pool.lastRegenTime = now;
		}
	}

	private boolean shouldRemovePool(SpawnerPool pool, long now) {
		if (shouldUnloadWhenFull() && pool.resource >= getMaxResource(pool.entityTypeName)) {
			return true;
		}

		long ttlMillis = getInactiveTtlSeconds() * 1000L;

		return ttlMillis > 0 && now - pool.lastAccessTime >= ttlMillis;
	}

	public int getInitialResource(String entityTypeName) {
		return moduleConfig.getInt(
				"per-entity." + entityTypeName + ".initial-resource",
				moduleConfig.getInt("default.initial-resource", 500)
		);
	}

	public int getMaxResource(String entityTypeName) {
		return moduleConfig.getInt(
				"per-entity." + entityTypeName + ".max-resource",
				moduleConfig.getInt("default.max-resource", 1000)
		);
	}

	public int getRegenAmount(String entityTypeName) {
		return moduleConfig.getInt(
				"per-entity." + entityTypeName + ".regen-amount",
				moduleConfig.getInt("default.regen-amount", 4)
		);
	}

	public int getRegenIntervalSeconds(String entityTypeName) {
		return moduleConfig.getInt(
				"per-entity." + entityTypeName + ".regen-interval-seconds",
				moduleConfig.getInt("default.regen-interval-seconds", 60)
		);
	}

	private boolean shouldUnloadWhenFull() {
		return moduleConfig.getBoolean("cleanup.unload-when-full", false);
	}

	private int getInactiveTtlSeconds() {
		return moduleConfig.getInt("cleanup.inactive-ttl-seconds", 1800);
	}

	private String getSpawnerKey(Location location) {
		return location.getWorld().getName() + "," +
				location.getBlockX() + "," +
				location.getBlockY() + "," +
				location.getBlockZ();
	}

	private static class SpawnerPool {
		private String entityTypeName;
		private int resource;
		private long lastRegenTime;
		private long lastAccessTime;

		private SpawnerPool(
				String entityTypeName,
				int resource,
				long lastRegenTime,
				long lastAccessTime
		) {
			this.entityTypeName = entityTypeName;
			this.resource = resource;
			this.lastRegenTime = lastRegenTime;
			this.lastAccessTime = lastAccessTime;
		}
	}
}