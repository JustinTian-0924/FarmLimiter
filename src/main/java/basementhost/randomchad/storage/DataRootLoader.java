package basementhost.randomchad.storage;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class DataRootLoader {

	private DataRootLoader() {
	}

	public static File load(JavaPlugin plugin, String moduleFolderName) {
		File dataFolder = new File(plugin.getDataFolder(), "data");

		if (!dataFolder.exists()) {
			dataFolder.mkdirs();
		}

		File dataRootFolder = new File(dataFolder, moduleFolderName);

		if (!dataRootFolder.exists()) {
			dataRootFolder.mkdirs();
		}

		return dataRootFolder;
	}
}