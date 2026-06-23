package basementhost.randomchad.chunkmobunload;

import org.bukkit.entity.LivingEntity;

public class ChunkMobUnloadCandidate {
	private final LivingEntity entity;
	private final ChunkMobUnloadCandidateType type;

	public ChunkMobUnloadCandidate(LivingEntity entity, ChunkMobUnloadCandidateType type) {
		this.entity = entity;
		this.type = type;
	}

	public LivingEntity getEntity() {
		return entity;
	}

	public ChunkMobUnloadCandidateType getType() {
		return type;
	}
}