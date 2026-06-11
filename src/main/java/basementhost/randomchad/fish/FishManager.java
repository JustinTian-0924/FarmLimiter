package basementhost.randomchad.fish;

import basementhost.randomchad.storage.*;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public class FishManager {

	private final JavaPlugin plugin;

	private final Map<String, FishPool> fishPools = new HashMap<>();

	private final RegionLoadState regionState = new RegionLoadState();

	private File configFile;
	private YamlConfiguration moduleConfig;
	private RegionStorageConfig storageConfig;

	private File dataRootFolder;

	private final Object dataSaveLock = new Object();

	private final AsyncSaveGuard asyncSaveGuard = new AsyncSaveGuard();

	public FishManager(JavaPlugin plugin) {
		this.plugin = plugin;
	}

	public void load() {
		loadModuleConfig();
		loadDataRoot();

		fishPools.clear();
		regionState.clear();
		asyncSaveGuard.clear();

		plugin.getLogger().info("Fish depletion regional data storage initialized.");
	}

	private void loadModuleConfig() {
		ModuleConfigLoader configLoader = new ModuleConfigLoader(plugin, "fish-depletion.yml");

		configFile = configLoader.getConfigFile();
		moduleConfig = configLoader.getConfig();
		storageConfig = new RegionStorageConfig(moduleConfig);
	}

	private void loadDataRoot() {
		dataRootFolder = DataRootLoader.load(plugin, "fish-depletion");
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

			int fish = regionConfig.getInt(basePath + ".fish", getMaxFish());
			long lastRegenTime = regionConfig.getLong(basePath + ".last-regen-time", now);
			long lastAccessTime = regionConfig.getLong(basePath + ".last-access-time", now);

			FishPool pool = new FishPool(fish, lastRegenTime, lastAccessTime);

			applyRegen(pool);

			if (shouldRemovePool(pool, now)) {
				markRegionDirty(regionId);
				continue;
			}

			fishPools.put(chunkKey, pool);
		}

		regionState.markLoaded(regionId);
		enforceLoadedRegionLimit();
	}

	public void save() {
		cleanup();

		Map<String, Long> regionsToSave = regionState.snapshotDirtyVersions();

		if (regionsToSave.isEmpty()) {
			int unloadedRegions = enforceLoadedRegionLimit();

			RegionSaveCompletionHelper.logUnloadedRegions(plugin, "Fish_Depletion", unloadedRegions);

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

		RegionSaveCompletionHelper.logUnloadedRegions(plugin, "Fish_Depletion", unloadedRegions);
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

			RegionSaveCompletionHelper.logUnloadedRegions(plugin, "Fish_Depletion", unloadedRegions);

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

					RegionSaveCompletionHelper.logUnloadedRegions(plugin, "Fish_Depletion", unloadedRegions);

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

			for (Map.Entry<String, FishPool> entry : fishPools.entrySet()) {
				String chunkKey = entry.getKey();

				if (!regionId.equals(getRegionIdFromChunkKey(chunkKey))) {
					continue;
				}

				FishPool pool = entry.getValue();

				ChunkSnapshot chunkSnapshot = new ChunkSnapshot(
						pool.fish,
						pool.lastRegenTime,
						pool.lastAccessTime
				);

				snapshot.chunks.put(chunkKey, chunkSnapshot);
			}

			snapshots.put(regionId, snapshot);
		}

		return snapshots;
	}

	private void writeRegionSnapshot(String regionId, RegionSnapshot snapshot) {
		File regionFile = getRegionFile(regionId);

		if (snapshot.chunks.isEmpty()) {
			RegionFileIoHelper.deleteEmptyRegionFile(
					plugin,
					regionFile,
					"Unable to delete empty fish region data file"
			);
			return;
		}
		RegionFileIoHelper.ensureParentFolderExists(regionFile);

		YamlConfiguration regionConfig = new YamlConfiguration();

		for (Map.Entry<String, ChunkSnapshot> entry : snapshot.chunks.entrySet()) {
			String chunkKey = entry.getKey();
			ChunkSnapshot snapshotPool = entry.getValue();

			String basePath = "chunks." + chunkKey;

			regionConfig.set(basePath + ".fish", snapshotPool.fish);
			regionConfig.set(basePath + ".last-regen-time", snapshotPool.lastRegenTime);
			regionConfig.set(basePath + ".last-access-time", snapshotPool.lastAccessTime);
		}

		RegionFileIoHelper.saveRegionConfig(
				plugin,
				regionConfig,
				regionFile,
				"An error occurred when saving fish region data file"
		);
	}

	public void cleanup() {
		int removed = cleanupAndGetRemovedCount();

		if (removed > 0) {
			plugin.getLogger().info("Fish_Depletion cleanup removed " + removed + " chunk records. Remaining loaded tracked chunks: " + getTrackedChunkCount());
		}

		int unloadedRegions = enforceLoadedRegionLimit();

		RegionSaveCompletionHelper.logUnloadedRegions(plugin, "Fish_Depletion", unloadedRegions);
	}

	public int cleanupAndGetRemovedCount() {
		long now = System.currentTimeMillis();

		int before = fishPools.size();

		Iterator<Map.Entry<String, FishPool>> iterator = fishPools.entrySet().iterator();

		while (iterator.hasNext()) {
			Map.Entry<String, FishPool> entry = iterator.next();

			String chunkKey = entry.getKey();
			String regionId = getRegionIdFromChunkKey(chunkKey);
			FishPool pool = entry.getValue();

			int beforeFish = pool.fish;
			long beforeRegenTime = pool.lastRegenTime;

			applyRegen(pool);

			if (beforeFish != pool.fish || beforeRegenTime != pool.lastRegenTime) {
				markRegionDirty(regionId);
			}

			if (shouldRemovePool(pool, now)) {
				iterator.remove();
				markRegionDirty(regionId);
			}
		}

		return before - fishPools.size();
	}

	private boolean shouldRemovePool(FishPool pool, long now) {
		if (shouldUnloadWhenFull() && pool.fish >= getMaxFish()) {
			return true;
		}

		long ttlMillis = getInactiveTtlSeconds() * 1000L;

		return ttlMillis > 0 && now - pool.lastAccessTime >= ttlMillis;
	}

	public int enforceLoadedRegionLimit() {
		return RegionUnloadHelper.enforceLoadedRegionLimit(
				plugin,
				"Fish_Depletion",
				regionState,
				shouldUnloadEmptyRegionsAfterSave(),
				getMaxLoadedRegions(),
				shouldUnloadInactiveLoadedRegions(),
				getLoadedRegionInactiveTtlSeconds(),
				getMaxRegionUnloadsPerCleanup(),
				this::regionHasTrackedChunks,
				this::unloadRegionFromMemory
		);
	}

	public int unloadSavedEmptyRegions() {
		return RegionUnloadHelper.unloadSavedEmptyRegions(
				regionState,
				shouldUnloadEmptyRegionsAfterSave(),
				this::regionHasTrackedChunks
		);
	}

	private void unloadRegionFromMemory(String regionId) {
		Set<String> chunkKeys = new HashSet<>(fishPools.keySet());

		for (String chunkKey : chunkKeys) {
			if (regionId.equals(getRegionIdFromChunkKey(chunkKey))) {
				fishPools.remove(chunkKey);
			}
		}

		regionState.removeLoadedRegion(regionId);
	}

	private boolean regionHasTrackedChunks(String regionId) {
		for (String chunkKey : fishPools.keySet()) {
			if (regionId.equals(getRegionIdFromChunkKey(chunkKey))) {
				return true;
			}
		}

		return false;
	}

	public boolean isEnabled() {
		return moduleConfig.getBoolean("enabled", true);
	}

	public boolean shouldNotifyPlayer() {
		return moduleConfig.getBoolean("notify-player", true);
	}

	public int getRemainingFish(Chunk chunk) {
		ensureRegionLoaded(chunk);

		String key = getChunkKey(chunk);
		String regionId = getRegionId(chunk);

		FishPool pool = fishPools.computeIfAbsent(key, k -> createNewPool());

		pool.lastAccessTime = System.currentTimeMillis();

		touchRegion(regionId);
		markRegionDirty(regionId);

		applyRegen(pool);

		return pool.fish;
	}

	public int peekRemainingFish(Chunk chunk) {
		ensureRegionLoaded(chunk);

		String key = getChunkKey(chunk);
		FishPool pool = fishPools.get(key);

		if (pool == null) {
			return getMaxFish();
		}

		applyRegen(pool);

		return pool.fish;
	}

	public boolean tryConsumeFish(Chunk chunk) {
		ensureRegionLoaded(chunk);

		String key = getChunkKey(chunk);
		String regionId = getRegionId(chunk);

		FishPool pool = fishPools.computeIfAbsent(key, k -> createNewPool());

		pool.lastAccessTime = System.currentTimeMillis();

		touchRegion(regionId);
		markRegionDirty(regionId);

		applyRegen(pool);

		if (pool.fish <= 0) {
			return false;
		}

		pool.fish--;

		markRegionDirty(regionId);

		return true;
	}

	private FishPool createNewPool() {
		long now = System.currentTimeMillis();
		return new FishPool(getMaxFish(), now, now);
	}

	private void applyRegen(FishPool pool) {
		int maxFish = getMaxFish();
		int regenAmount = getRegenAmount();
		int regenIntervalSeconds = getRegenIntervalSeconds();

		if (regenIntervalSeconds <= 0) {
			return;
		}

		long intervalMillis = regenIntervalSeconds * 1000L;
		long now = System.currentTimeMillis();

		if (pool.fish >= maxFish) {
			pool.fish = maxFish;
			pool.lastRegenTime = now;
			return;
		}

		long passedTime = now - pool.lastRegenTime;

		if (passedTime < intervalMillis) {
			return;
		}

		long regenTimes = passedTime / intervalMillis;
		int totalRegen = (int) regenTimes * regenAmount;

		pool.fish = Math.min(maxFish, pool.fish + totalRegen);
		pool.lastRegenTime += regenTimes * intervalMillis;

		if (pool.fish >= maxFish) {
			pool.fish = maxFish;
			pool.lastRegenTime = now;
		}
	}

	private void touchRegion(String regionId) {
		regionState.touch(regionId);
	}

	private void markRegionDirty(String regionId) {
		regionState.markDirty(regionId);
	}

	public int getTrackedChunkCount() {
		return fishPools.size();
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

	public int getMaxFish() {
		return moduleConfig.getInt("max-fish", 64);
	}

	public int getRegenAmount() {
		return moduleConfig.getInt("regen-amount", 8);
	}

	public int getRegenIntervalSeconds() {
		return moduleConfig.getInt("regen-interval-seconds", 600);
	}

	private boolean shouldUnloadWhenFull() {
		return moduleConfig.getBoolean("cleanup.unload-when-full", true);
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

	private static class FishPool {
		private int fish;
		private long lastRegenTime;
		private long lastAccessTime;

		private FishPool(int fish, long lastRegenTime, long lastAccessTime) {
			this.fish = fish;
			this.lastRegenTime = lastRegenTime;
			this.lastAccessTime = lastAccessTime;
		}
	}

	private static class ChunkSnapshot {
		private final int fish;
		private final long lastRegenTime;
		private final long lastAccessTime;

		private ChunkSnapshot(int fish, long lastRegenTime, long lastAccessTime) {
			this.fish = fish;
			this.lastRegenTime = lastRegenTime;
			this.lastAccessTime = lastAccessTime;
		}
	}

	private static class RegionSnapshot {
		private final Map<String, ChunkSnapshot> chunks = new HashMap<>();
	}
}