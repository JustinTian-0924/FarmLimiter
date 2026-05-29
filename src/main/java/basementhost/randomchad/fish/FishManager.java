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

	private File dataFile;
	private YamlConfiguration dataConfig;

	public FishManager(JavaPlugin plugin) {
		this.plugin = plugin;
	}

	public void load() {
		dataFile = new File(plugin.getDataFolder(), "data.yml");

		if (!plugin.getDataFolder().exists()) {
			plugin.getDataFolder().mkdirs();
		}

		if (!dataFile.exists()) {
			try {
				dataFile.createNewFile();
			} catch (IOException e) {
				plugin.getLogger().warning("Unable to create data.yml！");
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

		plugin.getLogger().info("Loaded " + fishPools.size() + " chunks of fishing data");
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
			plugin.getLogger().warning("An error occurs when saving data.yml！");
			e.printStackTrace();
		}
	}

	public boolean isEnabled() {
		return plugin.getConfig().getBoolean("modules.fish-depletion.enabled", true);
	}

	public boolean shouldNotifyPlayer() {
		return plugin.getConfig().getBoolean("modules.fish-depletion.notify-player", true);
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

	private int getMaxFish() {
		return plugin.getConfig().getInt("modules.fish-depletion.max-fish", 64);
	}

	private int getRegenAmount() {
		return plugin.getConfig().getInt("modules.fish-depletion.regen-amount", 8);
	}

	private int getRegenIntervalSeconds() {
		return plugin.getConfig().getInt("modules.fish-depletion.regen-interval-seconds", 600);
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