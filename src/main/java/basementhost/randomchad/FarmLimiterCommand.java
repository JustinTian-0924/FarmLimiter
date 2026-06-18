package basementhost.randomchad;

import basementhost.randomchad.breeding.BreedingLimitManager;
import basementhost.randomchad.fish.FishManager;
import basementhost.randomchad.natural.NaturalSpawnManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.CreatureSpawner;
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
	private final BreedingLimitManager breedingLimitManager;

	public FarmLimiterCommand(
			FarmLimiterPlugin plugin,
			FishManager fishManager,
			NaturalSpawnManager naturalSpawnManager,
			BreedingLimitManager breedingLimitManager
	) {
		this.plugin = plugin;
		this.fishManager = fishManager;
		this.naturalSpawnManager = naturalSpawnManager;
		this.breedingLimitManager = breedingLimitManager;
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

			case "spawnercheck":
				handleSpawnerCheck(sender);
				return true;

			case "spawnerapply":
				handleSpawnerApply(sender);
				return true;

			case "spawnerreset":
				handleSpawnerReset(sender);
				return true;

			case "breeding":
				handleBreeding(sender, args);
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
		plugin.getSpawnerManager().save();
		breedingLimitManager.save();

		plugin.reloadConfig();
		plugin.getLangManager().load();

		fishManager.load();
		naturalSpawnManager.load();
		plugin.getSpawnerManager().load();
		breedingLimitManager.load();

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

	private void handleBreeding(CommandSender sender, String[] args) {
		if (!(sender instanceof Player player)) {
			sender.sendMessage(lang("command.player-only"));
			return;
		}

		if (!sender.hasPermission("farmlimiter.use")) {
			sender.sendMessage(lang("command.no-permission"));
			return;
		}

		Chunk chunk = player.getLocation().getChunk();

		player.sendMessage(lang("breeding.header"));
		player.sendMessage(lang("breeding.world", Map.of(
				"world", chunk.getWorld().getName()
		)));
		player.sendMessage(lang("breeding.chunk", Map.of(
				"x", chunk.getX(),
				"z", chunk.getZ()
		)));

		if (args.length >= 2) {
			String entityName = args[1].toUpperCase();
			EntityType entityType;

			try {
				entityType = EntityType.valueOf(entityName);
			} catch (IllegalArgumentException exception) {
				player.sendMessage(lang("breeding.unknown-entity", Map.of(
						"entity", entityName
				)));
				return;
			}

			sendBreedingEntityStatus(player, chunk, entityType);
			return;
		}

		sendBreedingEntityStatus(player, chunk, EntityType.SHEEP);
		sendBreedingEntityStatus(player, chunk, EntityType.COW);
		sendBreedingEntityStatus(player, chunk, EntityType.CHICKEN);
		sendBreedingEntityStatus(player, chunk, EntityType.PIG);
		sendBreedingEntityStatus(player, chunk, EntityType.VILLAGER);
		sendBreedingEntityStatus(player, chunk, EntityType.ALLAY);
	}

	private void sendBreedingEntityStatus(Player player, Chunk chunk, EntityType entityType) {
		int current = breedingLimitManager.countSameTypeInChunk(chunk, entityType);
		int limit = breedingLimitManager.getLimit(entityType);
		player.sendMessage(lang("breeding.entity-status", Map.of(
				"entity", entityType.name(),
				"current", current,
				"limit", limit
		)));
	}

	private void handleDebug(CommandSender sender, String[] args) {
		if (!sender.hasPermission("farmlimiter.admin")) {
			sender.sendMessage(lang("command.no-permission"));
			return;
		}

		if (args.length < 2 || args[1].equalsIgnoreCase("status")) {
			boolean enabled = naturalSpawnManager.isDebugEnabled();
			boolean logAll = naturalSpawnManager.shouldLogAllCreatureSpawns();
			boolean spawnerDebugEnabled = plugin.getSpawnerManager().isDebugEnabled();

			sender.sendMessage(Component.text("Spawner debug enabled: " + spawnerDebugEnabled));
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
			plugin.getSpawnerManager().setDebug(true);
			sender.sendMessage(lang("debug.enabled-message"));
			sender.sendMessage(lang("debug.spam-warning"));
			return;
		}

		if (args[1].equalsIgnoreCase("off")) {
			naturalSpawnManager.setDebug(false, false);
			plugin.getSpawnerManager().setDebug(false);
			sender.sendMessage(lang("debug.disabled-message"));
			return;
		}

		sender.sendMessage(lang("debug.usage"));
	}

	private void handleSpawnerCheck(CommandSender sender) {
		if (!sender.hasPermission("farmlimiter.admin")) {
			sender.sendMessage(lang("command.no-permission"));
			return;
		}

		if (!(sender instanceof Player player)) {
			sender.sendMessage(lang("command.player-only"));
			return;
		}

		Block targetBlock = player.getTargetBlockExact(10);

		if (targetBlock == null) {
			player.sendMessage(lang("spawnercheck.no-target"));
			return;
		}

		BlockState blockState = targetBlock.getState();

		if (!(blockState instanceof CreatureSpawner spawner)) {
			player.sendMessage(lang("spawnercheck.not-spawner"));
			return;
		}

		Location location = spawner.getLocation();
		EntityType entityType = spawner.getSpawnedType();
		String entityTypeName = entityType.name();
		String spawnCountRange = plugin.getSpawnerManager().getSpawnCountRangeText(entityTypeName);
		int currentResource = plugin.getSpawnerManager().getRemainingResource(location, entityType);
		int initialResource = plugin.getSpawnerManager().getInitialResource(entityTypeName);
		int maxResource = plugin.getSpawnerManager().getMaxResource(entityTypeName);
		int regenAmount = plugin.getSpawnerManager().getRegenAmount(entityTypeName);
		int regenIntervalSeconds = plugin.getSpawnerManager().getRegenIntervalSeconds(entityTypeName);

		player.sendMessage(lang("spawnercheck.header"));
		player.sendMessage(lang("spawnercheck.enabled", Map.of(
				"value", plugin.getSpawnerManager().isEnabled()
		)));
		player.sendMessage(lang("spawnercheck.world", Map.of(
				"world", location.getWorld().getName()
		)));
		player.sendMessage(lang("spawnercheck.location", Map.of(
				"x", location.getBlockX(),
				"y", location.getBlockY(),
				"z", location.getBlockZ()
		)));
		player.sendMessage(lang("spawnercheck.entity", Map.of(
				"entity", entityTypeName
		)));
		player.sendMessage(lang("spawnercheck.resource", Map.of(
				"current", currentResource,
				"max", maxResource
		)));
		player.sendMessage(lang("spawnercheck.initial-resource", Map.of(
				"value", initialResource
		)));
		player.sendMessage(lang("spawnercheck.regen", Map.of(
				"amount", regenAmount,
				"seconds", regenIntervalSeconds
		)));
		player.sendMessage(lang("spawnercheck.tracked-spawners", Map.of(
				"value", plugin.getSpawnerManager().getTrackedSpawnerCount()
		)));
		player.sendMessage(lang("spawnercheck.settings-header"));
		player.sendMessage(lang("spawnercheck.required-player-range", Map.of(
				"value", spawner.getRequiredPlayerRange()
		)));
		player.sendMessage(lang("spawnercheck.spawn-range", Map.of(
				"value", spawner.getSpawnRange()
		)));
		player.sendMessage(lang("spawnercheck.spawn-count", Map.of(
				"current", spawner.getSpawnCount(),
				"configured", spawnCountRange
		)));
		player.sendMessage(lang("spawnercheck.min-delay", Map.of(
				"ticks", spawner.getMinSpawnDelay(),
				"seconds", spawner.getMinSpawnDelay() / 20
		)));
		player.sendMessage(lang("spawnercheck.max-delay", Map.of(
				"ticks", spawner.getMaxSpawnDelay(),
				"seconds", spawner.getMaxSpawnDelay() / 20
		)));
		player.sendMessage(lang("spawnercheck.current-delay", Map.of(
				"ticks", spawner.getDelay(),
				"seconds", spawner.getDelay() / 20
		)));
	}

	private void handleSpawnerReset(CommandSender sender) {
		if (!sender.hasPermission("farmlimiter.admin")) {
			sender.sendMessage(lang("command.no-permission"));
			return;
		}

		if (!(sender instanceof Player player)) {
			sender.sendMessage(lang("command.player-only"));
			return;
		}

		Block targetBlock = player.getTargetBlockExact(10);

		if (targetBlock == null) {
			player.sendMessage(lang("spawnercheck.no-target"));
			return;
		}

		BlockState blockState = targetBlock.getState();

		if (!(blockState instanceof CreatureSpawner spawner)) {
			player.sendMessage(lang("spawnercheck.not-spawner"));
			return;
		}

		Location location = spawner.getLocation();
		EntityType entityType = spawner.getSpawnedType();

		int resource = plugin.getSpawnerManager().resetSpawnerResource(location, entityType);
		int maxResource = plugin.getSpawnerManager().getMaxResource(entityType.name());

		plugin.getSpawnerManager().saveAsync();

		player.sendMessage(lang("spawnerreset.success", Map.of(
				"entity", entityType.name(),
				"current", resource,
				"max", maxResource
		)));
	}
	private void handleStats(CommandSender sender) {
		if (!sender.hasPermission("farmlimiter.admin")) {
			sender.sendMessage(lang("command.no-permission"));
			return;
		}

		int fishTrackedChunks = fishManager.getTrackedChunkCount();
		int naturalTrackedChunks = naturalSpawnManager.getTrackedChunkCount();
		int spawnerTrackedSpawners = plugin.getSpawnerManager().getTrackedSpawnerCount();

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
		sender.sendMessage(lang("stats.spawner-tracked-spawners", Map.of(
				"value", spawnerTrackedSpawners
		)));
		sender.sendMessage(lang("stats.spawner-loaded-regions", Map.of(
				"loaded", plugin.getSpawnerManager().getLoadedRegionCount(),
				"max", plugin.getSpawnerManager().getMaxLoadedRegions()
		)));
		sender.sendMessage(lang("stats.spawner-dirty-regions", Map.of(
				"value", plugin.getSpawnerManager().getDirtyRegionCount()
		)));
		sender.sendMessage(lang("stats.spawner-async-running", Map.of(
				"value", plugin.getSpawnerManager().isAsyncSaveRunning()
		)));
		sender.sendMessage(lang("stats.spawner-async-queued", Map.of(
				"value", plugin.getSpawnerManager().isAsyncSaveQueued()
		)));
		sender.sendMessage(lang("stats.breeding-enabled", Map.of(
				"value", breedingLimitManager.isEnabled()
		)));
		sender.sendMessage(lang("stats.breeding-default-limit", Map.of(
				"value", breedingLimitManager.getDefaultLimit()
		)));

	}

	private void handleCleanup(CommandSender sender) {
		if (!sender.hasPermission("farmlimiter.admin")) {
			sender.sendMessage(lang("command.no-permission"));
			return;
		}

		int fishBefore = fishManager.getTrackedChunkCount();
		int naturalBefore = naturalSpawnManager.getTrackedChunkCount();
		int spawnerBefore = plugin.getSpawnerManager().getTrackedSpawnerCount();

		int fishRemoved = fishManager.cleanupAndGetRemovedCount();
		int naturalRemoved = naturalSpawnManager.cleanupAndGetRemovedCount();
		int spawnerRemoved = plugin.getSpawnerManager().cleanupAndGetRemovedCount();
		int breedingRemoved = breedingLimitManager.cleanupAndGetRemovedCount();

		plugin.getSpawnerManager().cleanupAndGetRemovedCount();
		plugin.getSpawnerManager().saveAsync();

		int fishAfter = fishManager.getTrackedChunkCount();
		int naturalAfter = naturalSpawnManager.getTrackedChunkCount();
		int spawnerAfter = plugin.getSpawnerManager().getTrackedSpawnerCount();

		fishManager.saveAsync();
		naturalSpawnManager.saveAsync();
		plugin.getSpawnerManager().saveAsync();

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
		sender.sendMessage(lang("cleanup.spawner-result", Map.of(
				"before", spawnerBefore,
				"after", spawnerAfter,
				"removed", spawnerRemoved
		)));
		sender.sendMessage(lang("cleanup.breeding-result", Map.of(
				"removed", breedingRemoved
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
		breedingLimitManager.saveAsync();
		plugin.getSpawnerManager().saveAsync();
		sender.sendMessage(lang("command.save-started"));
	}

	private void sendHelp(CommandSender sender) {
		sender.sendMessage(lang("help.header"));
		sender.sendMessage(lang("help.help"));
		sender.sendMessage(lang("help.fish"));
		sender.sendMessage(lang("help.natural"));
		sender.sendMessage(lang("help.natural-entity"));
		sender.sendMessage(lang("help.spawnercheck"));
		sender.sendMessage(lang("help.spawnerreset"));
		sender.sendMessage(lang("help.spawnerapply"));
		sender.sendMessage(lang("help.breeding"));
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
			suggestions.add("spawnercheck");
			suggestions.add("spawnerreset");
			suggestions.add("spawnerapply");
			suggestions.add("breeding");

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

		if (args.length == 2 && args[0].equalsIgnoreCase("breeding")) {
			List<String> suggestions = new ArrayList<>();

			suggestions.add("SHEEP");
			suggestions.add("COW");
			suggestions.add("CHICKEN");
			suggestions.add("PIG");
			suggestions.add("VILLAGER");
			suggestions.add("ALLAY");
			suggestions.add("BEE");
			suggestions.add("HORSE");
			suggestions.add("DONKEY");
			suggestions.add("LLAMA");
			suggestions.add("GOAT");
			suggestions.add("WOLF");
			suggestions.add("CAT");
			suggestions.add("RABBIT");
			suggestions.add("FOX");
			suggestions.add("TURTLE");
			suggestions.add("AXOLOTL");
			suggestions.add("FROG");
			suggestions.add("CAMEL");
			suggestions.add("SNIFFER");

			String input = args[1].toUpperCase();

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

	private void handleSpawnerApply(CommandSender sender) {
		if (!sender.hasPermission("farmlimiter.admin")) {
			sender.sendMessage(lang("command.no-permission"));
			return;
		}
		int appliedCount = plugin.getSpawnerManager().applyLoadedSpawnerSettings();
		sender.sendMessage(lang("spawnerapply.success", Map.of(
				"count", appliedCount
		)));
	}
}