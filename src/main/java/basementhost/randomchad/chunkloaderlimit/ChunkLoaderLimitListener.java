package basementhost.randomchad.chunkloaderlimit;

import basementhost.randomchad.FarmLimiterPlugin;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.projectiles.ProjectileSource;

public class ChunkLoaderLimitListener implements Listener {
	private final FarmLimiterPlugin plugin;
	private final ChunkLoaderLimitManager chunkLoaderLimitManager;
	private final NamespacedKey pearlCreatedAtKey;
	private final NamespacedKey pearlOwnerKey;

	public ChunkLoaderLimitListener(FarmLimiterPlugin plugin) {
		this.plugin = plugin;
		this.chunkLoaderLimitManager = plugin.getChunkLoaderLimitManager();
		this.pearlCreatedAtKey = new NamespacedKey(plugin, "chunkloader_ender_pearl_created_at");
		this.pearlOwnerKey = new NamespacedKey(plugin, "chunkloader_ender_pearl_owner");
	}

	@EventHandler(ignoreCancelled = true)
	public void onProjectileLaunch(ProjectileLaunchEvent event) {
		if (!chunkLoaderLimitManager.isEnabled()) {
			return;
		}

		if (!chunkLoaderLimitManager.isEnderPearlEnabled()) {
			return;
		}

		if (!(event.getEntity() instanceof EnderPearl enderPearl)) {
			return;
		}

		ProjectileSource shooter = enderPearl.getShooter();

		if (chunkLoaderLimitManager.isOnlyPlayerThrownEnderPearl() && !(shooter instanceof Player)) {
			return;
		}

		enderPearl.getPersistentDataContainer().set(
				pearlCreatedAtKey,
				PersistentDataType.LONG,
				System.currentTimeMillis()
		);

		if (shooter instanceof Player player) {
			enderPearl.getPersistentDataContainer().set(
					pearlOwnerKey,
					PersistentDataType.STRING,
					player.getUniqueId().toString()
			);
		}

		if (chunkLoaderLimitManager.isDebugEnabled()) {
			plugin.getLogger().info("Tracked ender pearl for Chunk_Loader_Limit at "
					+ enderPearl.getWorld().getName()
					+ " "
					+ enderPearl.getLocation().getBlockX()
					+ ","
					+ enderPearl.getLocation().getBlockY()
					+ ","
					+ enderPearl.getLocation().getBlockZ());
		}
	}
}