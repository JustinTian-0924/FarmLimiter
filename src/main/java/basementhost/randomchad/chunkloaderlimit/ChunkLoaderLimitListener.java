package basementhost.randomchad.chunkloaderlimit;

import basementhost.randomchad.FarmLimiterPlugin;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Boat;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.projectiles.ProjectileSource;

import java.util.Map;

public class ChunkLoaderLimitListener implements Listener {
	private final FarmLimiterPlugin plugin;
	private final ChunkLoaderLimitManager chunkLoaderLimitManager;
	private final NamespacedKey pearlCreatedAtKey;
	private final NamespacedKey pearlOwnerKey;
	private final NamespacedKey portalTeleportCountKey;

	public ChunkLoaderLimitListener(FarmLimiterPlugin plugin) {
		this.plugin = plugin;
		this.chunkLoaderLimitManager = plugin.getChunkLoaderLimitManager();
		this.pearlCreatedAtKey = chunkLoaderLimitManager.getEnderPearlCreatedAtKey();
		this.pearlOwnerKey = new NamespacedKey(plugin, "chunkloader_ender_pearl_owner");
		this.portalTeleportCountKey = chunkLoaderLimitManager.getPortalTeleportCountKey();
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

	@EventHandler(ignoreCancelled = true)
	public void onEntityPortal(EntityPortalEvent event) {
		if (!chunkLoaderLimitManager.isEnabled()) {
			return;
		}

		if (!chunkLoaderLimitManager.isPortalEntityEnabled()) {
			return;
		}

		Entity entity = event.getEntity();

		if (!chunkLoaderLimitManager.isWorldAllowed(entity.getWorld())) {
			return;
		}

		if (chunkLoaderLimitManager.isEntityTypeIgnored(entity.getType())) {
			return;
		}

		if (entity instanceof Player) {
			return;
		}

		if (entity instanceof Minecart minecart) {
			handleMinecartPortal(event, minecart);
			return;
		}

		if (entity instanceof Boat boat) {
			handleBoatPortal(event, boat);
			return;
		}

		if (entity instanceof Item item) {
			handleItemPortal(event, item);
			return;
		}

		if (entity instanceof LivingEntity livingEntity) {
			handleLivingEntityPortal(event, livingEntity);
		}
	}

	private void handleMinecartPortal(EntityPortalEvent event, Minecart minecart) {
		if (!chunkLoaderLimitManager.isMinecartPortalLimitEnabled()) {
			return;
		}

		int teleportCount = increasePortalTeleportCount(minecart);
		int maxTeleports = chunkLoaderLimitManager.getMinecartMaxPortalTeleports();

		if (teleportCount <= maxTeleports) {
			debugPortalTeleport(minecart, "minecart", teleportCount, maxTeleports);
			return;
		}

		event.setCancelled(true);

		if (chunkLoaderLimitManager.shouldConvertMinecartToItem()) {
			minecart.getWorld().dropItemNaturally(
					minecart.getLocation(),
					new ItemStack(getMinecartItemMaterial(minecart.getType()), 1)
			);
		}

		debugPortalLimitExceeded(minecart, "minecart", teleportCount, maxTeleports, true);
		notifyNearbyAdmins(minecart, "minecart", teleportCount, maxTeleports);
		minecart.remove();
	}

	private void handleBoatPortal(EntityPortalEvent event, Boat boat) {
		if (!chunkLoaderLimitManager.isBoatPortalLimitEnabled()) {
			return;
		}

		int teleportCount = increasePortalTeleportCount(boat);
		int maxTeleports = chunkLoaderLimitManager.getBoatMaxPortalTeleports();

		if (teleportCount <= maxTeleports) {
			debugPortalTeleport(boat, "boat", teleportCount, maxTeleports);
			return;
		}

		event.setCancelled(true);

		if (chunkLoaderLimitManager.shouldConvertBoatToItem()) {
			boat.getWorld().dropItemNaturally(
					boat.getLocation(),
					new ItemStack(Material.OAK_BOAT, 1)
			);
		}

		debugPortalLimitExceeded(boat, "boat", teleportCount, maxTeleports, true);
		notifyNearbyAdmins(boat, "boat", teleportCount, maxTeleports);
		boat.remove();
	}

	private void handleItemPortal(EntityPortalEvent event, Item item) {
		if (!chunkLoaderLimitManager.isItemPortalLimitEnabled()) {
			return;
		}

		int teleportCount = increasePortalTeleportCount(item);
		int maxTeleports = chunkLoaderLimitManager.getItemMaxPortalTeleports();

		if (teleportCount <= maxTeleports) {
			debugPortalTeleport(item, "item", teleportCount, maxTeleports);
			return;
		}

		event.setCancelled(true);

		if (chunkLoaderLimitManager.shouldRemoveItemOnExceed()) {
			debugPortalLimitExceeded(item, "item", teleportCount, maxTeleports, true);
			notifyNearbyAdmins(item, "item", teleportCount, maxTeleports);
			item.remove();
			return;
		}

		debugPortalLimitExceeded(item, "item", teleportCount, maxTeleports, false);
		notifyNearbyAdmins(item, "item", teleportCount, maxTeleports);
	}

	private void handleLivingEntityPortal(EntityPortalEvent event, LivingEntity livingEntity) {
		if (!chunkLoaderLimitManager.isLivingEntityPortalLimitEnabled()) {
			return;
		}

		int teleportCount = increasePortalTeleportCount(livingEntity);
		int maxTeleports = chunkLoaderLimitManager.getLivingEntityMaxPortalTeleports();

		if (teleportCount <= maxTeleports) {
			debugPortalTeleport(livingEntity, "living entity", teleportCount, maxTeleports);
			return;
		}

		event.setCancelled(true);

		if (chunkLoaderLimitManager.shouldRemoveLivingEntityOnExceed()) {
			debugPortalLimitExceeded(livingEntity, "living entity", teleportCount, maxTeleports, true);
			notifyNearbyAdmins(livingEntity, "living entity", teleportCount, maxTeleports);
			livingEntity.remove();
			return;
		}

		debugPortalLimitExceeded(livingEntity, "living entity", teleportCount, maxTeleports, false);
		notifyNearbyAdmins(livingEntity, "living entity", teleportCount, maxTeleports);
	}

	private int increasePortalTeleportCount(Entity entity) {
		PersistentDataContainer dataContainer = entity.getPersistentDataContainer();
		int current = dataContainer.getOrDefault(
				portalTeleportCountKey,
				PersistentDataType.INTEGER,
				0
		);

		int updated = current + 1;

		dataContainer.set(
				portalTeleportCountKey,
				PersistentDataType.INTEGER,
				updated
		);

		return updated;
	}

	private Material getMinecartItemMaterial(EntityType entityType) {
		if (entityType == EntityType.CHEST_MINECART) {
			return Material.CHEST_MINECART;
		}

		if (entityType == EntityType.FURNACE_MINECART) {
			return Material.FURNACE_MINECART;
		}

		if (entityType == EntityType.HOPPER_MINECART) {
			return Material.HOPPER_MINECART;
		}

		if (entityType == EntityType.TNT_MINECART) {
			return Material.TNT_MINECART;
		}

		if (entityType == EntityType.COMMAND_BLOCK_MINECART) {
			return Material.COMMAND_BLOCK_MINECART;
		}

		return Material.MINECART;
	}

	private void debugPortalTeleport(Entity entity, String typeName, int teleportCount, int maxTeleports) {
		if (!chunkLoaderLimitManager.isDebugEnabled()) {
			return;
		}

		plugin.getLogger().info("Tracked " + typeName + " portal teleport by Chunk_Loader_Limit: "
				+ entity.getType().name()
				+ " count="
				+ teleportCount
				+ "/"
				+ maxTeleports
				+ " at "
				+ entity.getWorld().getName()
				+ " "
				+ entity.getLocation().getBlockX()
				+ ","
				+ entity.getLocation().getBlockY()
				+ ","
				+ entity.getLocation().getBlockZ());
	}

	private void debugPortalLimitExceeded(
			Entity entity,
			String typeName,
			int teleportCount,
			int maxTeleports,
			boolean removed
	) {
		if (!chunkLoaderLimitManager.isDebugEnabled()) {
			return;
		}

		plugin.getLogger().info("Blocked " + typeName + " portal chunk loader by Chunk_Loader_Limit: "
				+ entity.getType().name()
				+ " count="
				+ teleportCount
				+ "/"
				+ maxTeleports
				+ " removed="
				+ removed
				+ " at "
				+ entity.getWorld().getName()
				+ " "
				+ entity.getLocation().getBlockX()
				+ ","
				+ entity.getLocation().getBlockY()
				+ ","
				+ entity.getLocation().getBlockZ());
	}

	private void notifyNearbyAdmins(
			Entity entity,
			String typeName,
			int teleportCount,
			int maxTeleports
	) {
		if (!chunkLoaderLimitManager.shouldNotifyAdminOnPortalLimit()) {
			return;
		}

		int radius = chunkLoaderLimitManager.getAdminNotifyRadiusBlocks();

		if (radius <= 0) {
			return;
		}

		double radiusSquared = radius * radius;

		for (Player player : entity.getWorld().getPlayers()) {
			if (!player.hasPermission("farmlimiter.admin")) {
				continue;
			}

			if (player.getLocation().distanceSquared(entity.getLocation()) > radiusSquared) {
				continue;
			}

			player.sendMessage(Component.text(plugin.getLangManager().get("chunkloader.portal-limit-admin-notify", Map.of(
					"type", typeName,
					"entity", entity.getType().name(),
					"count", teleportCount,
					"max", maxTeleports,
					"x", entity.getLocation().getBlockX(),
					"y", entity.getLocation().getBlockY(),
					"z", entity.getLocation().getBlockZ()
			))));
		}
	}
}