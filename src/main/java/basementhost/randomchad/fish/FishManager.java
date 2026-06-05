package basementhost.randomchad.fish;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class FishManager {

	private final JavaPlugin plugin;

	private final Map<String, FishPool> fishPools = new HashMap<>();

	private File configFile;
	private YamlConfiguration moduleConfig;

	private File dataFile;
	private YamlConfiguration dataConfig;

	public FishManager(JavaPlugin plugin) {
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

		configFile = new File(modulesFolder, "fish-depletion.yml");

		if (!configFile.exists()) {
			plugin.saveResource("modules/fish-depletion.yml", false);
		}

		moduleConfig = YamlConfiguration.loadConfiguration(configFile);
	}

	private void loadData() {
		fishPools.clear();

		File dataFolder = new File(plugin.getDataFolder(), "data");

		if (!dataFolder.exists()) {
			dataFolder.mkdirs();
		}

		dataFile = new File(dataFolder, "fish-depletion.yml");

		if (!dataFile.exists()) {
			try {
				dataFile.createNewFile();
			} catch (IOException e) {
				plugin.getLogger().warning("Unable to create data/fish-depletion.yml!");
				e.printStackTrace();
			}
		}

		dataConfig = YamlConfiguration.loadConfiguration(dataFile);

		ConfigurationSection section = dataConfig.getConfigurationSection("fish-pools");
		if (section == null) {
			return;
		}

		long now = System.currentTimeMillis();

		for (String key : section.getKeys(false)) {
			int fish = section.getInt(key + ".fish", getMaxFish());
			long lastRegenTime = section.getLong(key + ".last-regen-time", now);
			long lastAccessTime = section.getLong(key + ".last-access-time", now);

			FishPool pool = new FishPool(fish, lastRegenTime, lastAccessTime);
			applyRegen(pool);

			if (shouldRemovePool(pool, now)) {
				continue;
			}

			fishPools.put(key, pool);
		}

		plugin.getLogger().info("Loaded " + fishPools.size() + " chunks of fishing data.");
	}

	public void save() {
		if (dataConfig == null || dataFile == null) {
			return;
		}

		cleanup();

		dataConfig.set("fish-pools", null);

		for (Map.Entry<String, FishPool> entry : fishPools.entrySet()) {
			String key = entry.getKey();
			FishPool pool = entry.getValue();

			dataConfig.set("fish-pools." + key + ".fish", pool.fish);
			dataConfig.set("fish-pools." + key + ".last-regen-time", pool.lastRegenTime);
			dataConfig.set("fish-pools." + key + ".last-access-time", pool.lastAccessTime);
		}

		try {
			dataConfig.save(dataFile);
		} catch (IOException e) {
			plugin.getLogger().warning("An error occurred when saving data/fish-depletion.yml!");
			e.printStackTrace();
		}
	}

	public void saveAsync() {
		if (dataFile == null) {
			return;
		}

		// Clean up in main processor, and copy a snapshot
		cleanup();

		Map<String, FishPoolSnapshot> snapshot = new HashMap<>();

		for (Map.Entry<String, FishPool> entry : fishPools.entrySet()) {
			String key = entry.getKey();
			FishPool pool = entry.getValue();

			snapshot.put(
					key,
					new FishPoolSnapshot(
							pool.fish,
							pool.lastRegenTime,
							pool.lastAccessTime
					)
			);
		}

		File targetFile = dataFile;

		// the backend process only in charge of writing the file and won't touching any active Bukkit related objects
		Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
			YamlConfiguration asyncConfig = new YamlConfiguration();

			for (Map.Entry<String, FishPoolSnapshot> entry : snapshot.entrySet()) {
				String key = entry.getKey();
				FishPoolSnapshot pool = entry.getValue();

				asyncConfig.set("fish-pools." + key + ".fish", pool.fish);
				asyncConfig.set("fish-pools." + key + ".last-regen-time", pool.lastRegenTime);
				asyncConfig.set("fish-pools." + key + ".last-access-time", pool.lastAccessTime);
			}

			try {
				asyncConfig.save(targetFile);
			} catch (IOException e) {
				plugin.getLogger().warning("An error occurred when asynchronously saving data/fish-depletion.yml!");
				e.printStackTrace();
			}
		});
	}

	public void cleanup() {
		int removed = cleanupAndGetRemovedCount();

		if (removed > 0) {
			plugin.getLogger().info("Fish_Depletion cleanup removed " + removed + " chunk records. Remaining: " + fishPools.size());
		}
	}

	public int cleanupAndGetRemovedCount() {
		long now = System.currentTimeMillis();

		int before = fishPools.size();

		Iterator<Map.Entry<String, FishPool>> iterator = fishPools.entrySet().iterator();

		while (iterator.hasNext()) {
			Map.Entry<String, FishPool> entry = iterator.next();
			FishPool pool = entry.getValue();

			applyRegen(pool);

			if (shouldRemovePool(pool, now)) {
				iterator.remove();
			}
		}

		return before - fishPools.size();
	}

	public int getTrackedChunkCount() {
		return fishPools.size();
	}

	private boolean shouldRemovePool(FishPool pool, long now) {
		if (shouldUnloadWhenFull() && pool.fish >= getMaxFish()) {
			return true;
		}

		long ttlMillis = getInactiveTtlSeconds() * 1000L;

		if (ttlMillis > 0 && now - pool.lastAccessTime >= ttlMillis) {
			return true;
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
		String key = getChunkKey(chunk);
		FishPool pool = fishPools.computeIfAbsent(key, k -> createNewPool());

		pool.lastAccessTime = System.currentTimeMillis();

		applyRegen(pool);

		return pool.fish;
	}

	public boolean tryConsumeFish(Chunk chunk) {
		String key = getChunkKey(chunk);
		FishPool pool = fishPools.computeIfAbsent(key, k -> createNewPool());

		pool.lastAccessTime = System.currentTimeMillis();

		applyRegen(pool);

		if (pool.fish <= 0) {
			return false;
		}

		pool.fish--;
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

	private String getChunkKey(Chunk chunk) {
		String worldId = chunk.getWorld().getUID().toString();
		int x = chunk.getX();
		int z = chunk.getZ();

		return worldId + "_" + x + "_" + z;
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

	private static class FishPoolSnapshot {
		private final int fish;
		private final long lastRegenTime;
		private final long lastAccessTime;
		private FishPoolSnapshot(int fish, long lastRegenTime, long lastAccessTime) {
			this.fish = fish;
			this.lastRegenTime = lastRegenTime;
			this.lastAccessTime = lastAccessTime;
		}
	}

}