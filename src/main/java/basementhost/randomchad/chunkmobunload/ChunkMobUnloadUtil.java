package basementhost.randomchad.chunkmobunload;

import org.bukkit.Chunk;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;

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
}