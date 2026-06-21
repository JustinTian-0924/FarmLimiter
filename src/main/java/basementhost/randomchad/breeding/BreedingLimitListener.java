package basementhost.randomchad.breeding;

import basementhost.randomchad.FarmLimiterPlugin;
import net.kyori.adventure.text.Component;
import org.bukkit.Chunk;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityBreedEvent;

import java.util.Map;

public class BreedingLimitListener implements Listener {

	private final FarmLimiterPlugin plugin;
	private final BreedingLimitManager breedingLimitManager;

	public BreedingLimitListener(FarmLimiterPlugin plugin, BreedingLimitManager breedingLimitManager) {
		this.plugin = plugin;
		this.breedingLimitManager = breedingLimitManager;
	}

	@EventHandler(ignoreCancelled = true)
	public void onEntityBreed(EntityBreedEvent event) {
		if (!breedingLimitManager.isEnabled()) {
			return;
		}

		if (event.getBreeder() instanceof Player player
				&& player.hasPermission("farmlimiter.breeding.bypass")) {
			return;
		}

		if (event.getBreeder() instanceof Player player
				&& player.hasPermission(breedingLimitManager.getBypassPermission())) {
			return;
		}

		EntityType entityType = event.getEntityType();
		Chunk chunk = event.getEntity().getLocation().getChunk();

		if (!breedingLimitManager.isLimitReached(chunk, entityType)) {
			return;
		}

		event.setCancelled(true);

		int current = breedingLimitManager.countSameTypeInChunk(chunk, entityType);
		int limit = breedingLimitManager.getLimit(entityType);

		if (event.getBreeder() instanceof Player player) {
			notifyPlayer(player, entityType, current, limit);
		}

		if (breedingLimitManager.isDebugEnabled()) {
			plugin.getLogger().info("[BreedingLimit] Blocked " + entityType.name() +
					" breeding in " + chunk.getWorld().getName() +
					" chunk " + chunk.getX() + "," + chunk.getZ() +
					" count=" + current + "/" + limit);
		}
	}

	@EventHandler(ignoreCancelled = true)
	public void onAllayDuplicate(CreatureSpawnEvent event) {
		if (!breedingLimitManager.isEnabled()) {
			return;
		}

		if (event.getEntityType() != EntityType.ALLAY) {
			return;
		}

		CreatureSpawnEvent.SpawnReason spawnReason = event.getSpawnReason();

		if (spawnReason != CreatureSpawnEvent.SpawnReason.DUPLICATION
				&& spawnReason != CreatureSpawnEvent.SpawnReason.BREEDING) {
			return;
		}

		Chunk chunk = event.getLocation().getChunk();

		if (!breedingLimitManager.isLimitReached(chunk, EntityType.ALLAY)) {
			return;
		}

		event.setCancelled(true);

		int current = breedingLimitManager.countSameTypeInChunk(chunk, EntityType.ALLAY);
		int limit = breedingLimitManager.getLimit(EntityType.ALLAY);

		if (breedingLimitManager.isDebugEnabled()) {
			plugin.getLogger().info("[BreedingLimit] Blocked ALLAY duplication in " +
					chunk.getWorld().getName() +
					" chunk " + chunk.getX() + "," + chunk.getZ() +
					" count=" + current + "/" + limit);
		}
	}

	private void notifyPlayer(Player player, EntityType entityType, int current, int limit) {
		Map<String, Object> placeholders = Map.of(
				"entity", entityType.name(),
				"current", current,
				"limit", limit
		);

		if (breedingLimitManager.isActionbarNotifyEnabled()) {
			player.sendActionBar(Component.text(plugin.getLangManager().get(
					"breeding.actionbar-blocked",
					placeholders
			)));
		}

		if (breedingLimitManager.isChatNotifyEnabled()) {
			player.sendMessage(Component.text(plugin.getLangManager().get(
					"breeding.chat-blocked",
					placeholders
			)));
		}
	}
}