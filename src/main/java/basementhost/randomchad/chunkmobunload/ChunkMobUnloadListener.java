package basementhost.randomchad.chunkmobunload;

import basementhost.randomchad.FarmLimiterPlugin;
import org.bukkit.Chunk;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;

public class ChunkMobUnloadListener implements Listener {
	private final FarmLimiterPlugin plugin;
	private final ChunkMobUnloadManager chunkMobUnloadManager;
	private final ChunkMobUnloadUtil chunkMobUnloadUtil;

	public ChunkMobUnloadListener(FarmLimiterPlugin plugin) {
		this.plugin = plugin;
		this.chunkMobUnloadManager = plugin.getChunkMobUnloadManager();
		this.chunkMobUnloadUtil = new ChunkMobUnloadUtil();
	}

	@EventHandler(ignoreCancelled = true)
	public void onCreatureSpawn(CreatureSpawnEvent event) {
		if (!chunkMobUnloadManager.isEnabled()) {
			return;
		}

		if (!chunkMobUnloadManager.shouldBlockNaturalSpawn()) {
			return;
		}

		if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.NATURAL) {
			return;
		}

		Chunk chunk = event.getLocation().getChunk();

		if (!chunkMobUnloadUtil.isOverSoftLimit(
				chunk,
				event.getEntityType(),
				chunkMobUnloadManager
		)) {
			return;
		}

		event.setCancelled(true);

		if (plugin.getChunkMobUnloadManager().isDebugEnabled()) {
			plugin.getLogger().info("Blocked natural spawn by Chunk_Mob_Unload: "
					+ event.getEntityType().name()
					+ " at "
					+ chunk.getWorld().getName()
					+ " "
					+ chunk.getX()
					+ ","
					+ chunk.getZ());
		}
	}
}