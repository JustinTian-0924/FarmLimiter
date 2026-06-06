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

	private final Set<String> loadedRegionIds = new HashSet<>();
	private final Map<String, Long> dirtyRegionVersions = new HashMap<>();

	private long dirtyVersionCounter = 0L;

	private File configFile;
	private YamlConfiguration moduleConfig;

	private File dataRootFolder;

	private final Object dataSaveLock = new Object();

	private final AtomicBoolean asyncSaveRunning = new AtomicBoolean(false);
	private final AtomicBoolean asyncSaveQueued = new AtomicBoolean(false);

	public NaturalSpawnManager(JavaPlugin plugin) {
		this.plugin = plugin;
	}

	public void load() {
		loadModuleConfig();
		loadDataRoot();

		totalPools.clear();
		entityPools.clear();
		lastAccessTimes.clear();
		loadedRegionIds.clear();
		dirtyRegionVersions.clear();
		dirtyVersionCounter = 0L;

		plugin.getLogger().info("Natural spawn regional data storage initialized.");
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

	private void loadDataRoot() {
		File dataFolder = new File(plugin.getDataFolder(), "data");

		if (!dataFolder.exists()) {
			dataFolder.mkdirs();
		}

		dataRootFolder = new File(dataFolder, "natural-spawn-rate-limit");

		if (!dataRootFolder.exists()) {
			dataRootFolder.mkdirs();
		}
	}

	private void ensureRegionLoaded(Chunk chunk) {
		String regionId = getRegionId(chunk);

		if (loadedRegionIds.contains(regionId)) {
			return;
		}

		File regionFile = getRegionFile(regionId);

		if (!regionFile.exists()) {
			return;
		}

		YamlConfiguration regionConfig = YamlConfiguration.loadConfiguration(regionFile);
		ConfigurationSection chunksSection = regionConfig.getConfigurationSection("chunks");

		if (chunksSection == null) {
			loadedRegionIds.add(regionId);
			return;
		}

		long now = System.currentTimeMillis();

		for (String chunkKey : chunksSection.getKeys(false)) {
			String basePath = "chunks." + chunkKey;

			long lastAccessTime = regionConfig.getLong(basePath + ".last-access-time", now);

			if (regionConfig.isConfigurationSection(basePath + ".total")) {
				int resource = regionConfig.getInt(basePath + ".total.resource", getTotalMaxResource());
				long lastRegenTime = regionConfig.getLong(basePath + ".total.last-regen-time", now);

				SpawnPool totalPool = new SpawnPool(resource, lastRegenTime);
				applyTotalRegen(totalPool);

				totalPools.put(chunkKey, totalPool);
			}

			ConfigurationSection entitiesSection = regionConfig.getConfigurationSection(basePath + ".entities");

			if (entitiesSection != null) {
				Map<String, SpawnPool> perChunkEntityPools = new HashMap<>();

				for (String entityTypeName : entitiesSection.getKeys(false)) {
					int resource = entitiesSection.getInt(entityTypeName + ".resource", getEntityMaxResource(entityTypeName));
					long lastRegenTime = entitiesSection.getLong(entityTypeName + ".last-regen-time", now);

					SpawnPool entityPool = new SpawnPool(resource, lastRegenTime);
					applyEntityRegen(entityTypeName, entityPool);

					perChunkEntityPools.put(entityTypeName, entityPool);
				}

				if (!perChunkEntityPools.isEmpty()) {
					entityPools.put(chunkKey, perChunkEntityPools);
				}
			}

			lastAccessTimes.put(chunkKey, lastAccessTime);
		}

		loadedRegionIds.add(regionId);
	}

	public void save() {
		cleanup();

		Map<String, Long> regionsToSave = new HashMap<>(dirtyRegionVersions);

		if (regionsToSave.isEmpty()) {
			return;
		}

		Map<String, RegionSnapshot> snapshots = createRegionSnapshots(regionsToSave.keySet());

		synchronized (dataSaveLock) {
			for (Map.Entry<String, RegionSnapshot> entry : snapshots.entrySet()) {
				writeRegionSnapshot(entry.getKey(), entry.getValue());
			}
		}

		for (Map.Entry<String, Long> entry : regionsToSave.entrySet()) {
			String regionId = entry.getKey();
			long savedVersion = entry.getValue();

			Long currentVersion = dirtyRegionVersions.get(regionId);

			if (currentVersion != null && currentVersion == savedVersion) {
				dirtyRegionVersions.remove(regionId);
			}
		}

		int unloadedRegions = unloadSavedEmptyRegions();

		if (unloadedRegions > 0) {
			plugin.getLogger().info("Natural_Spawn_Rate_Limit unloaded " + unloadedRegions + " empty saved regions from memory.");
		}
	}

	public void saveAsync() {
		if (dataRootFolder == null) {
			return;
		}

		if (!asyncSaveRunning.compareAndSet(false, true)) {
			asyncSaveQueued.set(true);
			return;
		}

		cleanup();

		Map<String, Long> regionsToSave = new HashMap<>(dirtyRegionVersions);
		if (regionsToSave.isEmpty()) {
			int unloadedRegions = unloadSavedEmptyRegions();
			if (unloadedRegions > 0) {
				plugin.getLogger().info("Natural_Spawn_Rate_Limit unloaded " + unloadedRegions + " empty saved regions from memory.");
			}
			asyncSaveRunning.set(false);
			if (asyncSaveQueued.getAndSet(false) && plugin.isEnabled()) {
				Bukkit.getScheduler().runTask(plugin, this::saveAsync);
			}
			return;
		}

		Map<String, RegionSnapshot> snapshots = createRegionSnapshots(regionsToSave.keySet());

		Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
			try {
				synchronized (dataSaveLock) {
					for (Map.Entry<String, RegionSnapshot> entry : snapshots.entrySet()) {
						writeRegionSnapshot(entry.getKey(), entry.getValue());
					}
				}
			} finally {
				Bukkit.getScheduler().runTask(plugin, () -> {
					for (Map.Entry<String, Long> entry : regionsToSave.entrySet()) {
						String regionId = entry.getKey();
						long savedVersion = entry.getValue();

						Long currentVersion = dirtyRegionVersions.get(regionId);

						if (currentVersion != null && currentVersion == savedVersion) {
							dirtyRegionVersions.remove(regionId);
						}
					}

					int unloadedRegions = unloadSavedEmptyRegions();

					if (unloadedRegions > 0) {
						plugin.getLogger().info("Natural_Spawn_Rate_Limit unloaded " + unloadedRegions + " empty saved regions from memory.");
					}

					asyncSaveRunning.set(false);

					if (asyncSaveQueued.getAndSet(false) && plugin.isEnabled()) {
						saveAsync();
					}
				});
			}
		});
	}

	private Map<String, RegionSnapshot> createRegionSnapshots(Set<String> regionIds) {
		Map<String, RegionSnapshot> snapshots = new HashMap<>();

		for (String regionId : regionIds) {
			RegionSnapshot snapshot = new RegionSnapshot();

			Set<String> chunkKeys = new HashSet<>();
			chunkKeys.addAll(totalPools.keySet());
			chunkKeys.addAll(entityPools.keySet());
			chunkKeys.addAll(lastAccessTimes.keySet());

			for (String chunkKey : chunkKeys) {
				String chunkRegionId = getRegionIdFromChunkKey(chunkKey);

				if (!regionId.equals(chunkRegionId)) {
					continue;
				}

				ChunkSnapshot chunkSnapshot = new ChunkSnapshot();

				SpawnPool totalPool = totalPools.get(chunkKey);

				if (totalPool != null) {
					chunkSnapshot.totalPool = new SpawnPoolSnapshot(
							totalPool.resource,
							totalPool.lastRegenTime
					);
				}

				Map<String, SpawnPool> perEntityPools = entityPools.get(chunkKey);

				if (perEntityPools != null) {
					for (Map.Entry<String, SpawnPool> entityEntry : perEntityPools.entrySet()) {
						SpawnPool pool = entityEntry.getValue();

						chunkSnapshot.entityPools.put(
								entityEntry.getKey(),
								new SpawnPoolSnapshot(
										pool.resource,
										pool.lastRegenTime
								)
						);
					}
				}

				chunkSnapshot.lastAccessTime = lastAccessTimes.getOrDefault(chunkKey, System.currentTimeMillis());

				if (chunkSnapshot.totalPool != null || !chunkSnapshot.entityPools.isEmpty()) {
					snapshot.chunks.put(chunkKey, chunkSnapshot);
				}
			}

			snapshots.put(regionId, snapshot);
		}

		return snapshots;
	}

	private void writeRegionSnapshot(String regionId, RegionSnapshot snapshot) {
		File regionFile = getRegionFile(regionId);

		if (snapshot.chunks.isEmpty()) {
			if (regionFile.exists() && !regionFile.delete()) {
				plugin.getLogger().warning("Unable to delete empty region data file: " + regionFile.getPath());
			}
			return;
		}

		File parentFolder = regionFile.getParentFile();

		if (!parentFolder.exists()) {
			parentFolder.mkdirs();
		}

		YamlConfiguration regionConfig = new YamlConfiguration();

		for (Map.Entry<String, ChunkSnapshot> chunkEntry : snapshot.chunks.entrySet()) {
			String chunkKey = chunkEntry.getKey();
			ChunkSnapshot chunkSnapshot = chunkEntry.getValue();

			String basePath = "chunks." + chunkKey;

			regionConfig.set(basePath + ".last-access-time", chunkSnapshot.lastAccessTime);

			if (chunkSnapshot.totalPool != null) {
				regionConfig.set(basePath + ".total.resource", chunkSnapshot.totalPool.resource);
				regionConfig.set(basePath + ".total.last-regen-time", chunkSnapshot.totalPool.lastRegenTime);
			}

			for (Map.Entry<String, SpawnPoolSnapshot> entityEntry : chunkSnapshot.entityPools.entrySet()) {
				String entityTypeName = entityEntry.getKey();
				SpawnPoolSnapshot pool = entityEntry.getValue();

				regionConfig.set(basePath + ".entities." + entityTypeName + ".resource", pool.resource);
				regionConfig.set(basePath + ".entities." + entityTypeName + ".last-regen-time", pool.lastRegenTime);
			}
		}

		try {
			regionConfig.save(regionFile);
		} catch (IOException e) {
			plugin.getLogger().warning("An error occurred when saving region data file: " + regionFile.getPath());
			e.printStackTrace();
		}
	}

	public void cleanup() {
		int removed = cleanupAndGetRemovedCount();
		if (removed > 0) {
			plugin.getLogger().info("Natural_Spawn_Rate_Limit cleanup removed " + removed + " chunk records. Remaining loaded tracked chunks: " + getTrackedChunkCount());
		}
		int unloadedRegions = unloadSavedEmptyRegions();
		if (unloadedRegions > 0) {
			plugin.getLogger().info("Natural_Spawn_Rate_Limit unloaded " + unloadedRegions + " empty saved regions from memory.");
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
			String regionId = getRegionIdFromChunkKey(chunkKey);

			SpawnPool totalPool = totalPools.get(chunkKey);

			if (totalPool != null) {
				int beforeResource = totalPool.resource;
				long beforeRegenTime = totalPool.lastRegenTime;

				applyTotalRegen(totalPool);

				if (beforeResource != totalPool.resource || beforeRegenTime != totalPool.lastRegenTime) {
					markRegionDirty(regionId);
				}
			}

			Map<String, SpawnPool> perEntityPools = entityPools.get(chunkKey);

			if (perEntityPools != null) {
				Iterator<Map.Entry<String, SpawnPool>> iterator = perEntityPools.entrySet().iterator();

				while (iterator.hasNext()) {
					Map.Entry<String, SpawnPool> entry = iterator.next();
					String entityTypeName = entry.getKey();
					SpawnPool pool = entry.getValue();

					int beforeResource = pool.resource;
					long beforeRegenTime = pool.lastRegenTime;

					applyEntityRegen(entityTypeName, pool);

					if (beforeResource != pool.resource || beforeRegenTime != pool.lastRegenTime) {
						markRegionDirty(regionId);
					}

					if (shouldUnloadWhenFull() && pool.resource >= getEntityMaxResource(entityTypeName)) {
						iterator.remove();
						markRegionDirty(regionId);
					}
				}

				if (perEntityPools.isEmpty()) {
					entityPools.remove(chunkKey);
					markRegionDirty(regionId);
				}
			}

			if (shouldRemoveChunk(chunkKey, now)) {
				totalPools.remove(chunkKey);
				entityPools.remove(chunkKey);
				lastAccessTimes.remove(chunkKey);
				markRegionDirty(regionId);
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

	public int getLoadedRegionCount() {
		return loadedRegionIds.size();
	}

	public int getDirtyRegionCount() {
		return dirtyRegionVersions.size();
	}

	public int unloadSavedEmptyRegions() {
		if (!shouldUnloadEmptyRegionsAfterSave()) {
			return 0;
		}

		int before = loadedRegionIds.size();

		Iterator<String> iterator = loadedRegionIds.iterator();

		while (iterator.hasNext()) {
			String regionId = iterator.next();

			if (dirtyRegionVersions.containsKey(regionId)) {
				continue;
			}

			if (regionHasTrackedChunks(regionId)) {
				continue;
			}

			iterator.remove();
		}

		return before - loadedRegionIds.size();
	}

	private boolean regionHasTrackedChunks(String regionId) {
		for (String chunkKey : totalPools.keySet()) {
			if (regionId.equals(getRegionIdFromChunkKey(chunkKey))) {
				return true;
			}
		}

		for (String chunkKey : entityPools.keySet()) {
			if (regionId.equals(getRegionIdFromChunkKey(chunkKey))) {
				return true;
			}
		}

		for (String chunkKey : lastAccessTimes.keySet()) {
			if (regionId.equals(getRegionIdFromChunkKey(chunkKey))) {
				return true;
			}
		}

		return false;
	}

	public boolean isAsyncSaveRunning() {
		return asyncSaveRunning.get();
	}

	public boolean isAsyncSaveQueued() {
		return asyncSaveQueued.get();
	}

	private void touch(String chunkKey) {
		lastAccessTimes.put(chunkKey, System.currentTimeMillis());
		markRegionDirty(getRegionIdFromChunkKey(chunkKey));
	}

	private void markRegionDirty(String regionId) {
		loadedRegionIds.add(regionId);
		dirtyVersionCounter++;
		dirtyRegionVersions.put(regionId, dirtyVersionCounter);
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
		ensureRegionLoaded(chunk);

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
		markRegionDirty(getRegionId(chunk));

		return true;
	}

	public int getRemainingTotalResource(Chunk chunk) {
		ensureRegionLoaded(chunk);

		String chunkKey = getChunkKey(chunk);

		touch(chunkKey);

		SpawnPool totalPool = totalPools.computeIfAbsent(chunkKey, key -> createTotalPool());
		applyTotalRegen(totalPool);

		markRegionDirty(getRegionId(chunk));

		return totalPool.resource;
	}

	public int peekRemainingTotalResource(Chunk chunk) {
		ensureRegionLoaded(chunk);

		String chunkKey = getChunkKey(chunk);

		SpawnPool totalPool = totalPools.get(chunkKey);

		if (totalPool == null) {
			return getTotalMaxResource();
		}

		applyTotalRegen(totalPool);

		return totalPool.resource;
	}

	public int getRemainingEntityResource(Chunk chunk, EntityType entityType) {
		String entityTypeName = entityType.name();

		if (!hasEntityLimit(entityTypeName)) {
			return -1;
		}

		ensureRegionLoaded(chunk);

		String chunkKey = getChunkKey(chunk);

		touch(chunkKey);

		Map<String, SpawnPool> perChunkEntityPools = entityPools.computeIfAbsent(chunkKey, key -> new HashMap<>());

		SpawnPool entityPool = perChunkEntityPools.computeIfAbsent(
				entityTypeName,
				key -> createEntityPool(entityTypeName)
		);

		applyEntityRegen(entityTypeName, entityPool);

		markRegionDirty(getRegionId(chunk));

		return entityPool.resource;
	}

	public int peekRemainingEntityResource(Chunk chunk, EntityType entityType) {
		String entityTypeName = entityType.name();

		if (!hasEntityLimit(entityTypeName)) {
			return -1;
		}

		ensureRegionLoaded(chunk);

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

	private int getRegionSizeChunks() {
		return Math.max(1, moduleConfig.getInt("storage.region-size-chunks", 32));
	}

	private boolean shouldUnloadEmptyRegionsAfterSave() {
		return moduleConfig.getBoolean("storage.unload-empty-regions-after-save", true);
	}

	private String getChunkKey(Chunk chunk) {
		String worldId = chunk.getWorld().getUID().toString();
		int x = chunk.getX();
		int z = chunk.getZ();

		return worldId + "_" + x + "_" + z;
	}

	private String getRegionId(Chunk chunk) {
		String worldId = chunk.getWorld().getUID().toString();
		int regionSize = getRegionSizeChunks();

		int regionX = Math.floorDiv(chunk.getX(), regionSize);
		int regionZ = Math.floorDiv(chunk.getZ(), regionSize);

		return worldId + "_" + regionX + "_" + regionZ;
	}

	private String getRegionIdFromChunkKey(String chunkKey) {
		String[] parts = chunkKey.split("_");

		if (parts.length != 3) {
			return "unknown_0_0";
		}

		String worldId = parts[0];
		int chunkX;
		int chunkZ;

		try {
			chunkX = Integer.parseInt(parts[1]);
			chunkZ = Integer.parseInt(parts[2]);
		} catch (NumberFormatException exception) {
			return worldId + "_0_0";
		}

		int regionSize = getRegionSizeChunks();

		int regionX = Math.floorDiv(chunkX, regionSize);
		int regionZ = Math.floorDiv(chunkZ, regionSize);

		return worldId + "_" + regionX + "_" + regionZ;
	}

	private File getRegionFile(String regionId) {
		String[] parts = regionId.split("_");

		if (parts.length != 3) {
			return new File(dataRootFolder, "unknown/r.0.0.yml");
		}

		String worldId = parts[0];
		String regionX = parts[1];
		String regionZ = parts[2];

		File worldFolder = new File(dataRootFolder, worldId);

		return new File(worldFolder, "r." + regionX + "." + regionZ + ".yml");
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

	private static class ChunkSnapshot {
		private SpawnPoolSnapshot totalPool;
		private final Map<String, SpawnPoolSnapshot> entityPools = new HashMap<>();
		private long lastAccessTime;
	}

	private static class RegionSnapshot {
		private final Map<String, ChunkSnapshot> chunks = new HashMap<>();
	}
}