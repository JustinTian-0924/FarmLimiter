package basementhost.randomchad.natural;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;

public class NaturalSpawnListener implements Listener {

	private final NaturalSpawnManager naturalSpawnManager;

	public NaturalSpawnListener(NaturalSpawnManager naturalSpawnManager) {
		this.naturalSpawnManager = naturalSpawnManager;
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onCreatureSpawn(CreatureSpawnEvent event) {
		if (!naturalSpawnManager.isEnabled()) {
			return;
		}

		EntityType entityType = event.getEntityType();
		CreatureSpawnEvent.SpawnReason spawnReason = event.getSpawnReason();
		Location location = event.getLocation();
		Chunk chunk = location.getChunk();

		if (naturalSpawnManager.isDebugEnabled() && naturalSpawnManager.shouldLogAllCreatureSpawns()) {
			naturalSpawnManager.debugLog(
					"CreatureSpawnEvent received: " +
							"entity=" + entityType.name() +
							", reason=" + spawnReason.name() +
							", cancelled=" + event.isCancelled() +
							", world=" + chunk.getWorld().getName() +
							", chunkX=" + chunk.getX() +
							", chunkZ=" + chunk.getZ() +
							", blockX=" + location.getBlockX() +
							", blockY=" + location.getBlockY() +
							", blockZ=" + location.getBlockZ()
			);
		}

		if (event.isCancelled()) {
			if (naturalSpawnManager.isDebugEnabled()) {
				naturalSpawnManager.debugLog(
						"Ignored because event was already cancelled: " +
								"entity=" + entityType.name() +
								", reason=" + spawnReason.name()
				);
			}
			return;
		}

		if (!naturalSpawnManager.shouldLimitSpawnReason(spawnReason)) {
			if (naturalSpawnManager.isDebugEnabled() && naturalSpawnManager.shouldLogIgnoredSpawnReasons()) {
				naturalSpawnManager.debugLog(
						"Ignored spawn reason: " +
								"entity=" + entityType.name() +
								", reason=" + spawnReason.name() +
								", world=" + chunk.getWorld().getName() +
								", chunkX=" + chunk.getX() +
								", chunkZ=" + chunk.getZ()
				);
			}
			return;
		}

		int totalBefore = naturalSpawnManager.getRemainingTotalResource(chunk);
		int entityBefore = naturalSpawnManager.getRemainingEntityResource(chunk, entityType);

		boolean allowed = naturalSpawnManager.tryConsumeSpawnResource(chunk, entityType);

		int totalAfter = naturalSpawnManager.getRemainingTotalResource(chunk);
		int entityAfter = naturalSpawnManager.getRemainingEntityResource(chunk, entityType);

		if (!allowed) {
			event.setCancelled(true);

			if (naturalSpawnManager.isDebugEnabled() && naturalSpawnManager.shouldLogCancel()) {
				naturalSpawnManager.debugLog(
						"Spawn cancelled due to insufficient resource: " +
								"entity=" + entityType.name() +
								", reason=" + spawnReason.name() +
								", world=" + chunk.getWorld().getName() +
								", chunkX=" + chunk.getX() +
								", chunkZ=" + chunk.getZ() +
								", total=" + totalBefore + " -> " + totalAfter +
								", entity=" + entityBefore + " -> " + entityAfter
				);
			}

			return;
		}

		if (naturalSpawnManager.isDebugEnabled() && naturalSpawnManager.shouldLogConsume()) {
			naturalSpawnManager.debugLog(
					"Spawn allowed and resource consumed: " +
							"entity=" + entityType.name() +
							", reason=" + spawnReason.name() +
							", world=" + chunk.getWorld().getName() +
							", chunkX=" + chunk.getX() +
							", chunkZ=" + chunk.getZ() +
							", total=" + totalBefore + " -> " + totalAfter +
							", entity=" + entityBefore + " -> " + entityAfter
			);
		}
	}
}