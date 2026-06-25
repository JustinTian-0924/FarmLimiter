package basementhost.randomchad.chunkloaderlimit;

public class ChunkLoaderLimitStatus {
	private int trackedEnderPearls;
	private int trackedMinecarts;
	private int trackedBoats;
	private int trackedItems;
	private int trackedLivingEntities;

	public void addTrackedEnderPearl() {
		trackedEnderPearls++;
	}

	public void addTrackedMinecart() {
		trackedMinecarts++;
	}

	public void addTrackedBoat() {
		trackedBoats++;
	}

	public void addTrackedItem() {
		trackedItems++;
	}

	public void addTrackedLivingEntity() {
		trackedLivingEntities++;
	}

	public int getTrackedEnderPearls() {
		return trackedEnderPearls;
	}

	public int getTrackedMinecarts() {
		return trackedMinecarts;
	}

	public int getTrackedBoats() {
		return trackedBoats;
	}

	public int getTrackedItems() {
		return trackedItems;
	}

	public int getTrackedLivingEntities() {
		return trackedLivingEntities;
	}
}