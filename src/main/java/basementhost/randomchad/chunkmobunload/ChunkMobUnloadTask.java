package basementhost.randomchad.chunkmobunload;

import basementhost.randomchad.FarmLimiterPlugin;
import net.kyori.adventure.text.Component;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ChunkMobUnloadTask extends BukkitRunnable {
	private final FarmLimiterPlugin plugin;
	private final ChunkMobUnloadManager chunkMobUnloadManager;
	private final ChunkMobUnloadUtil chunkMobUnloadUtil;
	private final Map<String, Long> warnedChunks = new HashMap<>();

	public ChunkMobUnloadTask(FarmLimiterPlugin plugin) {
		this.plugin = plugin;
		this.chunkMobUnloadManager = plugin.getChunkMobUnloadManager();
		this.chunkMobUnloadUtil = new ChunkMobUnloadUtil();
	}

	@Override
	public void run() {
		if (!chunkMobUnloadManager.isEnabled()) {
			return;
		}

		for (World world : plugin.getServer().getWorlds()) {
			for (Chunk chunk : world.getLoadedChunks()) {
				cleanupChunk(chunk);
			}
		}
	}

	private void cleanupChunk(Chunk chunk) {
		if (shouldWarnBeforeCleanup(chunk)) {
			warnNearbyPlayers(chunk);
			markChunkWarned(chunk);
			return;
		}

		Map<EntityType, Integer> removedCounts = new HashMap<>();

		cleanupEntityHardLimits(chunk, removedCounts);
		cleanupGroupHardLimits(chunk, removedCounts);
		cleanupTotalHardLimit(chunk, removedCounts);

		if (!removedCounts.isEmpty()) {
			notifyCleanupResult(chunk, removedCounts);
		}

		clearChunkWarning(chunk);
	}

	private void cleanupEntityHardLimits(Chunk chunk, Map<EntityType, Integer> removedCounts) {
		for (Map.Entry<EntityType, ChunkMobUnloadRule> entry : chunkMobUnloadManager.getEntityRules().entrySet()) {
			EntityType entityType = entry.getKey();
			ChunkMobUnloadRule rule = entry.getValue();

			int current = chunkMobUnloadUtil.countEntityType(chunk, entityType);

			if (current <= rule.getHardLimit()) {
				continue;
			}

			List<ChunkMobUnloadCandidate> candidates = chunkMobUnloadUtil.getEntityTypeCandidates(
					chunk,
					entityType,
					chunkMobUnloadManager
			);

			int removeAmount = calculateRemoveAmount(
					current,
					rule.getHardLimit(),
					candidates.size()
			);

			removeCandidates(candidates, removeAmount, "entity " + entityType.name(), chunk, removedCounts);
		}
	}

	private void cleanupGroupHardLimits(Chunk chunk, Map<EntityType, Integer> removedCounts) {
		for (Map.Entry<String, ChunkMobUnloadRule> entry : chunkMobUnloadManager.getGroupRules().entrySet()) {
			String groupName = entry.getKey();
			ChunkMobUnloadRule rule = entry.getValue();
			Set<EntityType> entityTypes = chunkMobUnloadManager.getGroupEntities(groupName);

			int current = chunkMobUnloadUtil.countGroup(chunk, entityTypes);

			if (current <= rule.getHardLimit()) {
				continue;
			}

			List<ChunkMobUnloadCandidate> candidates = chunkMobUnloadUtil.getGroupCandidates(
					chunk,
					entityTypes,
					chunkMobUnloadManager
			);

			int removeAmount = calculateRemoveAmount(
					current,
					rule.getHardLimit(),
					candidates.size()
			);

			removeCandidates(candidates, removeAmount, "group " + groupName, chunk, removedCounts);
		}
	}

	private void cleanupTotalHardLimit(Chunk chunk, Map<EntityType, Integer> removedCounts) {
		ChunkMobUnloadRule rule = chunkMobUnloadManager.getTotalRule();
		int current = chunkMobUnloadUtil.countLivingEntities(chunk);

		if (current <= rule.getHardLimit()) {
			return;
		}

		List<ChunkMobUnloadCandidate> candidates = chunkMobUnloadUtil.getLivingEntityCandidates(
				chunk,
				chunkMobUnloadManager
		);

		int removeAmount = calculateRemoveAmount(
				current,
				rule.getHardLimit(),
				candidates.size()
		);

		removeCandidates(candidates, removeAmount, "total", chunk, removedCounts);
	}

	private int calculateRemoveAmount(int current, int hardLimit, int candidateSize) {
		int excess = Math.max(0, current - hardLimit);
		int removeValue = chunkMobUnloadManager.getRemoveValue();

		int amount;

		switch (chunkMobUnloadManager.getRemoveMode()) {
			case FIXED -> amount = removeValue;
			case TOTAL_PERCENT -> amount = (int) Math.ceil(current * (removeValue / 100.0));
			case EXCESS_PERCENT -> amount = (int) Math.ceil(excess * (removeValue / 100.0));
			case ALL_EXCESS -> amount = excess;
			case ALL -> amount = candidateSize;
			default -> amount = excess;
		}

		if (amount <= 0 && excess > 0) {
			amount = 1;
		}

		return Math.min(amount, candidateSize);
	}

	private void removeCandidates(
			List<ChunkMobUnloadCandidate> candidates,
			int removeAmount,
			String reason,
			Chunk chunk,
			Map<EntityType, Integer> removedCounts
	) {
		if (removeAmount <= 0) {
			return;
		}
		int removed = 0;
		for (ChunkMobUnloadCandidate candidate : candidates) {
			if (removed >= removeAmount) {
				break;
			}
			EntityType entityType = candidate.getEntity().getType();
			candidate.getEntity().remove();
			removed++;
			removedCounts.put(entityType, removedCounts.getOrDefault(entityType, 0) + 1);
		}
		debugCleanup(reason, removed, chunk);
	}

	private boolean shouldWarnBeforeCleanup(Chunk chunk) {
		if (!chunkMobUnloadManager.isWarningEnabled()) {
			return false;
		}

		if (!isChunkOverAnyHardLimit(chunk)) {
			return false;
		}

		if (!hasNearbyPlayers(chunk)) {
			return false;
		}

		String key = getChunkKey(chunk);
		long now = System.currentTimeMillis();
		long lastWarnTime = warnedChunks.getOrDefault(key, 0L);
		long cooldownMillis = chunkMobUnloadManager.getWarningCooldownSeconds() * 1000L;

		if (cooldownMillis <= 0) {
			return !warnedChunks.containsKey(key);
		}

		return now - lastWarnTime >= cooldownMillis;
	}

	private boolean isChunkOverAnyHardLimit(Chunk chunk) {
		ChunkMobUnloadRule totalRule = chunkMobUnloadManager.getTotalRule();

		if (totalRule != null && chunkMobUnloadUtil.countLivingEntities(chunk) > totalRule.getHardLimit()) {
			return true;
		}

		for (Map.Entry<EntityType, ChunkMobUnloadRule> entry : chunkMobUnloadManager.getEntityRules().entrySet()) {
			if (chunkMobUnloadUtil.countEntityType(chunk, entry.getKey()) > entry.getValue().getHardLimit()) {
				return true;
			}
		}

		for (Map.Entry<String, ChunkMobUnloadRule> entry : chunkMobUnloadManager.getGroupRules().entrySet()) {
			Set<EntityType> entityTypes = chunkMobUnloadManager.getGroupEntities(entry.getKey());

			if (chunkMobUnloadUtil.countGroup(chunk, entityTypes) > entry.getValue().getHardLimit()) {
				return true;
			}
		}

		return false;
	}

	private boolean hasNearbyPlayers(Chunk chunk) {
		for (Player player : chunk.getWorld().getPlayers()) {
			if (isPlayerNearChunk(player, chunk)) {
				return true;
			}
		}

		return false;
	}

	private void warnNearbyPlayers(Chunk chunk) {
		for (Player player : chunk.getWorld().getPlayers()) {
			if (!isPlayerNearChunk(player, chunk)) {
				continue;
			}

			player.sendMessage(Component.text(plugin.getLangManager().get("chunkmob.cleanup-warning", Map.of(
					"x", chunk.getX(),
					"z", chunk.getZ()
			))));
		}
	}

	private boolean isPlayerNearChunk(Player player, Chunk chunk) {
		int radius = chunkMobUnloadManager.getWarningRadiusBlocks();

		if (radius <= 0) {
			return false;
		}

		double centerX = chunk.getX() * 16 + 8;
		double centerZ = chunk.getZ() * 16 + 8;
		double dx = player.getLocation().getX() - centerX;
		double dz = player.getLocation().getZ() - centerZ;

		return dx * dx + dz * dz <= radius * radius;
	}

	private String getChunkKey(Chunk chunk) {
		return chunk.getWorld().getName() + ":" + chunk.getX() + ":" + chunk.getZ();
	}

	private void markChunkWarned(Chunk chunk) {
		warnedChunks.put(getChunkKey(chunk), System.currentTimeMillis());
	}

	private void clearChunkWarning(Chunk chunk) {
		warnedChunks.remove(getChunkKey(chunk));
	}

	private void debugCleanup(String reason, int removed, Chunk chunk) {
		if (!chunkMobUnloadManager.isDebugEnabled()) {
			return;
		}

		plugin.getLogger().info("Chunk_Mob_Unload removed "
				+ removed
				+ " entities by "
				+ reason
				+ " at "
				+ chunk.getWorld().getName()
				+ " "
				+ chunk.getX()
				+ ","
				+ chunk.getZ());
	}

	private void notifyCleanupResult(Chunk chunk, Map<EntityType, Integer> removedCounts) {
		if (!chunkMobUnloadManager.shouldNotifyCleanupResult()) {
			return;
		}
		String summary = buildCleanupSummary(removedCounts);
		for (Player player : chunk.getWorld().getPlayers()) {
			if (!isPlayerNearCleanupResultChunk(player, chunk)) {
				continue;
			}
			player.sendMessage(Component.text(plugin.getLangManager().get("chunkmob.cleanup-result", Map.of(
					"x", chunk.getX(),
					"z", chunk.getZ(),
					"summary", summary
			))));
		}
	}

	private String buildCleanupSummary(Map<EntityType, Integer> removedCounts) {
		StringBuilder builder = new StringBuilder();
		for (Map.Entry<EntityType, Integer> entry : removedCounts.entrySet()) {
			if (!builder.isEmpty()) {
				builder.append(", ");
			}
			builder.append(entry.getKey().name())
					.append(" x")
					.append(entry.getValue());
		}
		return builder.toString();
	}

	private boolean isPlayerNearCleanupResultChunk(Player player, Chunk chunk) {
		int radius = chunkMobUnloadManager.getCleanupResultRadiusBlocks();
		if (radius <= 0) {
			return false;
		}
		double centerX = chunk.getX() * 16 + 8;
		double centerZ = chunk.getZ() * 16 + 8;
		double dx = player.getLocation().getX() - centerX;
		double dz = player.getLocation().getZ() - centerZ;
		return dx * dx + dz * dz <= radius * radius;
	}
}