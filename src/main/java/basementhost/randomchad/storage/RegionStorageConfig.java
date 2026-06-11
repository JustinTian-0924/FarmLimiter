package basementhost.randomchad.storage;

import org.bukkit.configuration.file.YamlConfiguration;

public class RegionStorageConfig {

	private final YamlConfiguration config;

	public RegionStorageConfig(YamlConfiguration config) {
		this.config = config;
	}

	public int getRegionSizeChunks() {
		return Math.max(1, config.getInt("storage.region-size-chunks", 32));
	}

	public boolean shouldUnloadEmptyRegionsAfterSave() {
		return config.getBoolean("storage.unload-empty-regions-after-save", true);
	}

	public int getMaxLoadedRegions() {
		return config.getInt("storage.max-loaded-regions", 128);
	}

	public boolean shouldUnloadInactiveLoadedRegions() {
		return config.getBoolean("storage.unload-inactive-loaded-regions", true);
	}

	public int getLoadedRegionInactiveTtlSeconds() {
		return config.getInt("storage.loaded-region-inactive-ttl-seconds", 900);
	}

	public int getMaxRegionUnloadsPerCleanup() {
		return Math.max(1, config.getInt("storage.max-region-unloads-per-cleanup", 8));
	}
}