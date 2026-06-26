package basementhost.randomchad.spawner;

import basementhost.randomchad.storage.*;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public class SpawnerManager {

	private final JavaPlugin plugin;

	private final Map<String, SpawnerPool> spawnerPools = new HashMap<>();
	private final RegionLoadState regionState = new RegionLoadState();
	private final AsyncSaveGuard asyncSaveGuard = new AsyncSaveGuard();

	private File configFile;
	private YamlConfiguration moduleConfig;
	private RegionStorageConfig storageConfig;

	private File dataRootFolder;

	private final Object dataSaveLock = new Object();
	private final Map<String, Long> emptyActionbarNotifyTimes = new HashMap<>();

	public SpawnerManager(JavaPlugin plugin) {
		this.plugin = plugin;
	}

	public void load() {
		loadModuleConfig();
		loadDataRoot();

		spawnerPools.clear();
		regionState.clear();
		asyncSaveGuard.clear();

		plugin.getLogger().info("Spawner output limit regional data storage initialized.");
	}

	private void loadModuleConfig() {
		ModuleConfigLoader configLoader = new ModuleConfigLoader(plugin, "spawner-output-limit.yml");

		configFile = configLoader.getConfigFile();
		moduleConfig = configLoader.getConfig();
		storageConfig = new RegionStorageConfig(moduleConfig);
	}

	private void loadDataRoot() {
		dataRootFolder = DataRootLoader.load(plugin, "spawner-output-limit");
	}

	public boolean isEnabled() {
		return moduleConfig.getBoolean("enabled", true);
	}

	private void ensureRegionLoaded(Location location) {
		String regionId = getRegionId(location);

		if (regionState.isLoaded(regionId)) {
			regionState.touch(regionId);
			return;
		}

		File regionFile = getRegionFile(regionId);

		if (!regionFile.exists()) {
			regionState.markLoaded(regionId);
			enforceLoadedRegionLimit();
			return;
		}

		YamlConfiguration regionConfig = YamlConfiguration.loadConfiguration(regionFile);
		ConfigurationSection spawnersSection = regionConfig.getConfigurationSection("spawners");

		if (spawnersSection == null) {
			regionState.markLoaded(regionId);
			enforceLoadedRegionLimit();
			return;
		}

		long now = System.currentTimeMillis();

		for (String spawnerKey : spawnersSection.getKeys(false)) {
			String basePath = "spawners." + spawnerKey;

			String entityTypeName = regionConfig.getString(basePath + ".entity-type", "UNKNOWN");
			int resource = regionConfig.getInt(basePath + ".resource", getInitialResource(entityTypeName));
			long lastRegenTime = regionConfig.getLong(basePath + ".last-regen-time", now);
			long lastAccessTime = regionConfig.getLong(basePath + ".last-access-time", now);

			SpawnerPool pool = new SpawnerPool(
					regionId,
					entityTypeName,
					resource,
					lastRegenTime,
					lastAccessTime
			);
			if (shouldRegenWhileChunkUnloaded()) {
				applyRegen(pool);
			} else {
				pool.lastRegenTime = now;
			}
			if (shouldRemovePool(pool, now)) {
				markRegionDirty(regionId);
				continue;
			}

			spawnerPools.put(spawnerKey, pool);
		}

		regionState.markLoaded(regionId);
		enforceLoadedRegionLimit();
	}

	public boolean tryConsumeSpawnerResource(Location location, EntityType entityType) {
		ensureRegionLoaded(location);

		String spawnerKey = getSpawnerKey(location);
		String regionId = getRegionId(location);
		String entityTypeName = entityType.name();

		SpawnerPool pool = spawnerPools.computeIfAbsent(
				spawnerKey,
				key -> createSpawnerPool(regionId, entityTypeName)
		);

		pool.entityTypeName = entityTypeName;
		pool.lastAccessTime = System.currentTimeMillis();

		touchRegion(regionId);
		markRegionDirty(regionId);

		applyRegen(pool);

		if (pool.resource <= 0) {
			return false;
		}

		pool.resource--;

		markRegionDirty(regionId);

		return true;
	}

	public int getRemainingResource(Location location, EntityType entityType) {
		ensureRegionLoaded(location);

		String spawnerKey = getSpawnerKey(location);
		String regionId = getRegionId(location);
		String entityTypeName = entityType.name();

		SpawnerPool pool = spawnerPools.computeIfAbsent(
				spawnerKey,
				key -> createSpawnerPool(regionId, entityTypeName)
		);

		pool.entityTypeName = entityTypeName;
		pool.lastAccessTime = System.currentTimeMillis();

		touchRegion(regionId);
		markRegionDirty(regionId);

		applyRegen(pool);

		return pool.resource;
	}

	public void removeSpawner(Location location) {
		String spawnerKey = getSpawnerKey(location);
		String regionId = getRegionId(location);

		ensureRegionLoaded(location);

		if (spawnerPools.remove(spawnerKey) != null) {
			markRegionDirty(regionId);
		}
	}

	public void cleanup() {
		int removed = cleanupAndGetRemovedCount();

		if (removed > 0) {
			plugin.getLogger().info("Spawner_Output_Limit cleanup removed " + removed + " spawner records. Remaining tracked spawners: " + getTrackedSpawnerCount());
		}

		int unloadedRegions = enforceLoadedRegionLimit();

		RegionSaveCompletionHelper.logUnloadedRegions(plugin, "Spawner_Output_Limit", unloadedRegions);
	}

	public int cleanupAndGetRemovedCount() {
		if (shouldRegenWhileInactiveLoaded()) {
			refreshLoadedChunkSpawnerResources();
		}
		long now = System.currentTimeMillis();

		int before = spawnerPools.size();

		Iterator<Map.Entry<String, SpawnerPool>> iterator = spawnerPools.entrySet().iterator();

		while (iterator.hasNext()) {
			Map.Entry<String, SpawnerPool> entry = iterator.next();
			SpawnerPool pool = entry.getValue();

			if (shouldRegenWhileInactiveLoaded()) {
				int beforeResource = pool.resource;
				long beforeRegenTime = pool.lastRegenTime;
				applyRegen(pool);
				if (beforeResource != pool.resource || beforeRegenTime != pool.lastRegenTime) {
					markRegionDirty(pool.regionId);
				}
			}

			if (shouldRemovePool(pool, now)) {
				iterator.remove();
				markRegionDirty(pool.regionId);
			}
		}

		return before - spawnerPools.size();
	}

	public void save() {
		cleanup();

		Map<String, Long> regionsToSave = regionState.snapshotDirtyVersions();

		if (regionsToSave.isEmpty()) {
			int unloadedRegions = enforceLoadedRegionLimit();

			RegionSaveCompletionHelper.logUnloadedRegions(plugin, "Spawner_Output_Limit", unloadedRegions);

			return;
		}

		Map<String, RegionSnapshot> snapshots = createRegionSnapshots(regionsToSave.keySet());

		RegionSnapshotWriteHelper.writeSnapshots(
				dataSaveLock,
				snapshots,
				this::writeRegionSnapshot
		);

		RegionSaveCompletionHelper.clearSavedDirtyRegions(regionState, regionsToSave);

		int unloadedRegions = enforceLoadedRegionLimit();

		RegionSaveCompletionHelper.logUnloadedRegions(plugin, "Spawner_Output_Limit", unloadedRegions);
	}

	public void saveAsync() {
		if (dataRootFolder == null) {
			return;
		}

		if (!asyncSaveGuard.tryStart()) {
			return;
		}

		cleanup();

		Map<String, Long> regionsToSave = regionState.snapshotDirtyVersions();

		if (regionsToSave.isEmpty()) {
			int unloadedRegions = enforceLoadedRegionLimit();

			RegionSaveCompletionHelper.logUnloadedRegions(plugin, "Spawner_Output_Limit", unloadedRegions);

			asyncSaveGuard.finish();
			if (asyncSaveGuard.consumeQueued() && plugin.isEnabled()) {
				Bukkit.getScheduler().runTask(plugin, this::saveAsync);
			}

			return;
		}

		Map<String, RegionSnapshot> snapshots = createRegionSnapshots(regionsToSave.keySet());

		Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
			try {
				RegionSnapshotWriteHelper.writeSnapshots(
						dataSaveLock,
						snapshots,
						this::writeRegionSnapshot
				);
			} finally {
				Bukkit.getScheduler().runTask(plugin, () -> {
					RegionSaveCompletionHelper.clearSavedDirtyRegions(regionState, regionsToSave);

					int unloadedRegions = enforceLoadedRegionLimit();

					RegionSaveCompletionHelper.logUnloadedRegions(plugin, "Spawner_Output_Limit", unloadedRegions);

					asyncSaveGuard.finish();
					if (asyncSaveGuard.consumeQueued() && plugin.isEnabled()) {
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

			for (Map.Entry<String, SpawnerPool> entry : spawnerPools.entrySet()) {
				SpawnerPool pool = entry.getValue();

				if (!regionId.equals(pool.regionId)) {
					continue;
				}

				SpawnerSnapshot spawnerSnapshot = new SpawnerSnapshot(
						pool.entityTypeName,
						pool.resource,
						pool.lastRegenTime,
						pool.lastAccessTime
				);

				snapshot.spawners.put(entry.getKey(), spawnerSnapshot);
			}

			snapshots.put(regionId, snapshot);
		}

		return snapshots;
	}

	private void writeRegionSnapshot(String regionId, RegionSnapshot snapshot) {
		File regionFile = getRegionFile(regionId);

		if (snapshot.spawners.isEmpty()) {
			RegionFileIoHelper.deleteEmptyRegionFile(
					plugin,
					regionFile,
					"Unable to delete empty spawner region data file"
			);
			return;
		}

		RegionFileIoHelper.ensureParentFolderExists(regionFile);

		YamlConfiguration regionConfig = new YamlConfiguration();

		for (Map.Entry<String, SpawnerSnapshot> entry : snapshot.spawners.entrySet()) {
			String spawnerKey = entry.getKey();
			SpawnerSnapshot snapshotPool = entry.getValue();

			String basePath = "spawners." + spawnerKey;

			regionConfig.set(basePath + ".entity-type", snapshotPool.entityTypeName);
			regionConfig.set(basePath + ".resource", snapshotPool.resource);
			regionConfig.set(basePath + ".last-regen-time", snapshotPool.lastRegenTime);
			regionConfig.set(basePath + ".last-access-time", snapshotPool.lastAccessTime);
		}

		RegionFileIoHelper.saveRegionConfig(
				plugin,
				regionConfig,
				regionFile,
				"An error occurred when saving spawner region data file"
		);
	}

	public int enforceLoadedRegionLimit() {
		return RegionUnloadHelper.enforceLoadedRegionLimit(
				plugin,
				"Spawner_Output_Limit",
				regionState,
				shouldUnloadEmptyRegionsAfterSave(),
				getMaxLoadedRegions(),
				shouldUnloadInactiveLoadedRegions(),
				getLoadedRegionInactiveTtlSeconds(),
				getMaxRegionUnloadsPerCleanup(),
				this::regionHasTrackedSpawners,
				this::unloadRegionFromMemory
		);
	}

	private void unloadRegionFromMemory(String regionId) {
		Set<String> spawnerKeys = new HashSet<>(spawnerPools.keySet());

		for (String spawnerKey : spawnerKeys) {
			SpawnerPool pool = spawnerPools.get(spawnerKey);

			if (pool != null && regionId.equals(pool.regionId)) {
				spawnerPools.remove(spawnerKey);
			}
		}

		regionState.removeLoadedRegion(regionId);
	}

	private boolean regionHasTrackedSpawners(String regionId) {
		for (SpawnerPool pool : spawnerPools.values()) {
			if (regionId.equals(pool.regionId)) {
				return true;
			}
		}

		return false;
	}

	public int getTrackedSpawnerCount() {
		return spawnerPools.size();
	}

	public int getLoadedRegionCount() {
		return regionState.getLoadedRegionCount();
	}

	public int getDirtyRegionCount() {
		return regionState.getDirtyRegionCount();
	}

	public boolean isAsyncSaveRunning() {
		return asyncSaveGuard.isRunning();
	}

	public boolean isAsyncSaveQueued() {
		return asyncSaveGuard.isQueued();
	}

	private SpawnerPool createSpawnerPool(String regionId, String entityTypeName) {
		long now = System.currentTimeMillis();

		return new SpawnerPool(
				regionId,
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
		int value = moduleConfig.getInt(
				"per-entity." + entityTypeName + ".initial-resource",
				moduleConfig.getInt("default.initial-resource", 500)
		);

		return Math.max(0, value);
	}

	public int getMaxResource(String entityTypeName) {
		int value = moduleConfig.getInt(
				"per-entity." + entityTypeName + ".max-resource",
				moduleConfig.getInt("default.max-resource", 1000)
		);
		return Math.max(0, value);
	}

	public int getRegenAmount(String entityTypeName) {
		int value = moduleConfig.getInt(
				"per-entity." + entityTypeName + ".regen-amount",
				moduleConfig.getInt("default.regen-amount", 4)
		);
		return Math.max(0, value);
	}

	public int getRegenIntervalSeconds(String entityTypeName) {
		int value = moduleConfig.getInt(
				"per-entity." + entityTypeName + ".regen-interval-seconds",
				moduleConfig.getInt("default.regen-interval-seconds", 60)
		);
		return Math.max(1, value);
	}

	private boolean shouldUnloadWhenFull() {
		return moduleConfig.getBoolean("cleanup.unload-when-full", false);
	}

	private int getInactiveTtlSeconds() {
		return moduleConfig.getInt("cleanup.inactive-ttl-seconds", 1800);
	}

	private int getRegionSizeChunks() {
		return storageConfig.getRegionSizeChunks();
	}

	private boolean shouldUnloadEmptyRegionsAfterSave() {
		return storageConfig.shouldUnloadEmptyRegionsAfterSave();
	}

	public int getMaxLoadedRegions() {
		return storageConfig.getMaxLoadedRegions();
	}

	private boolean shouldUnloadInactiveLoadedRegions() {
		return storageConfig.shouldUnloadInactiveLoadedRegions();
	}

	private int getLoadedRegionInactiveTtlSeconds() {
		return storageConfig.getLoadedRegionInactiveTtlSeconds();
	}

	private int getMaxRegionUnloadsPerCleanup() {
		return storageConfig.getMaxRegionUnloadsPerCleanup();
	}

	private void touchRegion(String regionId) {
		regionState.touch(regionId);
	}

	private void markRegionDirty(String regionId) {
		regionState.markDirty(regionId);
	}

	private String getSpawnerKey(Location location) {
		return location.getWorld().getName() + "," +
				location.getBlockX() + "," +
				location.getBlockY() + "," +
				location.getBlockZ();
	}

	private String getRegionId(Location location) {
		return RegionKeyUtil.getRegionId(location.getChunk(), getRegionSizeChunks());
	}

	private File getRegionFile(String regionId) {
		return RegionKeyUtil.getRegionFile(dataRootFolder, regionId);
	}

	private static class SpawnerPool {
		private final String regionId;
		private String entityTypeName;
		private int resource;
		private long lastRegenTime;
		private long lastAccessTime;

		private SpawnerPool(
				String regionId,
				String entityTypeName,
				int resource,
				long lastRegenTime,
				long lastAccessTime
		) {
			this.regionId = regionId;
			this.entityTypeName = entityTypeName;
			this.resource = resource;
			this.lastRegenTime = lastRegenTime;
			this.lastAccessTime = lastAccessTime;
		}
	}

	private static class SpawnerSnapshot {
		private final String entityTypeName;
		private final int resource;
		private final long lastRegenTime;
		private final long lastAccessTime;

		private SpawnerSnapshot(
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

	private static class RegionSnapshot {
		private final Map<String, SpawnerSnapshot> spawners = new HashMap<>();
	}

	public void applySpawnerSettings(CreatureSpawner spawner) {
		if (!shouldApplySpawnerSettings()) {
			return;
		}

		String entityTypeName = spawner.getSpawnedType().name();

		int requiredPlayerRange = Math.max(1, getRequiredPlayerRange(entityTypeName));
		int spawnRange = Math.max(1, getSpawnRange(entityTypeName));
		int spawnCount = rollSpawnCount(entityTypeName);

		int minDelayTicks = Math.max(1, getMinSpawnDelaySeconds(entityTypeName) * 20);
		int maxDelayTicks = Math.max(1, getMaxSpawnDelaySeconds(entityTypeName) * 20);

		if (maxDelayTicks < minDelayTicks) {
			maxDelayTicks = minDelayTicks;
		}

		spawner.setRequiredPlayerRange(requiredPlayerRange);
		spawner.setSpawnRange(spawnRange);
		spawner.setSpawnCount(spawnCount);

		spawner.setMaxSpawnDelay(maxDelayTicks);
		spawner.setMinSpawnDelay(minDelayTicks);

		if (spawner.getDelay() > maxDelayTicks || spawner.getDelay() < minDelayTicks) {
			spawner.setDelay(minDelayTicks);
		}

		spawner.update(true, false);

		if (shouldLogSpawnerSettingsApply()) {
			plugin.getLogger().info(
					"[SpawnerDebug] Applied settings to " +
							entityTypeName +
							" spawner at " +
							formatLocation(spawner.getLocation()) +
							", range=" + requiredPlayerRange +
							", spawnRange=" + spawnRange +
							", spawnCount=" + spawnCount +
							", minDelayTicks=" + minDelayTicks +
							", maxDelayTicks=" + maxDelayTicks
			);
		}
	}

	public boolean shouldApplySpawnerSettings() {
		return moduleConfig.getBoolean("behavior.apply-settings", true);
	}

	public int getRequiredPlayerRange(String entityTypeName) {
		int value = moduleConfig.getInt(
				"per-entity." + entityTypeName + ".behavior.required-player-range",
				moduleConfig.getInt("behavior.required-player-range", 16)
		);
		return Math.max(1, value);
	}

	public int getSpawnRange(String entityTypeName) {
		int value = moduleConfig.getInt(
				"per-entity." + entityTypeName + ".behavior.spawn-range",
				moduleConfig.getInt("behavior.spawn-range", 4)
		);
		return Math.max(1, value);
	}

	public int getMinSpawnCount(String entityTypeName) {
		int fallback = moduleConfig.getInt("behavior.spawn-count", 4);

		return moduleConfig.getInt(
				"per-entity." + entityTypeName + ".behavior.spawn-count-min",
				moduleConfig.getInt("behavior.spawn-count-min", fallback)
		);
	}

	public int getMaxSpawnCount(String entityTypeName) {
		int fallback = moduleConfig.getInt("behavior.spawn-count", 4);

		return moduleConfig.getInt(
				"per-entity." + entityTypeName + ".behavior.spawn-count-max",
				moduleConfig.getInt("behavior.spawn-count-max", fallback)
		);
	}

	public String getSpawnCountRangeText(String entityTypeName) {
		int minSpawnCount = getMinSpawnCount(entityTypeName);
		int maxSpawnCount = getMaxSpawnCount(entityTypeName);

		if (maxSpawnCount < minSpawnCount) {
			maxSpawnCount = minSpawnCount;
		}

		if (minSpawnCount == maxSpawnCount) {
			return String.valueOf(minSpawnCount);
		}

		return minSpawnCount + "-" + maxSpawnCount;
	}

	private int rollSpawnCount(String entityTypeName) {
		int minSpawnCount = getMinSpawnCount(entityTypeName);
		int maxSpawnCount = getMaxSpawnCount(entityTypeName);

		if (minSpawnCount < 1) {
			minSpawnCount = 1;
		}

		if (maxSpawnCount < minSpawnCount) {
			maxSpawnCount = minSpawnCount;
		}

		return ThreadLocalRandom.current().nextInt(minSpawnCount, maxSpawnCount + 1);
	}

	public int getMinSpawnDelaySeconds(String entityTypeName) {
		int value = moduleConfig.getInt(
				"per-entity." + entityTypeName + ".behavior.min-spawn-delay-seconds",
				moduleConfig.getInt("behavior.min-spawn-delay-seconds", 10)
		);
		return Math.max(1, value);
	}

	public int getMaxSpawnDelaySeconds(String entityTypeName) {
		int value = moduleConfig.getInt(
				"per-entity." + entityTypeName + ".behavior.max-spawn-delay-seconds",
				moduleConfig.getInt("behavior.max-spawn-delay-seconds", 40)
		);
		return Math.max(1, value);
	}

	public int applyLoadedSpawnerSettings() {
		if (!isEnabled() || !shouldApplySpawnerSettings()) {
			return 0;
		}
		int appliedCount = 0;
		for (World world : Bukkit.getWorlds()) {
			for (Chunk chunk : world.getLoadedChunks()) {
				for (BlockState blockState : chunk.getTileEntities()) {
					if (blockState instanceof CreatureSpawner spawner) {
						applySpawnerSettings(spawner);
						appliedCount++;
					}
				}
			}
		}
		return appliedCount;
	}

	public void refreshLoadedChunkSpawnerResources() {
		if (!isEnabled() || !shouldRegenWhileInactiveLoaded()) {
			return;
		}

		for (World world : Bukkit.getWorlds()) {
			for (Chunk chunk : world.getLoadedChunks()) {
				refreshChunkSpawnerResources(chunk);
			}
		}
	}

	public void refreshChunkSpawnerResources(Chunk chunk) {
		if (!isEnabled() || !shouldRegenWhileInactiveLoaded()) {
			return;
		}

		for (BlockState blockState : chunk.getTileEntities()) {
			if (blockState instanceof CreatureSpawner spawner) {
				refreshKnownSpawnerResource(spawner.getLocation(), spawner.getSpawnedType());
			}
		}
	}

	private void refreshKnownSpawnerResource(Location location, EntityType entityType) {
		ensureRegionLoaded(location);

		String spawnerKey = getSpawnerKey(location);
		SpawnerPool pool = spawnerPools.get(spawnerKey);

		if (pool == null) {
			return;
		}

		pool.entityTypeName = entityType.name();

		int beforeResource = pool.resource;
		long beforeRegenTime = pool.lastRegenTime;

		applyRegen(pool);

		if (beforeResource != pool.resource || beforeRegenTime != pool.lastRegenTime) {
			markRegionDirty(pool.regionId);
		}
	}

	public boolean isDebugEnabled() {
		return moduleConfig.getBoolean("debug.enabled", false);
	}

	public boolean shouldLogSpawnerSpawnAttempts() {
		return isDebugEnabled() && moduleConfig.getBoolean("debug.log-spawn-attempts", false);
	}

	public boolean shouldLogCancelledSpawnerSpawns() {
		return isDebugEnabled() && moduleConfig.getBoolean("debug.log-cancelled-spawns", true);
	}

	public boolean shouldLogSpawnerSettingsApply() {
		return isDebugEnabled() && moduleConfig.getBoolean("debug.log-apply-settings", false);
	}

	public boolean shouldRegenWhileChunkUnloaded() {
		return moduleConfig.getBoolean("resource-regen.regen-while-chunk-unloaded", true);
	}

	public boolean shouldRegenWhileInactiveLoaded() {
		return moduleConfig.getBoolean("resource-regen.regen-while-inactive-loaded", true);
	}

	public void setDebug(boolean enabled) {
		moduleConfig.set("debug.enabled", enabled);
	}

	public String formatLocation(Location location) {
		return location.getWorld().getName() + " " +
				location.getBlockX() + "," +
				location.getBlockY() + "," +
				location.getBlockZ();
	}

	public boolean isEmptyActionbarEnabled() {
		return moduleConfig.getBoolean("notify.empty-actionbar.enabled", true);
	}

	public int getEmptyActionbarRadius() {
		return Math.max(1, moduleConfig.getInt("notify.empty-actionbar.radius", 8));
	}

	public int getEmptyActionbarCooldownSeconds() {
		return Math.max(1, moduleConfig.getInt("notify.empty-actionbar.cooldown-seconds", 5));
	}

	public boolean tryStartEmptyActionbarCooldown(Location location) {
		if (!isEmptyActionbarEnabled()) {
			return false;
		}

		String spawnerKey = getSpawnerKey(location);
		long now = System.currentTimeMillis();
		long cooldownMillis = getEmptyActionbarCooldownSeconds() * 1000L;
		long lastNotifyTime = emptyActionbarNotifyTimes.getOrDefault(spawnerKey, 0L);

		if (now - lastNotifyTime < cooldownMillis) {
			return false;
		}

		emptyActionbarNotifyTimes.put(spawnerKey, now);
		return true;
	}

	public int resetSpawnerResource(Location location, EntityType entityType) {
		ensureRegionLoaded(location);
		String spawnerKey = getSpawnerKey(location);
		String regionId = getRegionId(location);
		String entityTypeName = entityType.name();
		SpawnerPool pool = spawnerPools.computeIfAbsent(
				spawnerKey,
				key -> createSpawnerPool(regionId, entityTypeName)
		);
		int resetResource = getInitialResource(entityTypeName);
		int maxResource = getMaxResource(entityTypeName);
		if (maxResource >= 0 && resetResource > maxResource) {
			resetResource = maxResource;
		}
		long now = System.currentTimeMillis();
		pool.entityTypeName = entityTypeName;
		pool.resource = resetResource;
		pool.lastRegenTime = now;
		pool.lastAccessTime = now;
		touchRegion(regionId);
		markRegionDirty(regionId);
		return pool.resource;
	}
}