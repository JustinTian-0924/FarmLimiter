package basementhost.randomchad;

import basementhost.randomchad.fish.FishManager;
import basementhost.randomchad.natural.NaturalSpawnManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Chunk;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class FarmLimiterCommand implements CommandExecutor, TabCompleter {

	private final FarmLimiterPlugin plugin;
	private final FishManager fishManager;
	private final NaturalSpawnManager naturalSpawnManager;

	public FarmLimiterCommand(
			FarmLimiterPlugin plugin,
			FishManager fishManager,
			NaturalSpawnManager naturalSpawnManager
	) {
		this.plugin = plugin;
		this.fishManager = fishManager;
		this.naturalSpawnManager = naturalSpawnManager;
	}

	@Override
	public boolean onCommand(
			@NotNull CommandSender sender,
			@NotNull Command command,
			@NotNull String label,
			@NotNull String[] args
	) {
		if (args.length == 0) {
			sendHelp(sender);
			return true;
		}

		String subCommand = args[0].toLowerCase();

		switch (subCommand) {
			case "reload":
				handleReload(sender);
				return true;

			case "fish":
				handleFish(sender);
				return true;

			case "natural":
				handleNatural(sender, args);
				return true;

			case "debug":
				handleDebug(sender, args);
				return true;

			case "stats":
				handleStats(sender);
				return true;

			case "cleanup":
				handleCleanup(sender);
				return true;

			case "save":
				handleSave(sender);
				return true;

			case "help":
				sendHelp(sender);
				return true;

			default:
				sender.sendMessage(Component.text("Unknown command. Use /" + label + " help"));
				return true;
		}
	}

	private void handleReload(CommandSender sender) {
		if (!sender.hasPermission("farmlimiter.admin")) {
			sender.sendMessage(Component.text("You do not have permission to use this command."));
			return;
		}

		fishManager.save();
		naturalSpawnManager.save();

		plugin.reloadConfig();

		fishManager.load();
		naturalSpawnManager.load();

		sender.sendMessage(Component.text("FarmLimiter config and data reloaded."));
	}

	private void handleFish(CommandSender sender) {
		if (!(sender instanceof Player player)) {
			sender.sendMessage(Component.text("This command can only be used by a player."));
			return;
		}

		if (!sender.hasPermission("farmlimiter.use")) {
			sender.sendMessage(Component.text("You do not have permission to use this command."));
			return;
		}

		Chunk chunk = player.getLocation().getChunk();

		int remainingFish = fishManager.peekRemainingFish(chunk);
		int maxFish = fishManager.getMaxFish();
		int regenAmount = fishManager.getRegenAmount();
		int regenIntervalSeconds = fishManager.getRegenIntervalSeconds();

		player.sendMessage(Component.text("Current chunk fishing resource:"));
		player.sendMessage(Component.text("World: " + chunk.getWorld().getName()));
		player.sendMessage(Component.text("Chunk X: " + chunk.getX() + ", Z: " + chunk.getZ()));
		player.sendMessage(Component.text("Fish: " + remainingFish + " / " + maxFish));
		player.sendMessage(Component.text("Regen: +" + regenAmount + " every " + regenIntervalSeconds + " seconds"));
	}

	private void handleNatural(CommandSender sender, String[] args) {
		if (!(sender instanceof Player player)) {
			sender.sendMessage(Component.text("This command can only be used by a player."));
			return;
		}

		if (!sender.hasPermission("farmlimiter.use")) {
			sender.sendMessage(Component.text("You do not have permission to use this command."));
			return;
		}

		Chunk chunk = player.getLocation().getChunk();

		int remainingTotal = naturalSpawnManager.peekRemainingTotalResource(chunk);
		int maxTotal = naturalSpawnManager.getTotalMaxResource();
		int regenAmount = naturalSpawnManager.getTotalRegenAmount();
		int regenIntervalSeconds = naturalSpawnManager.getTotalRegenIntervalSeconds();

		player.sendMessage(Component.text("Current chunk natural spawn resource:"));
		player.sendMessage(Component.text("World: " + chunk.getWorld().getName()));
		player.sendMessage(Component.text("Chunk X: " + chunk.getX() + ", Z: " + chunk.getZ()));
		player.sendMessage(Component.text("Total Resource: " + remainingTotal + " / " + maxTotal));
		player.sendMessage(Component.text("Total Regen: +" + regenAmount + " every " + regenIntervalSeconds + " seconds"));

		if (args.length >= 2) {
			String entityName = args[1].toUpperCase();

			EntityType entityType;

			try {
				entityType = EntityType.valueOf(entityName);
			} catch (IllegalArgumentException exception) {
				player.sendMessage(Component.text("Unknown entity type: " + entityName));
				return;
			}

			int entityResource = naturalSpawnManager.peekRemainingEntityResource(chunk, entityType);

			if (entityResource < 0) {
				player.sendMessage(Component.text(entityName + " does not have a separate limit in modules/natural-spawn-rate-limit.yml."));
				return;
			}

			int entityMax = naturalSpawnManager.getEntityMaxResource(entityName);
			int entityRegenAmount = naturalSpawnManager.getEntityRegenAmount(entityName);
			int entityRegenIntervalSeconds = naturalSpawnManager.getEntityRegenIntervalSeconds(entityName);

			player.sendMessage(Component.text(entityName + " Resource: " + entityResource + " / " + entityMax));
			player.sendMessage(Component.text(entityName + " Regen: +" + entityRegenAmount + " every " + entityRegenIntervalSeconds + " seconds"));
		}
	}

	private void handleDebug(CommandSender sender, String[] args) {
		if (!sender.hasPermission("farmlimiter.admin")) {
			sender.sendMessage(Component.text("You do not have permission to use this command."));
			return;
		}

		if (args.length < 2 || args[1].equalsIgnoreCase("status")) {
			boolean enabled = naturalSpawnManager.isDebugEnabled();
			boolean logAll = naturalSpawnManager.shouldLogAllCreatureSpawns();

			sender.sendMessage(Component.text("FarmLimiter natural spawn debug status:"));
			sender.sendMessage(Component.text("Debug enabled: " + enabled));
			sender.sendMessage(Component.text("Log all creature spawns: " + logAll));
			sender.sendMessage(Component.text("Use /fl debug on or /fl debug off"));
			return;
		}

		if (args[1].equalsIgnoreCase("on")) {
			naturalSpawnManager.setDebug(true, true);

			sender.sendMessage(Component.text("Natural spawn debug enabled."));
			sender.sendMessage(Component.text("Warning: this may spam console when many mobs spawn."));
			return;
		}

		if (args[1].equalsIgnoreCase("off")) {
			naturalSpawnManager.setDebug(false, false);

			sender.sendMessage(Component.text("Natural spawn debug disabled."));
			return;
		}

		sender.sendMessage(Component.text("Usage: /fl debug <on|off|status>"));
	}

	private void handleStats(CommandSender sender) {
		if (!sender.hasPermission("farmlimiter.admin")) {
			sender.sendMessage(Component.text("You do not have permission to use this command."));
			return;
		}

		int fishTrackedChunks = fishManager.getTrackedChunkCount();
		int naturalTrackedChunks = naturalSpawnManager.getTrackedChunkCount();

		sender.sendMessage(Component.text("FarmLimiter stats:"));
		sender.sendMessage(Component.text("Fish_Depletion tracked chunks: " + fishTrackedChunks));
		sender.sendMessage(Component.text("Natural_Spawn_Rate_Limit tracked chunks: " + naturalTrackedChunks));
		sender.sendMessage(Component.text("Natural_Spawn_Rate_Limit loaded regions: " + naturalSpawnManager.getLoadedRegionCount() + " / " + naturalSpawnManager.getMaxLoadedRegions()));
		sender.sendMessage(Component.text("Natural_Spawn_Rate_Limit dirty regions: " + naturalSpawnManager.getDirtyRegionCount()));

		sender.sendMessage(Component.text("Fish_Depletion async save running: " + fishManager.isAsyncSaveRunning()));
		sender.sendMessage(Component.text("Fish_Depletion async save queued: " + fishManager.isAsyncSaveQueued()));

		sender.sendMessage(Component.text("Natural_Spawn_Rate_Limit async save running: " + naturalSpawnManager.isAsyncSaveRunning()));
		sender.sendMessage(Component.text("Natural_Spawn_Rate_Limit async save queued: " + naturalSpawnManager.isAsyncSaveQueued()));
	}

	private void handleCleanup(CommandSender sender) {
		if (!sender.hasPermission("farmlimiter.admin")) {
			sender.sendMessage(Component.text("You do not have permission to use this command."));
			return;
		}

		int fishBefore = fishManager.getTrackedChunkCount();
		int naturalBefore = naturalSpawnManager.getTrackedChunkCount();

		int fishRemoved = fishManager.cleanupAndGetRemovedCount();
		int naturalRemoved = naturalSpawnManager.cleanupAndGetRemovedCount();

		int fishAfter = fishManager.getTrackedChunkCount();
		int naturalAfter = naturalSpawnManager.getTrackedChunkCount();

		fishManager.saveAsync();
		naturalSpawnManager.saveAsync();

		sender.sendMessage(Component.text("FarmLimiter cleanup completed:"));
		sender.sendMessage(Component.text("Fish_Depletion: " + fishBefore + " -> " + fishAfter + " removed " + fishRemoved));
		sender.sendMessage(Component.text("Natural_Spawn_Rate_Limit: " + naturalBefore + " -> " + naturalAfter + " removed " + naturalRemoved));
		sender.sendMessage(Component.text("Data save has been started asynchronously."));
	}

	private void handleSave(CommandSender sender) {
		if (!sender.hasPermission("farmlimiter.admin")) {
			sender.sendMessage(Component.text("You do not have permission to use this command."));
			return;
		}

		fishManager.saveAsync();
		naturalSpawnManager.saveAsync();

		sender.sendMessage(Component.text("FarmLimiter async data save started."));
	}

	private void sendHelp(CommandSender sender) {
		sender.sendMessage(Component.text("FarmLimiter commands:"));
		sender.sendMessage(Component.text("/farmlimiter help - Show help"));
		sender.sendMessage(Component.text("/farmlimiter fish - Show current chunk fish amount"));
		sender.sendMessage(Component.text("/farmlimiter natural - Show current chunk natural spawn resource"));
		sender.sendMessage(Component.text("/farmlimiter natural <entity> - Show current chunk entity natural spawn resource"));
		sender.sendMessage(Component.text("/farmlimiter debug <on|off|status> - Toggle natural spawn debug"));
		sender.sendMessage(Component.text("/farmlimiter stats - Show tracked chunk stats"));
		sender.sendMessage(Component.text("/farmlimiter cleanup - Manually cleanup unused data"));
		sender.sendMessage(Component.text("/farmlimiter save - Save plugin data asynchronously"));
		sender.sendMessage(Component.text("/farmlimiter reload - Reload config and data"));
	}

	@Override
	public @Nullable List<String> onTabComplete(
			@NotNull CommandSender sender,
			@NotNull Command command,
			@NotNull String label,
			@NotNull String[] args
	) {
		if (args.length == 1) {
			List<String> suggestions = new ArrayList<>();

			suggestions.add("help");
			suggestions.add("fish");
			suggestions.add("natural");
			suggestions.add("debug");
			suggestions.add("stats");
			suggestions.add("cleanup");

			if (sender.hasPermission("farmlimiter.admin")) {
				suggestions.add("save");
				suggestions.add("reload");
			}

			String input = args[0].toLowerCase();

			return suggestions.stream()
					.filter(s -> s.startsWith(input))
					.toList();
		}

		if (args.length == 2 && args[0].equalsIgnoreCase("natural")) {
			List<String> suggestions = new ArrayList<>();

			suggestions.add("ZOMBIE");
			suggestions.add("SKELETON");
			suggestions.add("CREEPER");
			suggestions.add("SPIDER");
			suggestions.add("ENDERMAN");
			suggestions.add("WITCH");
			suggestions.add("SLIME");
			suggestions.add("SILVERFISH");
			suggestions.add("IRON_GOLEM");
			suggestions.add("PILLAGER");
			suggestions.add("VINDICATOR");
			suggestions.add("EVOKER");
			suggestions.add("RAVAGER");
			suggestions.add("GHAST");
			suggestions.add("WITHER_SKELETON");
			suggestions.add("WARDEN");
			suggestions.add("HOGLIN");
			suggestions.add("ZOMBIFIED_PIGLIN");
			suggestions.add("GUARDIAN");

			String input = args[1].toUpperCase();

			return suggestions.stream()
					.filter(s -> s.startsWith(input))
					.toList();
		}

		if (args.length == 2 && args[0].equalsIgnoreCase("debug")) {
			List<String> suggestions = new ArrayList<>();

			suggestions.add("on");
			suggestions.add("off");
			suggestions.add("status");

			String input = args[1].toLowerCase();

			return suggestions.stream()
					.filter(s -> s.startsWith(input))
					.toList();
		}

		return new ArrayList<>();
	}
}
