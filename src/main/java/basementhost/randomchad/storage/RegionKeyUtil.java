package basementhost.randomchad.storage;

import org.bukkit.Chunk;

import java.io.File;

public final class RegionKeyUtil {

	private RegionKeyUtil() {
	}

	public static String getChunkKey(Chunk chunk) {
		String worldId = chunk.getWorld().getUID().toString();
		int x = chunk.getX();
		int z = chunk.getZ();

		return worldId + "_" + x + "_" + z;
	}

	public static String getRegionId(Chunk chunk, int regionSizeChunks) {
		String worldId = chunk.getWorld().getUID().toString();

		int safeRegionSizeChunks = Math.max(1, regionSizeChunks);

		int regionX = Math.floorDiv(chunk.getX(), safeRegionSizeChunks);
		int regionZ = Math.floorDiv(chunk.getZ(), safeRegionSizeChunks);

		return worldId + "_" + regionX + "_" + regionZ;
	}

	public static String getRegionIdFromChunkKey(String chunkKey, int regionSizeChunks) {
		String[] parts = chunkKey.split("_");

		if (parts.length != 3) {
			return "unknown_0_0";
		}

		String worldId = parts[0];

		int chunkX;
		int chunkZ;

		try {
			chunkX = Integer.parseInt(parts[1]);
			chunkZ = Integer.parseInt(parts[2]);
		} catch (NumberFormatException exception) {
			return worldId + "_0_0";
		}

		int safeRegionSizeChunks = Math.max(1, regionSizeChunks);

		int regionX = Math.floorDiv(chunkX, safeRegionSizeChunks);
		int regionZ = Math.floorDiv(chunkZ, safeRegionSizeChunks);

		return worldId + "_" + regionX + "_" + regionZ;
	}

	public static File getRegionFile(File dataRootFolder, String regionId) {
		String[] parts = regionId.split("_");

		if (parts.length != 3) {
			return new File(dataRootFolder, "unknown/r.0.0.yml");
		}

		String worldId = parts[0];
		String regionX = parts[1];
		String regionZ = parts[2];

		File worldFolder = new File(dataRootFolder, worldId);

		return new File(worldFolder, "r." + regionX + "." + regionZ + ".yml");
	}
}