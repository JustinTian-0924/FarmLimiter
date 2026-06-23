package basementhost.randomchad;

import basementhost.randomchad.breeding.BreedingLimitListener;
import basementhost.randomchad.breeding.BreedingLimitManager;
import basementhost.randomchad.chunkmobunload.ChunkMobUnloadListener;
import basementhost.randomchad.chunkmobunload.ChunkMobUnloadManager;
import basementhost.randomchad.fish.FishListener;
import basementhost.randomchad.fish.FishManager;
import basementhost.randomchad.lang.LangManager;
import basementhost.randomchad.natural.NaturalSpawnListener;
import basementhost.randomchad.natural.NaturalSpawnManager;
import basementhost.randomchad.spawner.SpawnerListener;
import basementhost.randomchad.spawner.SpawnerManager;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class FarmLimiterPlugin extends JavaPlugin {

	private FishManager fishManager;
	private NaturalSpawnManager naturalSpawnManager;
	private SpawnerManager spawnerManager;
	private LangManager langManager;
	private BreedingLimitManager breedingLimitManager;
	private ChunkMobUnloadManager chunkMobUnloadManager;

	private int saveTaskId = -1;
	private int cleanupTaskId = -1;

	@Override
	public void onEnable() {
		saveDefaultConfig();

		this.fishManager = new FishManager(this);
		this.fishManager.load();

		this.naturalSpawnManager = new NaturalSpawnManager(this);
		this.naturalSpawnManager.load();

		this.spawnerManager = new SpawnerManager(this);
		this.spawnerManager.load();

		this.breedingLimitManager = new BreedingLimitManager(this);
		this.breedingLimitManager.load();

		chunkMobUnloadManager = new ChunkMobUnloadManager(this);
		chunkMobUnloadManager.load();

		saveDefaultConfig();
		langManager = new LangManager(this);
		langManager.load();

		Bukkit.getPluginManager().registerEvents(
				new FishListener(this, fishManager),
				this
		);

		Bukkit.getPluginManager().registerEvents(
				new NaturalSpawnListener(naturalSpawnManager),
				this
		);

		Bukkit.getPluginManager().registerEvents(
				new SpawnerListener(this, spawnerManager),
				this
		);

		Bukkit.getPluginManager().registerEvents(
				new BreedingLimitListener(this, breedingLimitManager),
				this
		);

		getServer().getPluginManager().registerEvents(
				new ChunkMobUnloadListener(this),
				this
		);

		FarmLimiterCommand farmLimiterCommand = new FarmLimiterCommand(
				this,
				fishManager,
				naturalSpawnManager,
				breedingLimitManager
		);
		PluginCommand command = getCommand("farmlimiter");

		if (command != null) {
			command.setExecutor(farmLimiterCommand);
			command.setTabCompleter(farmLimiterCommand);
		} else {
			getLogger().warning("Unable to register /farmlimiter command. Please check plugin.yml.");
		}

		startAutoSaveTask();
		startCleanupTask();

		getLogger().info("FarmLimiter Plugin Enabled!");
	}

	private void startAutoSaveTask() {
		int saveIntervalSeconds = getConfig().getInt("data.save-interval-seconds", 1800);
		long saveIntervalTicks = saveIntervalSeconds * 20L;

		this.saveTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(
				this,
				() -> {
					fishManager.saveAsync();
					naturalSpawnManager.saveAsync();
					spawnerManager.saveAsync();
					breedingLimitManager.saveAsync();
				},
				saveIntervalTicks,
				saveIntervalTicks
		);
	}

	private void startCleanupTask() {
		int cleanupIntervalSeconds = getConfig().getInt("data.cleanup-interval-seconds", 300);
		long cleanupIntervalTicks = cleanupIntervalSeconds * 20L;

		this.cleanupTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(
				this,
				() -> {
					fishManager.cleanup();
					naturalSpawnManager.cleanup();
					spawnerManager.cleanup();
					breedingLimitManager.cleanup();
				},
				cleanupIntervalTicks,
				cleanupIntervalTicks
		);
	}

	@Override
	public void onDisable() {
		if (cleanupTaskId != -1) {
			Bukkit.getScheduler().cancelTask(cleanupTaskId);
		}

		if (saveTaskId != -1) {
			Bukkit.getScheduler().cancelTask(saveTaskId);
		}

		// Prevent async saving while the server is shutting down.
		if (fishManager != null) {
			fishManager.save();
		}

		if (naturalSpawnManager != null) {
			naturalSpawnManager.save();
		}

		if (spawnerManager != null) {
			spawnerManager.save();
		}

		if (breedingLimitManager != null) {
			breedingLimitManager.save();
		}

		getLogger().info("FarmLimiter Plugin Disabled!");
	}

	public LangManager getLangManager() {
		return langManager;
	}

	public SpawnerManager getSpawnerManager() {
		return spawnerManager;
	}

	public BreedingLimitManager getBreedingLimitManager() {
		return breedingLimitManager;
	}

	public ChunkMobUnloadManager getChunkMobUnloadManager() {
		return chunkMobUnloadManager;
	}
}