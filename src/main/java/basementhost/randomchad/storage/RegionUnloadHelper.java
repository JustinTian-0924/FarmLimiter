package basementhost.randomchad.storage;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.function.Consumer;
import java.util.function.Predicate;

public final class RegionUnloadHelper {

	private RegionUnloadHelper() {
	}

	public static int enforceLoadedRegionLimit(
			JavaPlugin plugin,
			String moduleName,
			RegionLoadState regionState,
			boolean unloadEmptyRegionsAfterSave,
			int maxLoadedRegions,
			boolean unloadInactiveLoadedRegions,
			int loadedRegionInactiveTtlSeconds,
			int maxRegionUnloadsPerCleanup,
			Predicate<String> regionHasTrackedChunks,
			Consumer<String> unloadRegionFromMemory
	) {
		int unloaded = 0;

		unloaded += unloadSavedEmptyRegions(
				regionState,
				unloadEmptyRegionsAfterSave,
				regionHasTrackedChunks
		);

		unloaded += unloadCleanInactiveOrExcessRegions(
				regionState,
				maxLoadedRegions,
				unloadInactiveLoadedRegions,
				loadedRegionInactiveTtlSeconds,
				maxRegionUnloadsPerCleanup,
				unloadRegionFromMemory
		);

		if (maxLoadedRegions > 0 && regionState.getLoadedRegionCount() > maxLoadedRegions) {
			plugin.getLogger().warning(
					moduleName + " loaded regions are still above limit: " +
							regionState.getLoadedRegionCount() + " / " + maxLoadedRegions +
							". Most remaining regions may be dirty and cannot be safely unloaded yet."
			);
		}

		return unloaded;
	}

	public static int unloadSavedEmptyRegions(
			RegionLoadState regionState,
			boolean unloadEmptyRegionsAfterSave,
			Predicate<String> regionHasTrackedChunks
	) {
		if (!unloadEmptyRegionsAfterSave) {
			return 0;
		}

		int before = regionState.getLoadedRegionCount();

		for (String regionId : regionState.getLoadedRegionIdsSnapshot()) {
			if (regionState.isDirty(regionId)) {
				continue;
			}

			if (regionHasTrackedChunks.test(regionId)) {
				continue;
			}

			regionState.removeLoadedRegion(regionId);
		}

		return before - regionState.getLoadedRegionCount();
	}

	private static int unloadCleanInactiveOrExcessRegions(
			RegionLoadState regionState,
			int maxLoadedRegions,
			boolean unloadInactiveLoadedRegions,
			int loadedRegionInactiveTtlSeconds,
			int maxRegionUnloadsPerCleanup,
			Consumer<String> unloadRegionFromMemory
	) {
		int maxUnloads = Math.max(1, maxRegionUnloadsPerCleanup);

		int unloaded = 0;
		long now = System.currentTimeMillis();

		if (unloadInactiveLoadedRegions) {
			long ttlMillis = loadedRegionInactiveTtlSeconds * 1000L;

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
						unloadRegionFromMemory.accept(regionId);
						unloaded++;
					}
				}
			}
		}

		while (maxLoadedRegions > 0 && regionState.getLoadedRegionCount() > maxLoadedRegions && unloaded < maxUnloads) {
			String oldestCleanRegionId = findOldestCleanLoadedRegionId(regionState);

			if (oldestCleanRegionId == null) {
				break;
			}

			unloadRegionFromMemory.accept(oldestCleanRegionId);
			unloaded++;
		}

		return unloaded;
	}

	private static String findOldestCleanLoadedRegionId(RegionLoadState regionState) {
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
}