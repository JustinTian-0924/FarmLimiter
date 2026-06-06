package basementhost.randomchad.natural;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public class NaturalSpawnManager {

	private final JavaPlugin plugin;

	private final Map<String, SpawnPool> totalPools = new HashMap<>();
	private final Map<String, Map<String, SpawnPool>> entityPools = new HashMap<>();
	private final Map<String, Long> lastAccessTimes = new HashMap<>();

	private File configFile;
	private YamlConfiguration moduleConfig;

	private File dataFile;
	private YamlConfiguration dataConfig;

	private final Object dataSaveLock = new Object();

	private final AtomicBoolean asyncSaveRunning = new AtomicBoolean(false);
	private final AtomicBoolean asyncSaveQueued = new AtomicBoolean(false);

	public NaturalSpawnManager(JavaPlugin plugin) {
		this.plugin = plugin;
	}

	public void load() {
		loadModuleConfig();
		loadData();
		cleanup();
	}

	private void loadModuleConfig() {
		File modulesFolder = new File(plugin.getDataFolder(), "modules");

		if (!modulesFolder.exists()) {
			modulesFolder.mkdirs();
		}

		configFile = new File(modulesFolder, "natural-spawn-rate-limit.yml");

		if (!configFile.exists()) {
			plugin.saveResource("modules/natural-spawn-rate-limit.yml", false);
		}

		moduleConfig = YamlConfiguration.loadConfiguration(configFile);
	}

	private void loadData() {
		totalPools.clear();
		entityPools.clear();
		lastAccessTimes.clear();

		File dataFolder = new File(plugin.getDataFolder(), "data");

		if (!dataFolder.exists()) {
			dataFolder.mkdirs();
		}

		dataFile = new File(dataFolder, "natural-spawn-rate-limit.yml");

		if (!dataFile.exists()) {
			try {
				dataFile.createNewFile();
			} catch (IOException e) {
				plugin.getLogger().warning("Unable to create data/natural-spawn-rate-limit.yml!");
				e.printStackTrace();
			}
		}

		dataConfig = YamlConfiguration.loadConfiguration(dataFile);

		loadTotalPools();
		loadEntityPools();

		plugin.getLogger().info("Loaded " + totalPools.size() + " chunks of natural spawn total data.");
	}

	private void loadTotalPools() {
		ConfigurationSection section = dataConfig.getConfigurationSection("total-pools");
		if (section == null) {
			return;
		}

		long now = System.currentTimeMillis();

		for (String chunkKey : section.getKeys(false)) {
			int resource = section.getInt(chunkKey + ".resource", getTotalMaxResource());
			long lastRegenTime = section.getLong(chunkKey + ".last-regen-time", now);
			long lastAccessTime = section.getLong(chunkKey + ".last-access-time", now);

			SpawnPool pool = new SpawnPool(resource, lastRegenTime);

			applyTotalRegen(pool);

			totalPools.put(chunkKey, pool);
			lastAccessTimes.put(chunkKey, lastAccessTime);
		}
	}

	private void loadEntityPools() {
		ConfigurationSection section = dataConfig.getConfigurationSection("entity-pools");
		if (section == null) {
			return;
		}

		long now = System.currentTimeMillis();

		for (String chunkKey : section.getKeys(false)) {
			ConfigurationSection chunkSection = section.getConfigurationSection(chunkKey);
			if (chunkSection == null) {
				continue;
			}

			Map<String, SpawnPool> perChunkEntityPools = new HashMap<>();

			for (String entityTypeName : chunkSection.getKeys(false)) {
				int resource = chunkSection.getInt(entityTypeName + ".resource", getEntityMaxResource(entityTypeName));
				long lastRegenTime = chunkSection.getLong(entityTypeName + ".last-regen-time", now);

				SpawnPool pool = new SpawnPool(resource, lastRegenTime);
				applyEntityRegen(entityTypeName, pool);

				perChunkEntityPools.put(entityTypeName, pool);
			}

			if (!perChunkEntityPools.isEmpty()) {
				entityPools.put(chunkKey, perChunkEntityPools);
				lastAccessTimes.putIfAbsent(chunkKey, now);
			}
		}
	}

	public void save() {
		if (dataConfig == null || dataFile == null) {
			return;
		}

		cleanup();

		dataConfig.set("total-pools", null);
		dataConfig.set("entity-pools", null);

		for (Map.Entry<String, SpawnPool> entry : totalPools.entrySet()) {
			String chunkKey = entry.getKey();
			SpawnPool pool = entry.getValue();

			dataConfig.set("total-pools." + chunkKey + ".resource", pool.resource);
			dataConfig.set("total-pools." + chunkKey + ".last-regen-time", pool.lastRegenTime);
			dataConfig.set("total-pools." + chunkKey + ".last-access-time", lastAccessTimes.getOrDefault(chunkKey, System.currentTimeMillis()));
		}

		for (Map.Entry<String, Map<String, SpawnPool>> chunkEntry : entityPools.entrySet()) {
			String chunkKey = chunkEntry.getKey();

			for (Map.Entry<String, SpawnPool> entityEntry : chunkEntry.getValue().entrySet()) {
				String entityTypeName = entityEntry.getKey();
				SpawnPool pool = entityEntry.getValue();

				dataConfig.set("entity-pools." + chunkKey + "." + entityTypeName + ".resource", pool.resource);
				dataConfig.set("entity-pools." + chunkKey + "." + entityTypeName + ".last-regen-time", pool.lastRegenTime);
			}
		}

		synchronized (dataSaveLock) {
			try {
				dataConfig.save(dataFile);
			} catch (IOException e) {
				plugin.getLogger().warning("An error occurred when saving data/natural-spawn-rate-limit.yml!");
				e.printStackTrace();
			}
		}
	}

	public void saveAsync() {
		if (dataFile == null) {
			return;
		}

		if (!asyncSaveRunning.compareAndSet(false, true)) {
			asyncSaveQueued.set(true);
			return;
		}

		cleanup();

		Map<String, SpawnPoolSnapshot> totalSnapshot = new HashMap<>();
		Map<String, Map<String, SpawnPoolSnapshot>> entitySnapshot = new HashMap<>();
		Map<String, Long> accessSnapshot = new HashMap<>(lastAccessTimes);

		for (Map.Entry<String, SpawnPool> entry : totalPools.entrySet()) {
			String chunkKey = entry.getKey();
			SpawnPool pool = entry.getValue();

			totalSnapshot.put(
					chunkKey,
					new SpawnPoolSnapshot(
							pool.resource,
							pool.lastRegenTime
					)
			);
		}

		for (Map.Entry<String, Map<String, SpawnPool>> chunkEntry : entityPools.entrySet()) {
			String chunkKey = chunkEntry.getKey();

			Map<String, SpawnPoolSnapshot> perEntitySnapshot = new HashMap<>();

			for (Map.Entry<String, SpawnPool> entityEntry : chunkEntry.getValue().entrySet()) {
				String entityTypeName = entityEntry.getKey();
				SpawnPool pool = entityEntry.getValue();

				perEntitySnapshot.put(
						entityTypeName,
						new SpawnPoolSnapshot(
								pool.resource,
								pool.lastRegenTime
						)
				);
			}

			if (!perEntitySnapshot.isEmpty()) {
				entitySnapshot.put(chunkKey, perEntitySnapshot);
			}
		}

		File targetFile = dataFile;

		Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
			try {
				YamlConfiguration asyncConfig = new YamlConfiguration();

				for (Map.Entry<String, SpawnPoolSnapshot> entry : totalSnapshot.entrySet()) {
					String chunkKey = entry.getKey();
					SpawnPoolSnapshot pool = entry.getValue();

					asyncConfig.set("total-pools." + chunkKey + ".resource", pool.resource);
					asyncConfig.set("total-pools." + chunkKey + ".last-regen-time", pool.lastRegenTime);
					asyncConfig.set(
							"total-pools." + chunkKey + ".last-access-time",
							accessSnapshot.getOrDefault(chunkKey, System.currentTimeMillis())
					);
				}

				for (Map.Entry<String, Map<String, SpawnPoolSnapshot>> chunkEntry : entitySnapshot.entrySet()) {
					String chunkKey = chunkEntry.getKey();

					for (Map.Entry<String, SpawnPoolSnapshot> entityEntry : chunkEntry.getValue().entrySet()) {
						String entityTypeName = entityEntry.getKey();
						SpawnPoolSnapshot pool = entityEntry.getValue();

						asyncConfig.set("entity-pools." + chunkKey + "." + entityTypeName + ".resource", pool.resource);
						asyncConfig.set("entity-pools." + chunkKey + "." + entityTypeName + ".last-regen-time", pool.lastRegenTime);
					}
				}

				synchronized (dataSaveLock) {
					asyncConfig.save(targetFile);
				}
			} catch (IOException e) {
				plugin.getLogger().warning("An error occurred when asynchronously saving data/natural-spawn-rate-limit.yml!");
				e.printStackTrace();
			} finally {
				asyncSaveRunning.set(false);

				if (asyncSaveQueued.getAndSet(false) && plugin.isEnabled()) {
					Bukkit.getScheduler().runTask(plugin, this::saveAsync);
				}
			}
		});
	}

	public boolean isAsyncSaveRunning() {
		return asyncSaveRunning.get();
	}

	public boolean isAsyncSaveQueued() {
		return asyncSaveQueued.get();
	}

	public void cleanup() {
		int removed = cleanupAndGetRemovedCount();

		if (removed > 0) {
			plugin.getLogger().info("Natural_Spawn_Rate_Limit cleanup removed " + removed + " chunk records. Remaining: " + getTrackedChunkCount());
		}
	}

	public int cleanupAndGetRemovedCount() {
		long now = System.currentTimeMillis();

		int before = getTrackedChunkCount();

		Set<String> allChunkKeys = new HashSet<>();
		allChunkKeys.addAll(totalPools.keySet());
		allChunkKeys.addAll(entityPools.keySet());
		allChunkKeys.addAll(lastAccessTimes.keySet());

		for (String chunkKey : allChunkKeys) {
			SpawnPool totalPool = totalPools.get(chunkKey);

			if (totalPool != null) {
				applyTotalRegen(totalPool);
			}

			Map<String, SpawnPool> perEntityPools = entityPools.get(chunkKey);

			if (perEntityPools != null) {
				Iterator<Map.Entry<String, SpawnPool>> iterator = perEntityPools.entrySet().iterator();

				while (iterator.hasNext()) {
					Map.Entry<String, SpawnPool> entry = iterator.next();
					String entityTypeName = entry.getKey();
					SpawnPool pool = entry.getValue();

					applyEntityRegen(entityTypeName, pool);

					if (shouldUnloadWhenFull() && pool.resource >= getEntityMaxResource(entityTypeName)) {
						iterator.remove();
					}
				}

				if (perEntityPools.isEmpty()) {
					entityPools.remove(chunkKey);
				}
			}

			if (shouldRemoveChunk(chunkKey, now)) {
				totalPools.remove(chunkKey);
				entityPools.remove(chunkKey);
				lastAccessTimes.remove(chunkKey);
			}
		}

		return before - getTrackedChunkCount();
	}

	private boolean shouldRemoveChunk(String chunkKey, long now) {
		Long lastAccess = lastAccessTimes.get(chunkKey);

		if (lastAccess == null) {
			return true;
		}

		long ttlMillis = getInactiveTtlSeconds() * 1000L;

		if (ttlMillis > 0 && now - lastAccess >= ttlMillis) {
			return true;
		}

		if (!shouldUnloadWhenFull()) {
			return false;
		}

		SpawnPool totalPool = totalPools.get(chunkKey);
		Map<String, SpawnPool> perEntityPools = entityPools.get(chunkKey);

		boolean totalFullOrMissing = totalPool == null || totalPool.resource >= getTotalMaxResource();
		boolean noEntityPools = perEntityPools == null || perEntityPools.isEmpty();

		return totalFullOrMissing && noEntityPools;
	}

	public int getTrackedChunkCount() {
		Set<String> allChunkKeys = new HashSet<>();
		allChunkKeys.addAll(totalPools.keySet());
		allChunkKeys.addAll(entityPools.keySet());
		allChunkKeys.addAll(lastAccessTimes.keySet());
		return allChunkKeys.size();
	}

	private void touch(String chunkKey) {
		lastAccessTimes.put(chunkKey, System.currentTimeMillis());
	}

	public boolean isEnabled() {
		return moduleConfig.getBoolean("enabled", true);
	}

	public boolean isDebugEnabled() {
		return moduleConfig.getBoolean("debug.enabled", false);
	}

	public boolean shouldLogAllCreatureSpawns() {
		return moduleConfig.getBoolean("debug.log-all-creature-spawns", false);
	}

	public boolean shouldLogIgnoredSpawnReasons() {
		return moduleConfig.getBoolean("debug.log-ignored-spawn-reasons", true);
	}

	public boolean shouldLogConsume() {
		return moduleConfig.getBoolean("debug.log-consume", true);
	}

	public boolean shouldLogCancel() {
		return moduleConfig.getBoolean("debug.log-cancel", true);
	}

	public void setDebug(boolean enabled, boolean logAllCreatureSpawns) {
		moduleConfig.set("debug.enabled", enabled);
		moduleConfig.set("debug.log-all-creature-spawns", logAllCreatureSpawns);

		saveModuleConfig();
	}

	private void saveModuleConfig() {
		if (moduleConfig == null || configFile == null) {
			return;
		}

		try {
			moduleConfig.save(configFile);
		} catch (IOException e) {
			plugin.getLogger().warning("An error occurred when saving modules/natural-spawn-rate-limit.yml!");
			e.printStackTrace();
		}
	}

	public void debugLog(String message) {
		if (!isDebugEnabled()) {
			return;
		}

		plugin.getLogger().info("[NaturalSpawnDebug] " + message);
	}

	public boolean shouldLimitSpawnReason(CreatureSpawnEvent.SpawnReason spawnReason) {
		List<String> limitedReasons = moduleConfig.getStringList("limited-spawn-reasons");

		if (limitedReasons.isEmpty()) {
			return spawnReason == CreatureSpawnEvent.SpawnReason.NATURAL;
		}

		String currentReasonName = spawnReason.name();

		for (String reasonName : limitedReasons) {
			if (reasonName.equalsIgnoreCase(currentReasonName)) {
				return true;
			}
		}

		return false;
	}

	public boolean tryConsumeSpawnResource(Chunk chunk, EntityType entityType) {
		String chunkKey = getChunkKey(chunk);
		String entityTypeName = entityType.name();

		touch(chunkKey);

		SpawnPool totalPool = totalPools.computeIfAbsent(chunkKey, key -> createTotalPool());
		applyTotalRegen(totalPool);

		if (totalPool.resource <= 0) {
			return false;
		}

		if (hasEntityLimit(entityTypeName)) {
			Map<String, SpawnPool> perChunkEntityPools = entityPools.computeIfAbsent(chunkKey, key -> new HashMap<>());

			SpawnPool entityPool = perChunkEntityPools.computeIfAbsent(
					entityTypeName,
					key -> createEntityPool(entityTypeName)
			);

			applyEntityRegen(entityTypeName, entityPool);

			if (entityPool.resource <= 0) {
				return false;
			}

			entityPool.resource--;
		}

		totalPool.resource--;
		return true;
	}

	public int getRemainingTotalResource(Chunk chunk) {
		String chunkKey = getChunkKey(chunk);

		touch(chunkKey);

		SpawnPool totalPool = totalPools.computeIfAbsent(chunkKey, key -> createTotalPool());
		applyTotalRegen(totalPool);

		return totalPool.resource;
	}

	public int peekRemainingTotalResource(Chunk chunk) {
		String chunkKey = getChunkKey(chunk);
		SpawnPool totalPool = totalPools.get(chunkKey);
		if (totalPool == null) {
			return getTotalMaxResource();
		}
		applyTotalRegen(totalPool);
		if (totalPool.resource >= getTotalMaxResource() && shouldUnloadWhenFull()) {
			return getTotalMaxResource();
		}
		return totalPool.resource;
	}

	public int getRemainingEntityResource(Chunk chunk, EntityType entityType) {
		String entityTypeName = entityType.name();

		if (!hasEntityLimit(entityTypeName)) {
			return -1;
		}

		String chunkKey = getChunkKey(chunk);

		touch(chunkKey);

		Map<String, SpawnPool> perChunkEntityPools = entityPools.computeIfAbsent(chunkKey, key -> new HashMap<>());

		SpawnPool entityPool = perChunkEntityPools.computeIfAbsent(
				entityTypeName,
				key -> createEntityPool(entityTypeName)
		);

		applyEntityRegen(entityTypeName, entityPool);

		return entityPool.resource;
	}

	public int peekRemainingEntityResource(Chunk chunk, EntityType entityType) {
		String entityTypeName = entityType.name();
		if (!hasEntityLimit(entityTypeName)) {
			return -1;
		}
		String chunkKey = getChunkKey(chunk);
		Map<String, SpawnPool> perChunkEntityPools = entityPools.get(chunkKey);
		if (perChunkEntityPools == null) {
			return getEntityMaxResource(entityTypeName);
		}
		SpawnPool entityPool = perChunkEntityPools.get(entityTypeName);
		if (entityPool == null) {
			return getEntityMaxResource(entityTypeName);
		}
		applyEntityRegen(entityTypeName, entityPool);
		if (entityPool.resource >= getEntityMaxResource(entityTypeName) && shouldUnloadWhenFull()) {
			return getEntityMaxResource(entityTypeName);
		}
		return entityPool.resource;
	}

	private SpawnPool createTotalPool() {
		return new SpawnPool(getTotalMaxResource(), System.currentTimeMillis());
	}

	private SpawnPool createEntityPool(String entityTypeName) {
		return new SpawnPool(getEntityMaxResource(entityTypeName), System.currentTimeMillis());
	}

	private void applyTotalRegen(SpawnPool pool) {
		applyRegen(
				pool,
				getTotalMaxResource(),
				getTotalRegenAmount(),
				getTotalRegenIntervalSeconds()
		);
	}

	private void applyEntityRegen(String entityTypeName, SpawnPool pool) {
		applyRegen(
				pool,
				getEntityMaxResource(entityTypeName),
				getEntityRegenAmount(entityTypeName),
				getEntityRegenIntervalSeconds(entityTypeName)
		);
	}

	private void applyRegen(SpawnPool pool, int maxResource, int regenAmount, int regenIntervalSeconds) {
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

	private boolean hasEntityLimit(String entityTypeName) {
		return moduleConfig.isConfigurationSection("per-entity." + entityTypeName);
	}

	public int getTotalMaxResource() {
		return moduleConfig.getInt("total.max-resource", 500);
	}

	public int getTotalRegenIntervalSeconds() {
		return moduleConfig.getInt("total.regen-interval-seconds", 600);
	}

	public int getTotalRegenAmount() {
		return moduleConfig.getInt("total.regen-amount", 25);
	}

	public int getEntityMaxResource(String entityTypeName) {
		return moduleConfig.getInt(
				"per-entity." + entityTypeName + ".max-resource",
				getTotalMaxResource()
		);
	}

	public int getEntityRegenIntervalSeconds(String entityTypeName) {
		return moduleConfig.getInt(
				"per-entity." + entityTypeName + ".regen-interval-seconds",
				getTotalRegenIntervalSeconds()
		);
	}

	public int getEntityRegenAmount(String entityTypeName) {
		return moduleConfig.getInt(
				"per-entity." + entityTypeName + ".regen-amount",
				getTotalRegenAmount()
		);
	}

	private boolean shouldUnloadWhenFull() {
		return moduleConfig.getBoolean("cleanup.unload-when-full", true);
	}

	private int getInactiveTtlSeconds() {
		return moduleConfig.getInt("cleanup.inactive-ttl-seconds", 1800);
	}

	private String getChunkKey(Chunk chunk) {
		String worldId = chunk.getWorld().getUID().toString();
		int x = chunk.getX();
		int z = chunk.getZ();

		return worldId + "_" + x + "_" + z;
	}

	private static class SpawnPool {
		private int resource;
		private long lastRegenTime;

		private SpawnPool(int resource, long lastRegenTime) {
			this.resource = resource;
			this.lastRegenTime = lastRegenTime;
		}
	}

	private static class SpawnPoolSnapshot {
		private final int resource;
		private final long lastRegenTime;
		private SpawnPoolSnapshot(int resource, long lastRegenTime) {
			this.resource = resource;
			this.lastRegenTime = lastRegenTime;
		}
	}
}