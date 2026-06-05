package basementhost.randomchad;

import basementhost.randomchad.fish.FishListener;
import basementhost.randomchad.fish.FishManager;
import basementhost.randomchad.natural.NaturalSpawnListener;
import basementhost.randomchad.natural.NaturalSpawnManager;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class FarmLimiterPlugin extends JavaPlugin {

	private FishManager fishManager;
	private NaturalSpawnManager naturalSpawnManager;

	private int saveTaskId = -1;

	@Override
	public void onEnable() {
		// Config generation
		saveDefaultConfig();

		// Create FishManager
		this.fishManager = new FishManager(this);
		this.fishManager.load();

		// Create NaturalSpawnManager
		this.naturalSpawnManager = new NaturalSpawnManager(this);
		this.naturalSpawnManager.load();

		// Register Fish Listener
		Bukkit.getPluginManager().registerEvents(
				new FishListener(fishManager),
				this
		);

		// Register Natural Spawn Listener
		Bukkit.getPluginManager().registerEvents(
				new NaturalSpawnListener(naturalSpawnManager),
				this
		);

		// Register command
		FarmLimiterCommand farmLimiterCommand = new FarmLimiterCommand(this, fishManager, naturalSpawnManager);
		PluginCommand command = getCommand("farmlimiter");

		if (command != null) {
			command.setExecutor(farmLimiterCommand);
			command.setTabCompleter(farmLimiterCommand);
		} else {
			getLogger().warning("Unable to register /farmlimiter command. Please check plugin.yml.");
		}

		// Save the data periodically
		int saveIntervalSeconds = getConfig().getInt("data.save-interval-seconds", 900);
		long saveIntervalTicks = saveIntervalSeconds * 20L;

		this.saveTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(
				this,
				() -> {
					fishManager.save();
					naturalSpawnManager.save();
				},
				saveIntervalTicks,
				saveIntervalTicks
		);

		getLogger().info("FarmLimiter Plugin Enabled！");
	}

	@Override
	public void onDisable() {
		// Save data when closing the server
		if (fishManager != null) {
			fishManager.save();
		}

		if (naturalSpawnManager != null) {
			naturalSpawnManager.save();
		}

		// Cancel the auto-save task
		if (saveTaskId != -1) {
			Bukkit.getScheduler().cancelTask(saveTaskId);
		}

		getLogger().info("FarmLimiter Plugin Disabled！");
	}
}