package basementhost.randomchad.spawner;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.SpawnerSpawnEvent;

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

		Location spawnerLocation = spawner.getLocation();
		EntityType entityType = event.getEntityType();

		boolean allowed = spawnerManager.tryConsumeSpawnerResource(spawnerLocation, entityType);

		if (!allowed) {
			event.setCancelled(true);
		}
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onSpawnerBreak(BlockBreakEvent event) {
		if (!spawnerManager.isEnabled()) {
			return;
		}

		if (event.getBlock().getType() != Material.SPAWNER) {
			return;
		}

		spawnerManager.removeSpawner(event.getBlock().getLocation());
	}
}