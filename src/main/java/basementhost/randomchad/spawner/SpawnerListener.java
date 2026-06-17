package basementhost.randomchad.spawner;

import basementhost.randomchad.FarmLimiterPlugin;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.SpawnerSpawnEvent;
import org.bukkit.event.world.ChunkLoadEvent;

import java.util.Map;

public class SpawnerListener implements Listener {

	private final SpawnerManager spawnerManager;
	private final FarmLimiterPlugin plugin;

	public SpawnerListener(FarmLimiterPlugin plugin, SpawnerManager spawnerManager) {
		this.plugin = plugin;
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
			sendEmptySpawnerActionbar(spawnerLocation, entityType);
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

	private void sendEmptySpawnerActionbar(Location location, EntityType entityType) {
		if (!spawnerManager.tryStartEmptyActionbarCooldown(location)) {
			return;
		}
		if (location.getWorld() == null) {
			return;
		}
		int radius = spawnerManager.getEmptyActionbarRadius();
		double radiusSquared = radius * radius;
		Component message = Component.text(plugin.getLangManager().get("spawner.empty-actionbar", Map.of(
				"entity", entityType.name()
		)));
		for (Player player : location.getWorld().getPlayers()) {
			if (player.getLocation().distanceSquared(location) <= radiusSquared) {
				player.sendActionBar(message);
			}
		}
	}

}