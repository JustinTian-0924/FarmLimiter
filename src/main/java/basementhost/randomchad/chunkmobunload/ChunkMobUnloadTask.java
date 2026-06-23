package basementhost.randomchad.chunkmobunload;

import basementhost.randomchad.FarmLimiterPlugin;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.Map;

public class ChunkMobUnloadTask extends BukkitRunnable {
	private final FarmLimiterPlugin plugin;
	private final ChunkMobUnloadManager chunkMobUnloadManager;
	private final ChunkMobUnloadUtil chunkMobUnloadUtil;

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
		cleanupEntityHardLimits(chunk);
		cleanupTotalHardLimit(chunk);
	}

	private void cleanupEntityHardLimits(Chunk chunk) {
		for (Map.Entry<EntityType, ChunkMobUnloadRule> entry : chunkMobUnloadManager.getEntityRules().entrySet()) {
			EntityType entityType = entry.getKey();
			ChunkMobUnloadRule rule = entry.getValue();

			int current = chunkMobUnloadUtil.countEntityType(chunk, entityType);

			if (current <= rule.getHardLimit()) {
				continue;
			}

			List<LivingEntity> candidates = chunkMobUnloadUtil.getRemovableEntityTypeEntities(
					chunk,
					entityType,
					chunkMobUnloadManager
			);

			int removeAmount = calculateRemoveAmount(
					current,
					rule.getHardLimit(),
					candidates.size()
			);

			removeEntities(candidates, removeAmount, "entity " + entityType.name(), chunk);
		}
	}

	private void cleanupTotalHardLimit(Chunk chunk) {
		ChunkMobUnloadRule rule = chunkMobUnloadManager.getTotalRule();
		int current = chunkMobUnloadUtil.countLivingEntities(chunk);

		if (current <= rule.getHardLimit()) {
			return;
		}

		List<LivingEntity> candidates = chunkMobUnloadUtil.getRemovableLivingEntities(
				chunk,
				chunkMobUnloadManager
		);

		int removeAmount = calculateRemoveAmount(
				current,
				rule.getHardLimit(),
				candidates.size()
		);

		removeEntities(candidates, removeAmount, "total", chunk);
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

	private void removeEntities(
			List<LivingEntity> candidates,
			int removeAmount,
			String reason,
			Chunk chunk
	) {
		if (removeAmount <= 0) {
			return;
		}

		int removed = 0;

		for (LivingEntity entity : candidates) {
			if (removed >= removeAmount) {
				break;
			}

			entity.remove();
			removed++;
		}

		debugCleanup(reason, removed, chunk);
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
}