package basementhost.randomchad.chunkloaderlimit;

import java.util.Objects;

public class ChunkLoaderPortalKey {
	private final String worldName;
	private final int x;
	private final int y;
	private final int z;

	public ChunkLoaderPortalKey(String worldName, int x, int y, int z) {
		this.worldName = worldName;
		this.x = x;
		this.y = y;
		this.z = z;
	}

	public String getWorldName() {
		return worldName;
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

	@Override
	public boolean equals(Object object) {
		if (this == object) {
			return true;
		}

		if (!(object instanceof ChunkLoaderPortalKey other)) {
			return false;
		}

		return x == other.x
				&& y == other.y
				&& z == other.z
				&& Objects.equals(worldName, other.worldName);
	}

	@Override
	public int hashCode() {
		return Objects.hash(worldName, x, y, z);
	}
}