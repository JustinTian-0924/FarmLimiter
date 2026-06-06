package basementhost.randomchad.natural;

import basementhost.randomchad.storage.RegionKeyUtil;
import basementhost.randomchad.storage.RegionLoadState;
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

	private final RegionLoadState regionState = new RegionLoadState();

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
		regionState.clear();

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

		if (regionState.isLoaded(regionId)) {
			regionState.touch(regionId);
			return;
		}

		File regionFile = getRegionFile(regionId);

		if (!regionFile.exists()) {
			return;
		}

		YamlConfiguration regionConfig = YamlConfiguration.loadConfiguration(regionFile);
		ConfigurationSection chunksSection = regionConfig.getConfigurationSection("chunks");

		if (chunksSection == null) {
			regionState.markLoaded(regionId);
			enforceLoadedRegionLimit();
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

		regionState.markLoaded(regionId);
		enforceLoadedRegionLimit();
	}

	public void save() {
		cleanup();

		Map<String, Long> regionsToSave = regionState.snapshotDirtyVersions();

		if (regionsToSave.isEmpty()) {
			int unloadedRegions = enforceLoadedRegionLimit();

			if (unloadedRegions > 0) {
				plugin.getLogger().info("Natural_Spawn_Rate_Limit unloaded " + unloadedRegions + " saved regions from memory.");
			}

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

			regionState.clearDirtyIfVersionMatches(regionId, savedVersion);
		}

		int unloadedRegions = enforceLoadedRegionLimit();

		if (unloadedRegions > 0) {
			plugin.getLogger().info("Natural_Spawn_Rate_Limit unloaded " + unloadedRegions + " saved regions from memory.");
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

		Map<String, Long> regionsToSave = regionState.snapshotDirtyVersions();

		if (regionsToSave.isEmpty()) {
			int unloadedRegions = enforceLoadedRegionLimit();

			if (unloadedRegions > 0) {
				plugin.getLogger().info("Natural_Spawn_Rate_Limit unloaded " + unloadedRegions + " saved regions from memory.");
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

						regionState.clearDirtyIfVersionMatches(regionId, savedVersion);
					}

					int unloadedRegions = enforceLoadedRegionLimit();

					if (unloadedRegions > 0) {
						plugin.getLogger().info("Natural_Spawn_Rate_Limit unloaded " + unloadedRegions + " saved regions from memory.");
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
				plugin.getLogger().warning("Unable to delete empty natural spawn region data file: " + regionFile.getPath());
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
			plugin.getLogger().warning("An error occurred when saving natural spawn region data file: " + regionFile.getPath());
			e.printStackTrace();
		}
	}

	public void cleanup() {
		int removed = cleanupAndGetRemovedCount();

		if (removed > 0) {
			plugin.getLogger().info("Natural_Spawn_Rate_Limit cleanup removed " + removed + " chunk records. Remaining loaded tracked chunks: " + getTrackedChunkCount());
		}

		int unloadedRegions = enforceLoadedRegionLimit();

		if (unloadedRegions > 0) {
			plugin.getLogger().info("Natural_Spawn_Rate_Limit unloaded " + unloadedRegions + " saved regions from memory.");
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
		return regionState.getLoadedRegionCount();
	}

	public int getDirtyRegionCount() {
		return regionState.getDirtyRegionCount();
	}

	public int unloadSavedEmptyRegions() {
		if (!shouldUnloadEmptyRegionsAfterSave()) {
			return 0;
		}

		int before = regionState.getLoadedRegionCount();

		for (String regionId : regionState.getLoadedRegionIdsSnapshot()) {
			if (regionState.isDirty(regionId)) {
				continue;
			}

			if (regionHasTrackedChunks(regionId)) {
				continue;
			}

			regionState.removeLoadedRegion(regionId);
		}

		return before - regionState.getLoadedRegionCount();
	}

	public int enforceLoadedRegionLimit() {
		int unloaded = 0;

		unloaded += unloadSavedEmptyRegions();
		unloaded += unloadCleanInactiveOrExcessRegions();

		int maxLoadedRegions = getMaxLoadedRegions();

		if (maxLoadedRegions > 0 && regionState.getLoadedRegionCount() > maxLoadedRegions) {
			plugin.getLogger().warning(
					"Natural_Spawn_Rate_Limit loaded regions are still above limit: " +
							regionState.getLoadedRegionCount() + " / " + maxLoadedRegions +
							". Most remaining regions may be dirty and cannot be safely unloaded yet."
			);
		}

		return unloaded;
	}

	private int unloadCleanInactiveOrExcessRegions() {
		int maxUnloads = getMaxRegionUnloadsPerCleanup();

		if (maxUnloads <= 0) {
			return 0;
		}

		int unloaded = 0;
		long now = System.currentTimeMillis();

		if (shouldUnloadInactiveLoadedRegions()) {
			long ttlMillis = getLoadedRegionInactiveTtlSeconds() * 1000L;

			if (ttlMillis > 0) {
				for (String regionId : regionState.getLoadedRegionIdsSnapshot()) {
					if (unloaded >= maxUnloads) {
						return unloaded;
					}

					if (regionState.isDirty(regionId)) {
						continue;
					}

					long lastAccessTime = regionState.getLastAccessTime(regionId, now);

					if (now - lastAccessTime >= ttlMillis) {
						unloadRegionFromMemory(regionId);
						unloaded++;
					}
				}
			}
		}

		int maxLoadedRegions = getMaxLoadedRegions();

		while (maxLoadedRegions > 0 && regionState.getLoadedRegionCount() > maxLoadedRegions && unloaded < maxUnloads) {
			String oldestCleanRegionId = findOldestCleanLoadedRegionId();

			if (oldestCleanRegionId == null) {
				break;
			}

			unloadRegionFromMemory(oldestCleanRegionId);
			unloaded++;
		}

		return unloaded;
	}

	private String findOldestCleanLoadedRegionId() {
		String oldestRegionId = null;
		long oldestAccessTime = Long.MAX_VALUE;

		for (String regionId : regionState.getLoadedRegionIdsSnapshot()) {
			if (regionState.isDirty(regionId)) {
				continue;
			}

			long lastAccessTime = regionState.getLastAccessTime(regionId, 0L);

			if (lastAccessTime < oldestAccessTime) {
				oldestAccessTime = lastAccessTime;
				oldestRegionId = regionId;
			}
		}

		return oldestRegionId;
	}

	private void unloadRegionFromMemory(String regionId) {
		Set<String> chunkKeys = new HashSet<>();

		chunkKeys.addAll(totalPools.keySet());
		chunkKeys.addAll(entityPools.keySet());
		chunkKeys.addAll(lastAccessTimes.keySet());

		for (String chunkKey : chunkKeys) {
			if (!regionId.equals(getRegionIdFromChunkKey(chunkKey))) {
				continue;
			}

			totalPools.remove(chunkKey);
			entityPools.remove(chunkKey);
			lastAccessTimes.remove(chunkKey);
		}

		regionState.removeLoadedRegion(regionId);
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

	private void touchRegion(String regionId) {
		regionState.touch(regionId);
	}

	private void markRegionDirty(String regionId) {
		regionState.markDirty(regionId);
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
		String regionId = getRegionId(chunk);
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
		markRegionDirty(regionId);

		return true;
	}

	public int getRemainingTotalResource(Chunk chunk) {
		ensureRegionLoaded(chunk);

		String chunkKey = getChunkKey(chunk);
		String regionId = getRegionId(chunk);

		touch(chunkKey);

		SpawnPool totalPool = totalPools.computeIfAbsent(chunkKey, key -> createTotalPool());
		applyTotalRegen(totalPool);

		markRegionDirty(regionId);

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
		String regionId = getRegionId(chunk);

		touch(chunkKey);

		Map<String, SpawnPool> perChunkEntityPools = entityPools.computeIfAbsent(chunkKey, key -> new HashMap<>());

		SpawnPool entityPool = perChunkEntityPools.computeIfAbsent(
				entityTypeName,
				key -> createEntityPool(entityTypeName)
		);

		applyEntityRegen(entityTypeName, entityPool);

		markRegionDirty(regionId);

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

	public int getMaxLoadedRegions() {
		return moduleConfig.getInt("storage.max-loaded-regions", 128);
	}

	private boolean shouldUnloadInactiveLoadedRegions() {
		return moduleConfig.getBoolean("storage.unload-inactive-loaded-regions", true);
	}

	private int getLoadedRegionInactiveTtlSeconds() {
		return moduleConfig.getInt("storage.loaded-region-inactive-ttl-seconds", 900);
	}

	private int getMaxRegionUnloadsPerCleanup() {
		return Math.max(1, moduleConfig.getInt("storage.max-region-unloads-per-cleanup", 8));
	}

	private String getChunkKey(Chunk chunk) {
		return RegionKeyUtil.getChunkKey(chunk);
	}

	private String getRegionId(Chunk chunk) {
		return RegionKeyUtil.getRegionId(chunk, getRegionSizeChunks());
	}

	private String getRegionIdFromChunkKey(String chunkKey) {
		return RegionKeyUtil.getRegionIdFromChunkKey(chunkKey, getRegionSizeChunks());
	}

	private File getRegionFile(String regionId) {
		return RegionKeyUtil.getRegionFile(dataRootFolder, regionId);
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