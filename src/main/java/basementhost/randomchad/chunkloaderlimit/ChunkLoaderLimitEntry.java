package basementhost.randomchad.chunkloaderlimit;

public class ChunkLoaderLimitEntry {
	private final String type;
	private final String entityType;
	private final String world;
	private final int x;
	private final int y;
	private final int z;
	private final int portalTeleports;
	private final long enderPearlAgeSeconds;

	public ChunkLoaderLimitEntry(
			String type,
			String entityType,
			String world,
			int x,
			int y,
			int z,
			int portalTeleports,
			long enderPearlAgeSeconds
	) {
		this.type = type;
		this.entityType = entityType;
		this.world = world;
		this.x = x;
		this.y = y;
		this.z = z;
		this.portalTeleports = portalTeleports;
		this.enderPearlAgeSeconds = enderPearlAgeSeconds;
	}

	public String getType() {
		return type;
	}

	public String getEntityType() {
		return entityType;
	}

	public String getWorld() {
		return world;
	}

	public int getX() {
		return x;
	}

	public int getY() {
		return y;
	}

	public int getZ() {
		return z;
	}

	public int getPortalTeleports() {
		return portalTeleports;
	}

	public long getEnderPearlAgeSeconds() {
		return enderPearlAgeSeconds;
	}
}