package basementhost.randomchad.chunkmobunload;

public class ChunkMobUnloadRule {
	private final int softLimit;
	private final int hardLimit;

	public ChunkMobUnloadRule(int softLimit, int hardLimit) {
		this.softLimit = softLimit;
		this.hardLimit = hardLimit;
	}

	public int getSoftLimit() {
		return softLimit;
	}

	public int getHardLimit() {
		return hardLimit;
	}
}