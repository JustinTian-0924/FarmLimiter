package basementhost.randomchad.storage;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;

public final class RegionFileIoHelper {

	private RegionFileIoHelper() {
	}

	public static boolean deleteEmptyRegionFile(
			JavaPlugin plugin,
			File regionFile,
			String warningMessagePrefix
	) {
		if (!regionFile.exists()) {
			return true;
		}

		if (regionFile.delete()) {
			return true;
		}

		plugin.getLogger().warning(warningMessagePrefix + ": " + regionFile.getPath());
		return false;
	}

	public static void ensureParentFolderExists(File regionFile) {
		File parentFolder = regionFile.getParentFile();

		if (parentFolder != null && !parentFolder.exists()) {
			parentFolder.mkdirs();
		}
	}

	public static void saveRegionConfig(
			JavaPlugin plugin,
			YamlConfiguration regionConfig,
			File regionFile,
			String warningMessagePrefix
	) {
		try {
			regionConfig.save(regionFile);
		} catch (IOException e) {
			plugin.getLogger().warning(warningMessagePrefix + ": " + regionFile.getPath());
			e.printStackTrace();
		}
	}
}