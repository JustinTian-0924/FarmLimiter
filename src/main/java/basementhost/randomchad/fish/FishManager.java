package basementhost.randomchad.fish;

import basementhost.randomchad.storage.RegionKeyUtil;
import basementhost.randomchad.storage.RegionLoadState;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public class FishManager {

	private final JavaPlugin plugin;

	private final Map<String, FishPool> fishPools = new HashMap<>();

	private final RegionLoadState regionState = new RegionLoadState();

	private File configFile;
	private YamlConfiguration moduleConfig;

	private File dataRootFolder;

	private final Object dataSaveLock = new Object();

	private final AtomicBoolean asyncSaveRunning = new AtomicBoolean(false);
	private final AtomicBoolean asyncSaveQueued = new AtomicBoolean(false);

	public FishManager(JavaPlugin plugin) {
		this.plugin = plugin;
	}

	public void load() {
		loadModuleConfig();
		loadDataRoot();

		fishPools.clear();
		regionState.clear();

		plugin.getLogger().info("Fish depletion regional data storage initialized.");
	}

	private void loadModuleConfig() {
		File modulesFolder = new File(plugin.getDataFolder(), "modules");

		if (!modulesFolder.exists()) {
			modulesFolder.mkdirs();
		}

		configFile = new File(modulesFolder, "fish-depletion.yml");

		if (!configFile.exists()) {
			plugin.saveResource("modules/fish-depletion.yml", false);
		}

		moduleConfig = YamlConfiguration.loadConfiguration(configFile);
	}

	private void loadDataRoot() {
		File dataFolder = new File(plugin.getDataFolder(), "data");

		if (!dataFolder.exists()) {
			dataFolder.mkdirs();
		}

		dataRootFolder = new File(dataFolder, "fish-depletion");

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

			if (unloadedRegions > 0) {
				plugin.getLogger().info("Fish_Depletion unloaded " + unloadedRegions + " saved regions from memory.");
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
			plugin.getLogger().info("Fish_Depletion unloaded " + unloadedRegions + " saved regions from memory.");
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
				plugin.getLogger().info("Fish_Depletion unloaded " + unloadedRegions + " saved regions from memory.");
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
						plugin.getLogger().info("Fish_Depletion unloaded " + unloadedRegions + " saved regions from memory.");
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
			if (regionFile.exists() && !regionFile.delete()) {
				plugin.getLogger().warning("Unable to delete empty fish region data file: " + regionFile.getPath());
			}
			return;
		}

		File parentFolder = regionFile.getParentFile();

		if (!parentFolder.exists()) {
			parentFolder.mkdirs();
		}

		YamlConfiguration regionConfig = new YamlConfiguration();

		for (Map.Entry<String, ChunkSnapshot> entry : snapshot.chunks.entrySet()) {
			String chunkKey = entry.getKey();
			ChunkSnapshot snapshotPool = entry.getValue();

			String basePath = "chunks." + chunkKey;

			regionConfig.set(basePath + ".fish", snapshotPool.fish);
			regionConfig.set(basePath + ".last-regen-time", snapshotPool.lastRegenTime);
			regionConfig.set(basePath + ".last-access-time", snapshotPool.lastAccessTime);
		}

		try {
			regionConfig.save(regionFile);
		} catch (IOException e) {
			plugin.getLogger().warning("An error occurred when saving fish region data file: " + regionFile.getPath());
			e.printStackTrace();
		}
	}

	public void cleanup() {
		int removed = cleanupAndGetRemovedCount();

		if (removed > 0) {
			plugin.getLogger().info("Fish_Depletion cleanup removed " + removed + " chunk records. Remaining loaded tracked chunks: " + getTrackedChunkCount());
		}

		int unloadedRegions = enforceLoadedRegionLimit();

		if (unloadedRegions > 0) {
			plugin.getLogger().info("Fish_Depletion unloaded " + unloadedRegions + " saved regions from memory.");
		}
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
		int unloaded = 0;

		unloaded += unloadSavedEmptyRegions();
		unloaded += unloadCleanInactiveOrExcessRegions();

		int maxLoadedRegions = getMaxLoadedRegions();

		if (maxLoadedRegions > 0 && regionState.getLoadedRegionCount() > maxLoadedRegions) {
			plugin.getLogger().warning(
					"Fish_Depletion loaded regions are still above limit: " +
							regionState.getLoadedRegionCount() + " / " + maxLoadedRegions +
							". Most remaining regions may be dirty and cannot be safely unloaded yet."
			);
		}

		return unloaded;
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
		return asyncSaveRunning.get();
	}

	public boolean isAsyncSaveQueued() {
		return asyncSaveQueued.get();
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