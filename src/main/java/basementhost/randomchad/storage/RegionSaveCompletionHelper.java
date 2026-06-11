package basementhost.randomchad.storage;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;

public final class RegionSaveCompletionHelper {

	private RegionSaveCompletionHelper() {
	}

	public static void clearSavedDirtyRegions(
			RegionLoadState regionState,
			Map<String, Long> savedRegionVersions
	) {
		for (Map.Entry<String, Long> entry : savedRegionVersions.entrySet()) {
			String regionId = entry.getKey();
			long savedVersion = entry.getValue();

			regionState.clearDirtyIfVersionMatches(regionId, savedVersion);
		}
	}

	public static void logUnloadedRegions(
			JavaPlugin plugin,
			String moduleName,
			int unloadedRegions
	) {
		if (unloadedRegions <= 0) {
			return;
		}

		plugin.getLogger().info(moduleName + " unloaded " + unloadedRegions + " saved regions from memory.");
	}
}