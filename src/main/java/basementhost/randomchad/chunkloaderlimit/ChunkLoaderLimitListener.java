package basementhost.randomchad.chunkloaderlimit;

import basementhost.randomchad.FarmLimiterPlugin;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
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

import java.util.*;

public class ChunkLoaderLimitListener implements Listener {
	private final FarmLimiterPlugin plugin;
	private final ChunkLoaderLimitManager chunkLoaderLimitManager;
	private final NamespacedKey pearlCreatedAtKey;
	private final NamespacedKey pearlOwnerKey;
	private final NamespacedKey portalTeleportCountKey;
	private final Map<ChunkLoaderPortalKey, Queue<Long>> portalMinecartTeleportTimes = new HashMap<>();

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
			if (handleMinecartPortalRateLimit(event, minecart)) {
				return;
			}

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

	private boolean handleMinecartPortalRateLimit(EntityPortalEvent event, Minecart minecart) {
		if (!chunkLoaderLimitManager.isPortalRateLimitEnabled()) {
			return false;
		}

		Block portalBlock = findNearbyPortalBlock(minecart.getLocation());

		if (portalBlock == null) {
			return false;
		}

		List<Block> portalBlocks = findConnectedPortalBlocks(portalBlock);

		if (portalBlocks.isEmpty()) {
			return false;
		}

		ChunkLoaderPortalKey portalKey = createPortalKey(portalBlocks);
		int count = recordPortalMinecartTeleport(portalKey);

		int maxTeleports = chunkLoaderLimitManager.getPortalRateLimitMaxMinecartTeleports();

		if (count <= maxTeleports) {
			debugPortalRate(minecart, portalKey, count, maxTeleports);
			return false;
		}

		event.setCancelled(true);

		if (chunkLoaderLimitManager.shouldBreakPortalOnRateLimit()) {
			breakPortalBlocks(portalBlocks);
		}

		notifyPortalRateLimitAdmins(minecart, portalKey, count, maxTeleports);
		debugPortalRateLimitExceeded(minecart, portalKey, count, maxTeleports);

		return true;
	}

	private Block findNearbyPortalBlock(Location location) {
		World world = location.getWorld();

		if (world == null) {
			return null;
		}

		int baseX = location.getBlockX();
		int baseY = location.getBlockY();
		int baseZ = location.getBlockZ();

		for (int x = baseX - 2; x <= baseX + 2; x++) {
			for (int y = baseY - 2; y <= baseY + 2; y++) {
				for (int z = baseZ - 2; z <= baseZ + 2; z++) {
					Block block = world.getBlockAt(x, y, z);

					if (block.getType() == Material.NETHER_PORTAL) {
						return block;
					}
				}
			}
		}

		return null;
	}

	private List<Block> findConnectedPortalBlocks(Block startBlock) {
		List<Block> portalBlocks = new ArrayList<>();
		Set<String> visited = new HashSet<>();
		Queue<Block> queue = new ArrayDeque<>();

		queue.add(startBlock);

		while (!queue.isEmpty()) {
			Block block = queue.poll();
			String key = block.getWorld().getName()
					+ ":"
					+ block.getX()
					+ ":"
					+ block.getY()
					+ ":"
					+ block.getZ();

			if (visited.contains(key)) {
				continue;
			}

			visited.add(key);

			if (block.getType() != Material.NETHER_PORTAL) {
				continue;
			}

			portalBlocks.add(block);

			if (portalBlocks.size() >= 128) {
				break;
			}

			queue.add(block.getRelative(1, 0, 0));
			queue.add(block.getRelative(-1, 0, 0));
			queue.add(block.getRelative(0, 1, 0));
			queue.add(block.getRelative(0, -1, 0));
			queue.add(block.getRelative(0, 0, 1));
			queue.add(block.getRelative(0, 0, -1));
		}

		return portalBlocks;
	}

	private ChunkLoaderPortalKey createPortalKey(List<Block> portalBlocks) {
		Block first = portalBlocks.get(0);

		int minX = first.getX();
		int minY = first.getY();
		int minZ = first.getZ();

		for (Block block : portalBlocks) {
			minX = Math.min(minX, block.getX());
			minY = Math.min(minY, block.getY());
			minZ = Math.min(minZ, block.getZ());
		}

		return new ChunkLoaderPortalKey(first.getWorld().getName(), minX, minY, minZ);
	}

	private int recordPortalMinecartTeleport(ChunkLoaderPortalKey portalKey) {
		long now = System.currentTimeMillis();
		long windowMillis = chunkLoaderLimitManager.getPortalRateLimitWindowSeconds() * 1000L;
		Queue<Long> teleportTimes = portalMinecartTeleportTimes.computeIfAbsent(
				portalKey,
				key -> new ArrayDeque<>()
		);
		while (!teleportTimes.isEmpty() && now - teleportTimes.peek() > windowMillis) {
			teleportTimes.poll();
		}
		teleportTimes.add(now);
		return teleportTimes.size();
	}

	private void breakPortalBlocks(List<Block> portalBlocks) {
		for (Block block : portalBlocks) {
			if (block.getType() == Material.NETHER_PORTAL) {
				block.setType(Material.AIR);
			}
		}
	}

	private void notifyPortalRateLimitAdmins(
			Minecart minecart,
			ChunkLoaderPortalKey portalKey,
			int count,
			int maxTeleports
	) {
		if (!chunkLoaderLimitManager.shouldNotifyAdminsOnPortalRateLimit()) {
			return;
		}

		if (!chunkLoaderLimitManager.shouldNotifyAdminOnPortalLimit()) {
			return;
		}

		int radius = chunkLoaderLimitManager.getAdminNotifyRadiusBlocks();

		if (radius <= 0) {
			return;
		}

		double radiusSquared = radius * radius;

		for (Player player : minecart.getWorld().getPlayers()) {
			if (!player.hasPermission("farmlimiter.admin")) {
				continue;
			}

			if (player.getLocation().distanceSquared(minecart.getLocation()) > radiusSquared) {
				continue;
			}

			player.sendMessage(Component.text(plugin.getLangManager().get("chunkloader.portal-rate-limit-admin-notify", Map.of(
					"world", portalKey.getWorldName(),
					"x", portalKey.getX(),
					"y", portalKey.getY(),
					"z", portalKey.getZ(),
					"count", count,
					"max", maxTeleports,
					"seconds", chunkLoaderLimitManager.getPortalRateLimitWindowSeconds()
			))));
		}
	}

	private void debugPortalRate(
			Minecart minecart,
			ChunkLoaderPortalKey portalKey,
			int count,
			int maxTeleports
	) {
		if (!chunkLoaderLimitManager.isDebugEnabled()) {
			return;
		}

		plugin.getLogger().info("Tracked portal minecart rate by Chunk_Loader_Limit: portal="
				+ portalKey.getWorldName()
				+ " "
				+ portalKey.getX()
				+ ","
				+ portalKey.getY()
				+ ","
				+ portalKey.getZ()
				+ " count="
				+ count
				+ "/"
				+ maxTeleports
				+ " minecart="
				+ minecart.getType().name());
	}

	private void debugPortalRateLimitExceeded(
			Minecart minecart,
			ChunkLoaderPortalKey portalKey,
			int count,
			int maxTeleports
	) {
		if (!chunkLoaderLimitManager.isDebugEnabled()) {
			return;
		}

		plugin.getLogger().info("Broke portal by Chunk_Loader_Limit minecart rate limit: portal="
				+ portalKey.getWorldName()
				+ " "
				+ portalKey.getX()
				+ ","
				+ portalKey.getY()
				+ ","
				+ portalKey.getZ()
				+ " count="
				+ count
				+ "/"
				+ maxTeleports
				+ " minecart="
				+ minecart.getType().name());
	}
}