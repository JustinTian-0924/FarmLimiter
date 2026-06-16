package basementhost.randomchad.spawner;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.SpawnerSpawnEvent;
import org.bukkit.event.world.ChunkLoadEvent;

public class SpawnerListener implements Listener {

	private final SpawnerManager spawnerManager;

	public SpawnerListener(SpawnerManager spawnerManager) {
		this.spawnerManager = spawnerManager;
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onSpawnerSpawn(SpawnerSpawnEvent event) {
		if (!spawnerManager.isEnabled()) {
			return;
		}

		if (event.isCancelled()) {
			return;
		}

		CreatureSpawner spawner = event.getSpawner();

		if (spawner == null) {
			return;
		}
		spawnerManager.applySpawnerSettings(spawner);

		Location spawnerLocation = spawner.getLocation();
		EntityType entityType = event.getEntityType();

		boolean allowed = spawnerManager.tryConsumeSpawnerResource(spawnerLocation, entityType);
		int remainingResource = spawnerManager.getRemainingResource(spawnerLocation, entityType);

		if (spawnerManager.shouldLogSpawnerSpawnAttempts()) {
			Bukkit.getLogger().info(
					"[SpawnerDebug] Spawn attempt: " +
							entityType.name() +
							" at " +
							spawnerManager.formatLocation(spawnerLocation) +
							", allowed=" + allowed +
							", remainingResource=" + remainingResource
			);
		}

		if (!allowed) {
			event.setCancelled(true);

			if (spawnerManager.shouldLogCancelledSpawnerSpawns()) {
				Bukkit.getLogger().info(
						"[SpawnerDebug] Cancelled spawner spawn: " +
								entityType.name() +
								" at " +
								spawnerManager.formatLocation(spawnerLocation) +
								", remainingResource=" + remainingResource
				);
			}
		}
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onSpawnerPlace(BlockPlaceEvent event) {
		if (!spawnerManager.isEnabled()) {
			return;
		}

		BlockState blockState = event.getBlockPlaced().getState();

		if (!(blockState instanceof CreatureSpawner spawner)) {
			return;
		}

		spawnerManager.applySpawnerSettings(spawner);
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onChunkLoad(ChunkLoadEvent event) {
		if (!spawnerManager.isEnabled()) {
			return;
		}

		for (BlockState blockState : event.getChunk().getTileEntities()) {
			if (blockState instanceof CreatureSpawner spawner) {
				spawnerManager.applySpawnerSettings(spawner);
			}
		}
	}



}