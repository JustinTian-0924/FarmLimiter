package basementhost.randomchad;

import basementhost.randomchad.fish.FishListener;
import basementhost.randomchad.fish.FishManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class FarmLimiterPlugin extends JavaPlugin {

	private FishManager fishManager;
	private int saveTaskId = -1;

	@Override
	public void onEnable() {
		//Condig Generation
		saveDefaultConfig();

		// Create FishManager for the "Fish" management for each chuk
		this.fishManager = new FishManager(this);
		this.fishManager.load();

		// Create Fish Listener
		Bukkit.getPluginManager().registerEvents(
				new FishListener(this, fishManager),
				this
		);

		// Save the data periodically
		int saveIntervalSeconds = getConfig().getInt("data.save-interval-seconds", 900);
		long saveIntervalTicks = saveIntervalSeconds * 20L;

		this.saveTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(
				this,
				() -> fishManager.save(),
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

		// cancel the auto-safe task
		if (saveTaskId != -1) {
			Bukkit.getScheduler().cancelTask(saveTaskId);
		}

		getLogger().info("FarmLimiter Plugin Disabled！");
	}
}