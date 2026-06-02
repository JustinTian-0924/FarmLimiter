package basementhost.randomchad.natural;

import org.bukkit.Chunk;
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

		if (event.isCancelled()) {
			return;
		}

		CreatureSpawnEvent.SpawnReason spawnReason = event.getSpawnReason();

		if (!naturalSpawnManager.shouldLimitSpawnReason(spawnReason)) {
			return;
		}

		EntityType entityType = event.getEntityType();
		Chunk chunk = event.getLocation().getChunk();

		boolean allowed = naturalSpawnManager.tryConsumeSpawnResource(chunk, entityType);

		if (!allowed) {
			event.setCancelled(true);
		}
	}
}