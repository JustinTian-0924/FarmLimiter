package basementhost.randomchad.fish;

import basementhost.randomchad.FarmLimiterPlugin;
import net.kyori.adventure.text.Component;
import org.bukkit.Chunk;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;

public class FishListener implements Listener {

	private final FarmLimiterPlugin plugin;
	private final FishManager fishManager;

	public FishListener(FarmLimiterPlugin plugin, FishManager fishManager) {
		this.plugin = plugin;
		this.fishManager = fishManager;
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onPlayerFish(PlayerFishEvent event) {
		if (!fishManager.isEnabled()) {
			return;
		}

		if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) {
			return;
		}

		Player player = event.getPlayer();
		Chunk fishingChunk = event.getHook().getLocation().getChunk();

		boolean success = fishManager.tryConsumeFish(fishingChunk);

		if (success) {
			return;
		}

		event.setCancelled(true);
		event.setExpToDrop(0);

		Entity caught = event.getCaught();

		if (caught != null) {
			caught.remove();
		}

		if (fishManager.shouldNotifyPlayer()) {
			player.sendActionBar(Component.text(plugin.getLangManager().get("fish.actionbar-depleted")));
		}
	}
}