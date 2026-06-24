package basementhost.randomchad.chunkmobunload;

import org.bukkit.Chunk;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;

import java.util.*;

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

	public List<ChunkMobUnloadCandidate> getLivingEntityCandidates(
			Chunk chunk,
			ChunkMobUnloadManager manager
	) {
		List<ChunkMobUnloadCandidate> candidates = new ArrayList<>();

		for (Entity entity : chunk.getEntities()) {
			if (!(entity instanceof LivingEntity livingEntity)) {
				continue;
			}

			ChunkMobUnloadCandidate candidate = createCandidate(livingEntity, manager);

			if (candidate != null) {
				candidates.add(candidate);
			}
		}

		sortCandidates(candidates, chunk, manager);
		return candidates;
	}

	public List<ChunkMobUnloadCandidate> getEntityTypeCandidates(
			Chunk chunk,
			EntityType entityType,
			ChunkMobUnloadManager manager
	) {
		List<ChunkMobUnloadCandidate> candidates = new ArrayList<>();

		for (Entity entity : chunk.getEntities()) {
			if (entity.getType() != entityType) {
				continue;
			}

			if (!(entity instanceof LivingEntity livingEntity)) {
				continue;
			}

			ChunkMobUnloadCandidate candidate = createCandidate(livingEntity, manager);

			if (candidate != null) {
				candidates.add(candidate);
			}
		}

		sortCandidates(candidates, chunk, manager);
		return candidates;
	}

	public List<ChunkMobUnloadCandidate> getGroupCandidates(
			Chunk chunk,
			Set<EntityType> entityTypes,
			ChunkMobUnloadManager manager
	) {
		List<ChunkMobUnloadCandidate> candidates = new ArrayList<>();

		for (Entity entity : chunk.getEntities()) {
			if (!(entity instanceof LivingEntity livingEntity)) {
				continue;
			}

			if (!entityTypes.contains(entity.getType())) {
				continue;
			}

			ChunkMobUnloadCandidate candidate = createCandidate(livingEntity, manager);

			if (candidate != null) {
				candidates.add(candidate);
			}
		}

		sortCandidates(candidates, chunk, manager);
		return candidates;
	}

	private ChunkMobUnloadCandidate createCandidate(
			LivingEntity entity,
			ChunkMobUnloadManager manager
	) {
		if (entity instanceof Player) {
			return null;
		}

		boolean named = entity.customName() != null;
		boolean tamed = entity instanceof Tameable tameable && tameable.isTamed();

		if (tamed && manager.shouldRemoveTamedEntities()) {
			return new ChunkMobUnloadCandidate(entity, ChunkMobUnloadCandidateType.NORMAL);
		}

		if (tamed && manager.shouldRemoveTamedEntitiesAsSecondPriority()) {
			return new ChunkMobUnloadCandidate(entity, ChunkMobUnloadCandidateType.TAMED);
		}

		if (named && manager.shouldRemoveNamedEntities()) {
			return new ChunkMobUnloadCandidate(entity, ChunkMobUnloadCandidateType.NORMAL);
		}

		if (named && manager.shouldRemoveNamedEntitiesAsSecondPriority()) {
			return new ChunkMobUnloadCandidate(entity, ChunkMobUnloadCandidateType.NAMED);
		}

		if (named || tamed) {
			return null;
		}

		return new ChunkMobUnloadCandidate(entity, ChunkMobUnloadCandidateType.NORMAL);
	}

	private void sortCandidates(
			List<ChunkMobUnloadCandidate> candidates,
			Chunk chunk,
			ChunkMobUnloadManager manager
	) {
		Comparator<ChunkMobUnloadCandidate> priorityComparator = Comparator.comparingInt(candidate -> {
			if (candidate.getType() == ChunkMobUnloadCandidateType.NORMAL) {
				return 0;
			}
			if (candidate.getType() == ChunkMobUnloadCandidateType.NAMED) {
				return 1;
			}
			return 2;
		});
		ChunkMobUnloadSortMode sortMode = manager.getSortMode();
		if (sortMode == ChunkMobUnloadSortMode.RANDOM) {
			Collections.shuffle(candidates);
			candidates.sort(priorityComparator);
			return;
		}
		Comparator<ChunkMobUnloadCandidate> detailComparator = getDetailComparator(chunk, sortMode);
		candidates.sort(priorityComparator.thenComparing(detailComparator));
	}

	private Comparator<ChunkMobUnloadCandidate> getDetailComparator(
			Chunk chunk,
			ChunkMobUnloadSortMode sortMode
	) {
		double centerX = chunk.getX() * 16 + 8;
		double centerZ = chunk.getZ() * 16 + 8;

		if (sortMode == ChunkMobUnloadSortMode.NEAREST_TO_CHUNK_CENTER) {
			return Comparator.comparingDouble(candidate -> getDistanceSquaredTo(
					candidate,
					centerX,
					centerZ
			));
		}

		if (sortMode == ChunkMobUnloadSortMode.FARTHEST_FROM_CHUNK_CENTER) {
			return Comparator.comparingDouble((ChunkMobUnloadCandidate candidate) -> getDistanceSquaredTo(
					candidate,
					centerX,
					centerZ
			)).reversed();
		}

		if (sortMode == ChunkMobUnloadSortMode.NEAREST_TO_PLAYER) {
			return Comparator.comparingDouble(candidate -> getDistanceSquaredToNearestPlayer(candidate, chunk));
		}

		if (sortMode == ChunkMobUnloadSortMode.FARTHEST_FROM_PLAYER) {
			return Comparator.comparingDouble(
					(ChunkMobUnloadCandidate candidate) -> getDistanceSquaredToNearestPlayer(candidate, chunk)
			).reversed();
		}

		return Comparator.comparingInt(candidate -> candidate.getEntity().getEntityId());
	}

	private double getDistanceSquaredTo(
			ChunkMobUnloadCandidate candidate,
			double x,
			double z
	) {
		double dx = candidate.getEntity().getLocation().getX() - x;
		double dz = candidate.getEntity().getLocation().getZ() - z;

		return dx * dx + dz * dz;
	}

	private double getDistanceSquaredToNearestPlayer(
			ChunkMobUnloadCandidate candidate,
			Chunk chunk
	) {
		double nearestDistanceSquared = Double.MAX_VALUE;

		for (Player player : chunk.getWorld().getPlayers()) {
			double distanceSquared = candidate.getEntity().getLocation().distanceSquared(player.getLocation());

			if (distanceSquared < nearestDistanceSquared) {
				nearestDistanceSquared = distanceSquared;
			}
		}

		return nearestDistanceSquared;
	}
}