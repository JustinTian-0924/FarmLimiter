package basementhost.randomchad.fish;

import org.bukkit.Chunk;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
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

		for (String key : section.getKeys(false)) {
			int fish = section.getInt(key + ".fish", getMaxFish());
			long lastRegenTime = section.getLong(key + ".last-regen-time", System.currentTimeMillis());

			fishPools.put(key, new FishPool(fish, lastRegenTime));
		}

		plugin.getLogger().info("Loaded " + fishPools.size() + " chunks of fishing data.");
	}

	public void save() {
		if (dataConfig == null || dataFile == null) {
			return;
		}

		dataConfig.set("fish-pools", null);

		for (Map.Entry<String, FishPool> entry : fishPools.entrySet()) {
			String key = entry.getKey();
			FishPool pool = entry.getValue();

			dataConfig.set("fish-pools." + key + ".fish", pool.fish);
			dataConfig.set("fish-pools." + key + ".last-regen-time", pool.lastRegenTime);
		}

		try {
			dataConfig.save(dataFile);
		} catch (IOException e) {
			plugin.getLogger().warning("An error occurred when saving data/fish-depletion.yml!");
			e.printStackTrace();
		}
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

		applyRegen(pool);

		return pool.fish;
	}

	public boolean tryConsumeFish(Chunk chunk) {
		String key = getChunkKey(chunk);
		FishPool pool = fishPools.computeIfAbsent(key, k -> createNewPool());

		applyRegen(pool);

		if (pool.fish <= 0) {
			return false;
		}

		pool.fish--;
		return true;
	}

	private FishPool createNewPool() {
		return new FishPool(getMaxFish(), System.currentTimeMillis());
	}

	private void applyRegen(FishPool pool) {
		int maxFish = getMaxFish();
		int regenAmount = getRegenAmount();
		long intervalMillis = getRegenIntervalSeconds() * 1000L;

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

	private static class FishPool {
		private int fish;
		private long lastRegenTime;

		private FishPool(int fish, long lastRegenTime) {
			this.fish = fish;
			this.lastRegenTime = lastRegenTime;
		}
	}
}