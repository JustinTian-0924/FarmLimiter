package basementhost.randomchad.storage;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class RegionLoadState {

	private final Set<String> loadedRegionIds = new HashSet<>();
	private final Map<String, Long> dirtyRegionVersions = new HashMap<>();
	private final Map<String, Long> regionLastAccessTimes = new HashMap<>();

	private long dirtyVersionCounter = 0L;

	public void clear() {
		loadedRegionIds.clear();
		dirtyRegionVersions.clear();
		regionLastAccessTimes.clear();
		dirtyVersionCounter = 0L;
	}

	public boolean isLoaded(String regionId) {
		return loadedRegionIds.contains(regionId);
	}

	public void markLoaded(String regionId) {
		loadedRegionIds.add(regionId);
		touch(regionId);
	}

	public void touch(String regionId) {
		regionLastAccessTimes.put(regionId, System.currentTimeMillis());
	}

	public void markDirty(String regionId) {
		loadedRegionIds.add(regionId);
		touch(regionId);

		dirtyVersionCounter++;
		dirtyRegionVersions.put(regionId, dirtyVersionCounter);
	}

	public boolean isDirty(String regionId) {
		return dirtyRegionVersions.containsKey(regionId);
	}

	public Map<String, Long> snapshotDirtyVersions() {
		return new HashMap<>(dirtyRegionVersions);
	}

	public void clearDirtyIfVersionMatches(String regionId, long savedVersion) {
		Long currentVersion = dirtyRegionVersions.get(regionId);

		if (currentVersion != null && currentVersion == savedVersion) {
			dirtyRegionVersions.remove(regionId);
		}
	}

	public Set<String> getLoadedRegionIdsSnapshot() {
		return new HashSet<>(loadedRegionIds);
	}

	public void removeLoadedRegion(String regionId) {
		loadedRegionIds.remove(regionId);
		regionLastAccessTimes.remove(regionId);
	}

	public long getLastAccessTime(String regionId, long defaultValue) {
		return regionLastAccessTimes.getOrDefault(regionId, defaultValue);
	}

	public int getLoadedRegionCount() {
		return loadedRegionIds.size();
	}

	public int getDirtyRegionCount() {
		return dirtyRegionVersions.size();
	}
}