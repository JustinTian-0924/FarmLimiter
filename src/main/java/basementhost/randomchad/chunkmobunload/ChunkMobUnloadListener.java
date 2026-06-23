package basementhost.randomchad.chunkmobunload;

import basementhost.randomchad.FarmLimiterPlugin;
import net.kyori.adventure.text.Component;
import org.bukkit.Chunk;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityBreedEvent;

import java.util.Map;

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

		if (!shouldBlockSpawnReason(event.getSpawnReason())) {
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
		debugBlockedSpawn(event.getSpawnReason(), event.getEntityType().name(), chunk);
	}

	private boolean shouldBlockSpawnReason(CreatureSpawnEvent.SpawnReason spawnReason) {
		if (spawnReason == CreatureSpawnEvent.SpawnReason.NATURAL) {
			return chunkMobUnloadManager.shouldBlockNaturalSpawn();
		}

		if (spawnReason == CreatureSpawnEvent.SpawnReason.SPAWNER) {
			return chunkMobUnloadManager.shouldBlockSpawner();
		}

		if (spawnReason == CreatureSpawnEvent.SpawnReason.SPAWNER_EGG) {
			return chunkMobUnloadManager.shouldBlockSpawnEgg();
		}

		return false;
	}

	@EventHandler(ignoreCancelled = true)
	public void onEntityBreed(EntityBreedEvent event) {
		if (!chunkMobUnloadManager.isEnabled()) {
			return;
		}

		if (!chunkMobUnloadManager.shouldBlockBreeding()) {
			return;
		}

		Chunk chunk = event.getEntity().getLocation().getChunk();

		if (!chunkMobUnloadUtil.isOverSoftLimit(
				chunk,
				event.getEntityType(),
				chunkMobUnloadManager
		)) {
			return;
		}

		event.setCancelled(true);
		debugBlockedSpawn(CreatureSpawnEvent.SpawnReason.BREEDING, event.getEntityType().name(), chunk);

		if (event.getBreeder() instanceof Player player) {
			notifyPlayer(player, event.getEntityType().name());
		}
	}

	private void debugBlockedSpawn(
			CreatureSpawnEvent.SpawnReason spawnReason,
			String entityName,
			Chunk chunk
	) {
		if (!chunkMobUnloadManager.isDebugEnabled()) {
			return;
		}

		plugin.getLogger().info("Blocked spawn by Chunk_Mob_Unload: "
				+ entityName
				+ " reason="
				+ spawnReason.name()
				+ " at "
				+ chunk.getWorld().getName()
				+ " "
				+ chunk.getX()
				+ ","
				+ chunk.getZ());
	}

	private void notifyPlayer(Player player, String entityName) {
		player.sendMessage(Component.text(plugin.getLangManager().get("chunkmob.spawn-blocked", Map.of(
				"entity", entityName
		))));
	}
}