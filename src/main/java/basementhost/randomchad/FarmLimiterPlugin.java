package basementhost.randomchad;

import basementhost.randomchad.fish.FishListener;
import basementhost.randomchad.fish.FishManager;
import basementhost.randomchad.lang.LangManager;
import basementhost.randomchad.natural.NaturalSpawnListener;
import basementhost.randomchad.natural.NaturalSpawnManager;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class FarmLimiterPlugin extends JavaPlugin {

	private FishManager fishManager;
	private NaturalSpawnManager naturalSpawnManager;
	private LangManager langManager;

	private int saveTaskId = -1;
	private int cleanupTaskId = -1;

	@Override
	public void onEnable() {
		saveDefaultConfig();

		this.fishManager = new FishManager(this);
		this.fishManager.load();

		this.naturalSpawnManager = new NaturalSpawnManager(this);
		this.naturalSpawnManager.load();

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

		FarmLimiterCommand farmLimiterCommand = new FarmLimiterCommand(this, fishManager, naturalSpawnManager);
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

		getLogger().info("FarmLimiter Plugin Disabled!");
	}

	public LangManager getLangManager() {
		return langManager;
	}
}