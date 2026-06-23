package basementhost.randomchad.chunkmobunload;

import org.bukkit.Chunk;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;

import java.util.Map;
import java.util.Set;

public class ChunkMobUnloadUtil {
	public int countLivingEntities(Chunk chunk) {
		int count = 0;

		for (Entity entity : chunk.getEntities()) {
			if (entity instanceof LivingEntity) {
				count++;
			}
		}

		return count;
	}

	public int countEntityType(Chunk chunk, EntityType entityType) {
		int count = 0;

		for (Entity entity : chunk.getEntities()) {
			if (entity.getType() == entityType && entity instanceof LivingEntity) {
				count++;
			}
		}

		return count;
	}

	public int countGroup(Chunk chunk, Set<EntityType> entityTypes) {
		int count = 0;

		for (Entity entity : chunk.getEntities()) {
			if (!(entity instanceof LivingEntity)) {
				continue;
			}

			if (entityTypes.contains(entity.getType())) {
				count++;
			}
		}

		return count;
	}

	public boolean isOverSoftLimit(
			Chunk chunk,
			EntityType entityType,
			ChunkMobUnloadManager manager
	) {
		ChunkMobUnloadRule totalRule = manager.getTotalRule();

		if (totalRule != null && countLivingEntities(chunk) >= totalRule.getSoftLimit()) {
			return true;
		}

		ChunkMobUnloadRule entityRule = manager.getEntityRule(entityType);

		if (entityRule != null && countEntityType(chunk, entityType) >= entityRule.getSoftLimit()) {
			return true;
		}

		for (Map.Entry<String, ChunkMobUnloadRule> entry : manager.getGroupRules().entrySet()) {
			Set<EntityType> entityTypes = manager.getGroupEntities(entry.getKey());

			if (!entityTypes.contains(entityType)) {
				continue;
			}

			if (countGroup(chunk, entityTypes) >= entry.getValue().getSoftLimit()) {
				return true;
			}
		}

		return false;
	}
}