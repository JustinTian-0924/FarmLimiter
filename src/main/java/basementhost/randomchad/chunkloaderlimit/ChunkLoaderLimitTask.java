package basementhost.randomchad.chunkloaderlimit;

import basementhost.randomchad.FarmLimiterPlugin;
import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.UUID;

public class ChunkLoaderLimitTask extends BukkitRunnable {
	private final FarmLimiterPlugin plugin;
	private final ChunkLoaderLimitManager chunkLoaderLimitManager;
	private final NamespacedKey pearlCreatedAtKey;
	private final NamespacedKey pearlOwnerKey;

	public ChunkLoaderLimitTask(FarmLimiterPlugin plugin) {
		this.plugin = plugin;
		this.chunkLoaderLimitManager = plugin.getChunkLoaderLimitManager();
		this.pearlCreatedAtKey = new NamespacedKey(plugin, "chunkloader_ender_pearl_created_at");
		this.pearlOwnerKey = new NamespacedKey(plugin, "chunkloader_ender_pearl_owner");
	}

	@Override
	public void run() {
		if (!chunkLoaderLimitManager.isEnabled()) {
			return;
		}

		if (!chunkLoaderLimitManager.isEnderPearlEnabled()) {
			return;
		}

		for (World world : plugin.getServer().getWorlds()) {
			if (!chunkLoaderLimitManager.isWorldAllowed(world)) {
				continue;
			}

			for (EnderPearl enderPearl : world.getEntitiesByClass(EnderPearl.class)) {
				checkEnderPearl(enderPearl);
			}
		}
	}

	private void checkEnderPearl(EnderPearl enderPearl) {
		PersistentDataContainer dataContainer = enderPearl.getPersistentDataContainer();
		Long createdAt = dataContainer.get(pearlCreatedAtKey, PersistentDataType.LONG);

		if (createdAt == null) {
			return;
		}

		long lifetimeMillis = System.currentTimeMillis() - createdAt;
		long maxLifetimeMillis = chunkLoaderLimitManager.getEnderPearlMaxLifetimeSeconds() * 1000L;

		if (lifetimeMillis < maxLifetimeMillis) {
			return;
		}

		notifyOwnerIfNeeded(enderPearl);
		debugRemoveEnderPearl(enderPearl, lifetimeMillis);

		enderPearl.remove();
	}

	private void notifyOwnerIfNeeded(EnderPearl enderPearl) {
		if (!chunkLoaderLimitManager.shouldNotifyEnderPearlOwnerOnRemove()) {
			return;
		}

		String ownerUuidText = enderPearl.getPersistentDataContainer().get(pearlOwnerKey, PersistentDataType.STRING);

		if (ownerUuidText == null) {
			return;
		}

		Player owner;

		try {
			owner = plugin.getServer().getPlayer(UUID.fromString(ownerUuidText));
		} catch (IllegalArgumentException exception) {
			return;
		}

		if (owner == null) {
			return;
		}

		owner.sendMessage(Component.text(plugin.getLangManager().get("chunkloader.ender-pearl-removed", Map.of(
				"seconds", chunkLoaderLimitManager.getEnderPearlMaxLifetimeSeconds()
		))));
	}

	private void debugRemoveEnderPearl(EnderPearl enderPearl, long lifetimeMillis) {
		if (!chunkLoaderLimitManager.isDebugEnabled()) {
			return;
		}

		plugin.getLogger().info("Removed expired ender pearl by Chunk_Loader_Limit after "
				+ lifetimeMillis / 1000L
				+ "s at "
				+ enderPearl.getWorld().getName()
				+ " "
				+ enderPearl.getLocation().getBlockX()
				+ ","
				+ enderPearl.getLocation().getBlockY()
				+ ","
				+ enderPearl.getLocation().getBlockZ());
	}
}