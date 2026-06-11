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
import java.util.Map;

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
				sender.sendMessage(lang("command.unknown-command", Map.of(
						"label", label
				)));
				return true;
		}
	}

	private void handleReload(CommandSender sender) {
		if (!sender.hasPermission("farmlimiter.admin")) {
			sender.sendMessage(lang("command.no-permission"));
			return;
		}

		fishManager.save();
		naturalSpawnManager.save();

		plugin.reloadConfig();
		plugin.getLangManager().load();

		fishManager.load();
		naturalSpawnManager.load();

		sender.sendMessage(lang("command.reload-success"));
	}

	private void handleFish(CommandSender sender) {
		if (!(sender instanceof Player player)) {
			sender.sendMessage(lang("command.player-only"));
			return;
		}

		if (!sender.hasPermission("farmlimiter.use")) {
			sender.sendMessage(lang("command.no-permission"));
			return;
		}

		Chunk chunk = player.getLocation().getChunk();

		int remainingFish = fishManager.peekRemainingFish(chunk);
		int maxFish = fishManager.getMaxFish();
		int regenAmount = fishManager.getRegenAmount();
		int regenIntervalSeconds = fishManager.getRegenIntervalSeconds();

		player.sendMessage(lang("fish.header"));
		player.sendMessage(lang("fish.world", Map.of(
				"world", chunk.getWorld().getName()
		)));
		player.sendMessage(lang("fish.chunk", Map.of(
				"x", chunk.getX(),
				"z", chunk.getZ()
		)));
		player.sendMessage(lang("fish.amount", Map.of(
				"current", remainingFish,
				"max", maxFish
		)));
		player.sendMessage(lang("fish.regen", Map.of(
				"amount", regenAmount,
				"seconds", regenIntervalSeconds
		)));
	}

	private void handleNatural(CommandSender sender, String[] args) {
		if (!(sender instanceof Player player)) {
			sender.sendMessage(lang("command.player-only"));
			return;
		}

		if (!sender.hasPermission("farmlimiter.use")) {
			sender.sendMessage(lang("command.no-permission"));
			return;
		}

		Chunk chunk = player.getLocation().getChunk();

		int remainingTotal = naturalSpawnManager.peekRemainingTotalResource(chunk);
		int maxTotal = naturalSpawnManager.getTotalMaxResource();
		int regenAmount = naturalSpawnManager.getTotalRegenAmount();
		int regenIntervalSeconds = naturalSpawnManager.getTotalRegenIntervalSeconds();

		player.sendMessage(lang("natural.header"));
		player.sendMessage(lang("natural.world", Map.of(
				"world", chunk.getWorld().getName()
		)));
		player.sendMessage(lang("natural.chunk", Map.of(
				"x", chunk.getX(),
				"z", chunk.getZ()
		)));
		player.sendMessage(lang("natural.total-resource", Map.of(
				"current", remainingTotal,
				"max", maxTotal
		)));
		player.sendMessage(lang("natural.total-regen", Map.of(
				"amount", regenAmount,
				"seconds", regenIntervalSeconds
		)));

		if (args.length >= 2) {
			String entityName = args[1].toUpperCase();

			EntityType entityType;

			try {
				entityType = EntityType.valueOf(entityName);
			} catch (IllegalArgumentException exception) {
				player.sendMessage(lang("natural.unknown-entity", Map.of(
						"entity", entityName
				)));
				return;
			}

			int entityResource = naturalSpawnManager.peekRemainingEntityResource(chunk, entityType);

			if (entityResource < 0) {
				player.sendMessage(lang("natural.no-entity-limit", Map.of(
						"entity", entityName
				)));
				return;
			}

			int entityMax = naturalSpawnManager.getEntityMaxResource(entityName);
			int entityRegenAmount = naturalSpawnManager.getEntityRegenAmount(entityName);
			int entityRegenIntervalSeconds = naturalSpawnManager.getEntityRegenIntervalSeconds(entityName);

			player.sendMessage(lang("natural.entity-resource", Map.of(
					"entity", entityName,
					"current", entityResource,
					"max", entityMax
			)));
			player.sendMessage(lang("natural.entity-regen", Map.of(
					"entity", entityName,
					"amount", entityRegenAmount,
					"seconds", entityRegenIntervalSeconds
			)));
		}
	}

	private void handleDebug(CommandSender sender, String[] args) {
		if (!sender.hasPermission("farmlimiter.admin")) {
			sender.sendMessage(lang("command.no-permission"));
			return;
		}

		if (args.length < 2 || args[1].equalsIgnoreCase("status")) {
			boolean enabled = naturalSpawnManager.isDebugEnabled();
			boolean logAll = naturalSpawnManager.shouldLogAllCreatureSpawns();

			sender.sendMessage(lang("debug.header"));
			sender.sendMessage(lang("debug.enabled", Map.of(
					"value", enabled
			)));
			sender.sendMessage(lang("debug.log-all", Map.of(
					"value", logAll
			)));
			sender.sendMessage(lang("debug.usage-short"));
			return;
		}

		if (args[1].equalsIgnoreCase("on")) {
			naturalSpawnManager.setDebug(true, true);

			sender.sendMessage(lang("debug.enabled-message"));
			sender.sendMessage(lang("debug.spam-warning"));
			return;
		}

		if (args[1].equalsIgnoreCase("off")) {
			naturalSpawnManager.setDebug(false, false);

			sender.sendMessage(lang("debug.disabled-message"));
			return;
		}

		sender.sendMessage(lang("debug.usage"));
	}

	private void handleStats(CommandSender sender) {
		if (!sender.hasPermission("farmlimiter.admin")) {
			sender.sendMessage(lang("command.no-permission"));
			return;
		}

		int fishTrackedChunks = fishManager.getTrackedChunkCount();
		int naturalTrackedChunks = naturalSpawnManager.getTrackedChunkCount();

		sender.sendMessage(lang("stats.header"));
		sender.sendMessage(lang("stats.fish-tracked-chunks", Map.of(
				"value", fishTrackedChunks
		)));
		sender.sendMessage(lang("stats.fish-loaded-regions", Map.of(
				"loaded", fishManager.getLoadedRegionCount(),
				"max", fishManager.getMaxLoadedRegions()
		)));
		sender.sendMessage(lang("stats.fish-dirty-regions", Map.of(
				"value", fishManager.getDirtyRegionCount()
		)));
		sender.sendMessage(lang("stats.fish-async-running", Map.of(
				"value", fishManager.isAsyncSaveRunning()
		)));
		sender.sendMessage(lang("stats.fish-async-queued", Map.of(
				"value", fishManager.isAsyncSaveQueued()
		)));
		sender.sendMessage(lang("stats.natural-tracked-chunks", Map.of(
				"value", naturalTrackedChunks
		)));
		sender.sendMessage(lang("stats.natural-loaded-regions", Map.of(
				"loaded", naturalSpawnManager.getLoadedRegionCount(),
				"max", naturalSpawnManager.getMaxLoadedRegions()
		)));
		sender.sendMessage(lang("stats.natural-dirty-regions", Map.of(
				"value", naturalSpawnManager.getDirtyRegionCount()
		)));
		sender.sendMessage(lang("stats.natural-async-running", Map.of(
				"value", naturalSpawnManager.isAsyncSaveRunning()
		)));
		sender.sendMessage(lang("stats.natural-async-queued", Map.of(
				"value", naturalSpawnManager.isAsyncSaveQueued()
		)));
	}

	private void handleCleanup(CommandSender sender) {
		if (!sender.hasPermission("farmlimiter.admin")) {
			sender.sendMessage(lang("command.no-permission"));
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

		sender.sendMessage(lang("cleanup.header"));
		sender.sendMessage(lang("cleanup.fish-result", Map.of(
				"before", fishBefore,
				"after", fishAfter,
				"removed", fishRemoved
		)));
		sender.sendMessage(lang("cleanup.natural-result", Map.of(
				"before", naturalBefore,
				"after", naturalAfter,
				"removed", naturalRemoved
		)));
		sender.sendMessage(lang("command.cleanup-started-save"));
	}

	private void handleSave(CommandSender sender) {
		if (!sender.hasPermission("farmlimiter.admin")) {
			sender.sendMessage(lang("command.no-permission"));
			return;
		}

		fishManager.saveAsync();
		naturalSpawnManager.saveAsync();

		sender.sendMessage(lang("command.save-started"));
	}

	private void sendHelp(CommandSender sender) {
		sender.sendMessage(lang("help.header"));
		sender.sendMessage(lang("help.help"));
		sender.sendMessage(lang("help.fish"));
		sender.sendMessage(lang("help.natural"));
		sender.sendMessage(lang("help.natural-entity"));
		sender.sendMessage(lang("help.debug"));
		sender.sendMessage(lang("help.stats"));
		sender.sendMessage(lang("help.cleanup"));
		sender.sendMessage(lang("help.save"));
		sender.sendMessage(lang("help.reload"));
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

	private Component lang(String path) {
		return Component.text(plugin.getLangManager().get(path));
	}

	private Component lang(String path, Map<String, Object> placeholders) {
		return Component.text(plugin.getLangManager().get(path, placeholders));
	}
}