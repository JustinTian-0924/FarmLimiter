package basementhost.randomchad.storage;

import java.util.Map;
import java.util.function.BiConsumer;

public final class RegionSnapshotWriteHelper {

	private RegionSnapshotWriteHelper() {
	}

	public static <T> void writeSnapshots(
			Object dataSaveLock,
			Map<String, T> snapshots,
			BiConsumer<String, T> writeRegionSnapshot
	) {
		synchronized (dataSaveLock) {
			for (Map.Entry<String, T> entry : snapshots.entrySet()) {
				writeRegionSnapshot.accept(entry.getKey(), entry.getValue());
			}
		}
	}
}