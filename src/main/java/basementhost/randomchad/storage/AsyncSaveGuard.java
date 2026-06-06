package basementhost.randomchad.storage;

import java.util.concurrent.atomic.AtomicBoolean;

public class AsyncSaveGuard {

	private final AtomicBoolean running = new AtomicBoolean(false);
	private final AtomicBoolean queued = new AtomicBoolean(false);

	public boolean tryStart() {
		if (!running.compareAndSet(false, true)) {
			queued.set(true);
			return false;
		}

		return true;
	}

	public void finish() {
		running.set(false);
	}

	public boolean consumeQueued() {
		return queued.getAndSet(false);
	}

	public boolean isRunning() {
		return running.get();
	}

	public boolean isQueued() {
		return queued.get();
	}

	public void clear() {
		running.set(false);
		queued.set(false);
	}
}