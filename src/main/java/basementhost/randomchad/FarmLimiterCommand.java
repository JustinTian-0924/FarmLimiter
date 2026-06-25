package basementhost.randomchad;

import basementhost.randomchad.breeding.BreedingLimitManager;
import basementhost.randomchad.chunkloaderlimit.ChunkLoaderLimitManager;
import basementhost.randomchad.chunkloaderlimit.ChunkLoaderLimitStatus;
import basementhost.randomchad.chunkmobunload.ChunkMobUnloadManager;
import basementhost.randomchad.chunkmobunload.ChunkMobUnloadRule;
import basementhost.randomchad.chunkmobunload.ChunkMobUnloadUtil;
import basementhost.randomchad.fish.FishManager;
import basementhost.randomchad.natural.NaturalSpawnManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.*;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class FarmLimiterCommand implements CommandExecutor, TabCompleter {

	private final FarmLimiterPlugin plugin;
	private final FishManager fishManager;
	private final NaturalSpawnManager naturalSpawnManager;
	private final BreedingLimitManager breedingLimitManager;
	private final ChunkMobUnloadManager chunkMobUnloadManager;
	private final ChunkMobUnloadUtil chunkMobUnloadUtil = new ChunkMobUnloadUtil();
	private final ChunkLoaderLimitManager chunkLoaderLimitManager;

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
		this.chunkMobUnloadManager = plugin.getChunkMobUnloadManager();
		this.chunkLoaderLimitManager = plugin.getChunkLoaderLimitManager();
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

			case "chunkmob":
				handleChunkMob(sender, args);
				return true;

			case "chunkloader":
				handleChunkLoader(sender, args);
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
		plugin.getChunkMobUnloadManager().load();
		plugin.getChunkLoaderLimitManager().load();

		plugin.restartChunkMobUnloadTask();
		plugin.restartChunkLoaderLimitTask();

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
		if (args.length >= 2 && args[1].equalsIgnoreCase("reload")) {
			handleBreedingReload(sender);
			return;
		}
		if (args.length >= 2 && args[1].equalsIgnoreCase("debug")) {
			handleBreedingDebug(sender);
			return;
		}
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
			String breedingArgument = args[1];

			if (breedingArgument.equalsIgnoreCase("all")) {
				boolean foundAny = false;

				for (EntityType entityType : breedingLimitManager.getSupportedEntityTypes()) {
					int current = breedingLimitManager.countSameTypeInChunk(chunk, entityType);

					if (current > 0) {
						sendBreedingEntityStatus(player, chunk, entityType);
						foundAny = true;
					}
				}

				if (!foundAny) {
					player.sendMessage(lang("breeding.no-entities-in-chunk"));
				}

				return;
			}

			if (isReservedBreedingSubCommand(breedingArgument)) {
				player.sendMessage(lang("breeding.unknown-sub-command", Map.of(
						"subcommand", breedingArgument
				)));
				return;
			}

			String entityName = breedingArgument.toUpperCase();
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

		List<EntityType> configuredEntityTypes = breedingLimitManager.getConfiguredEntityTypes();
		if (configuredEntityTypes.isEmpty()) {
			player.sendMessage(lang("breeding.no-configured-entities"));
			return;
		}
		for (EntityType entityType : configuredEntityTypes) {
			sendBreedingEntityStatus(player, chunk, entityType);
		}

	}

	private boolean isReservedBreedingSubCommand(String argument) {
		String lowerArgument = argument.toLowerCase();
		return lowerArgument.equals("reload")
				|| lowerArgument.equals("debug")
				|| lowerArgument.equals("save")
				|| lowerArgument.equals("stats")
				|| lowerArgument.equals("help");
	}

	private void handleBreedingReload(CommandSender sender) {
		if (!sender.hasPermission("farmlimiter.admin")) {
			sender.sendMessage(lang("command.no-permission"));
			return;
		}
		breedingLimitManager.load();
		sender.sendMessage(lang("breeding.reload-success"));
	}

	private void handleBreedingDebug(CommandSender sender) {
		if (!sender.hasPermission("farmlimiter.admin")) {
			sender.sendMessage(lang("command.no-permission"));
			return;
		}

		sender.sendMessage(lang("breeding.debug-header"));
		sender.sendMessage(lang("breeding.debug-enabled", Map.of(
				"value", breedingLimitManager.isEnabled()
		)));
		sender.sendMessage(lang("breeding.debug-default-limit", Map.of(
				"value", breedingLimitManager.getDefaultLimit()
		)));
		sender.sendMessage(lang("breeding.debug-count-adults-only", Map.of(
				"value", breedingLimitManager.shouldCountAdultsOnly()
		)));
		sender.sendMessage(lang("breeding.debug-ignore-named-entities", Map.of(
				"value", breedingLimitManager.shouldIgnoreNamedEntities()
		)));
		sender.sendMessage(lang("breeding.debug-notify-cooldown", Map.of(
				"value", breedingLimitManager.getNotifyCooldownSeconds()
		)));
		sender.sendMessage(lang("breeding.debug-actionbar", Map.of(
				"value", breedingLimitManager.isActionbarNotifyEnabled()
		)));
		sender.sendMessage(lang("breeding.debug-chat", Map.of(
				"value", breedingLimitManager.isChatNotifyEnabled()
		)));
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
		sender.sendMessage(lang("stats.breeding-count-adults-only", Map.of(
				"value", breedingLimitManager.shouldCountAdultsOnly()
		)));
		sender.sendMessage(lang("stats.breeding-ignore-named-entities", Map.of(
				"value", breedingLimitManager.shouldIgnoreNamedEntities()
		)));
		sender.sendMessage(lang("stats.breeding-notify-cooldown", Map.of(
				"value", breedingLimitManager.getNotifyCooldownSeconds()
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
		sender.sendMessage(lang("help.chunkmob"));
		sender.sendMessage(lang("help.chunkloader"));
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
			suggestions.add("chunkmob");
			suggestions.add("chunkloader");

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
			if (sender.hasPermission("farmlimiter.admin")) {
				suggestions.add("reload");
				suggestions.add("debug");
			}
			suggestions.add("all");
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

		if (args.length == 2 && args[0].equalsIgnoreCase("chunkmob")) {
			List<String> suggestions = new ArrayList<>();
			if (sender.hasPermission("farmlimiter.admin")) {
				suggestions.add("debug");
			}
			suggestions.add("all");
			suggestions.add("farm_animal");
			suggestions.add("hostile");
			suggestions.add("ZOMBIE");
			suggestions.add("SKELETON");
			suggestions.add("CREEPER");
			suggestions.add("COW");
			suggestions.add("SHEEP");
			suggestions.add("VILLAGER");
			String input = args[1].toLowerCase(Locale.ROOT);
			return suggestions.stream()
					.filter(s -> s.toLowerCase(Locale.ROOT).startsWith(input))
					.toList();
		}

		if (args.length == 2 && args[0].equalsIgnoreCase("chunkloader")) {
			List<String> suggestions = new ArrayList<>();
			if (sender.hasPermission("farmlimiter.admin")) {
				suggestions.add("debug");
				suggestions.add("status");
			}
			String input = args[1].toLowerCase(Locale.ROOT);
			return suggestions.stream()
					.filter(suggestion -> suggestion.toLowerCase(Locale.ROOT).startsWith(input))
					.toList();

		}

		if (args.length == 3
				&& args[0].equalsIgnoreCase("chunkloader")
				&& args[1].equalsIgnoreCase("status")) {
			List<String> suggestions = new ArrayList<>();
			if (sender.hasPermission("farmlimiter.admin")) {
				suggestions.add("nearby");
				suggestions.add("world");
			}
			String input = args[2].toLowerCase(Locale.ROOT);
			return suggestions.stream()
					.filter(suggestion -> suggestion.toLowerCase(Locale.ROOT).startsWith(input))
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

	private void handleChunkMob(CommandSender sender, String[] args) {
		if (args.length >= 2 && args[1].equalsIgnoreCase("debug")) {
			handleChunkMobDebug(sender);
			return;
		}
		if (!(sender instanceof Player player)) {
			sender.sendMessage(lang("command.player-only"));
			return;
		}

		if (!sender.hasPermission("farmlimiter.use")) {
			sender.sendMessage(lang("command.no-permission"));
			return;
		}

		Chunk chunk = player.getLocation().getChunk();

		player.sendMessage(lang("chunkmob.header"));
		player.sendMessage(lang("chunkmob.world", Map.of(
				"world", chunk.getWorld().getName()
		)));
		player.sendMessage(lang("chunkmob.chunk", Map.of(
				"x", chunk.getX(),
				"z", chunk.getZ()
		)));

		if (args.length >= 2) {
			handleChunkMobTarget(player, chunk, args[1]);
			return;
		}

		sendChunkMobTotalStatus(player, chunk);
	}

	private void handleChunkMobTarget(Player player, Chunk chunk, String target) {
		if (target.equalsIgnoreCase("all")) {
			sendChunkMobTotalStatus(player, chunk);
			sendChunkMobGroupStatuses(player, chunk);
			sendChunkMobEntityStatuses(player, chunk);
			return;
		}

		if (chunkMobUnloadManager.hasGroup(target)) {
			sendChunkMobGroupStatus(player, chunk, target);
			return;
		}

		EntityType entityType;

		try {
			entityType = EntityType.valueOf(target.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException exception) {
			player.sendMessage(lang("chunkmob.unknown-target", Map.of(
					"target", target
			)));
			return;
		}

		ChunkMobUnloadRule rule = chunkMobUnloadManager.getEntityRule(entityType);

		if (rule == null) {
			player.sendMessage(lang("chunkmob.no-entity-rule", Map.of(
					"entity", entityType.name()
			)));
			return;
		}

		sendChunkMobEntityStatus(player, chunk, entityType, rule);
	}

	private void sendChunkMobTotalStatus(Player player, Chunk chunk) {
		ChunkMobUnloadRule rule = chunkMobUnloadManager.getTotalRule();
		int current = chunkMobUnloadUtil.countLivingEntities(chunk);

		player.sendMessage(lang("chunkmob.total-status", Map.of(
				"current", current,
				"soft", rule.getSoftLimit(),
				"hard", rule.getHardLimit()
		)));
	}

	private void sendChunkMobGroupStatuses(Player player, Chunk chunk) {
		for (Map.Entry<String, ChunkMobUnloadRule> entry : chunkMobUnloadManager.getGroupRules().entrySet()) {
			sendChunkMobGroupStatus(player, chunk, entry.getKey());
		}
	}

	private void sendChunkMobGroupStatus(Player player, Chunk chunk, String groupName) {
		ChunkMobUnloadRule rule = chunkMobUnloadManager.getGroupRule(groupName);
		Set<EntityType> entityTypes = chunkMobUnloadManager.getGroupEntities(groupName);

		if (rule == null) {
			return;
		}

		int current = chunkMobUnloadUtil.countGroup(chunk, entityTypes);

		player.sendMessage(lang("chunkmob.group-status", Map.of(
				"group", groupName,
				"current", current,
				"soft", rule.getSoftLimit(),
				"hard", rule.getHardLimit()
		)));
	}

	private void sendChunkMobEntityStatuses(Player player, Chunk chunk) {
		for (Map.Entry<EntityType, ChunkMobUnloadRule> entry : chunkMobUnloadManager.getEntityRules().entrySet()) {
			sendChunkMobEntityStatus(player, chunk, entry.getKey(), entry.getValue());
		}
	}

	private void sendChunkMobEntityStatus(Player player, Chunk chunk, EntityType entityType, ChunkMobUnloadRule rule) {
		int current = chunkMobUnloadUtil.countEntityType(chunk, entityType);

		player.sendMessage(lang("chunkmob.entity-status", Map.of(
				"entity", entityType.name(),
				"current", current,
				"soft", rule.getSoftLimit(),
				"hard", rule.getHardLimit()
		)));
	}

	private void handleChunkMobDebug(CommandSender sender) {
		if (!sender.hasPermission("farmlimiter.admin")) {
			sender.sendMessage(lang("command.no-permission"));
			return;
		}
		sender.sendMessage(lang("chunkmob.debug-header"));
		sender.sendMessage(lang("chunkmob.debug-enabled", Map.of(
				"value", chunkMobUnloadManager.isEnabled()
		)));
		sender.sendMessage(lang("chunkmob.debug-interval", Map.of(
				"value", chunkMobUnloadManager.getCheckIntervalSeconds()
		)));
		sender.sendMessage(lang("chunkmob.debug-remove-mode", Map.of(
				"value", chunkMobUnloadManager.getRemoveMode().name()
		)));
		sender.sendMessage(lang("chunkmob.debug-remove-value", Map.of(
				"value", chunkMobUnloadManager.getRemoveValue()
		)));
		sender.sendMessage(lang("chunkmob.debug-sort-mode", Map.of(
				"value", chunkMobUnloadManager.getSortMode().name()
		)));
		sender.sendMessage(lang("chunkmob.debug-warning", Map.of(
				"value", chunkMobUnloadManager.isWarningEnabled()
		)));
		sender.sendMessage(lang("chunkmob.debug-warning-radius", Map.of(
				"value", chunkMobUnloadManager.getWarningRadiusBlocks()
		)));
		sender.sendMessage(lang("chunkmob.debug-cleanup-result", Map.of(
				"value", chunkMobUnloadManager.shouldNotifyCleanupResult()
		)));
		sender.sendMessage(lang("chunkmob.debug-cleanup-result-radius", Map.of(
				"value", chunkMobUnloadManager.getCleanupResultRadiusBlocks()
		)));
	}

	private void handleChunkLoader(CommandSender sender, String[] args) {
		if (args.length >= 2 && args[1].equalsIgnoreCase("debug")) {
			handleChunkLoaderDebug(sender);
			return;
		}

		if (args.length >= 2 && args[1].equalsIgnoreCase("status")) {
			handleChunkLoaderStatus(sender, args);
			return;
		}

		sender.sendMessage(lang("chunkloader.usage"));
	}

	private void handleChunkLoaderStatus(CommandSender sender, String[] args) {
		if (!sender.hasPermission("farmlimiter.admin")) {
			sender.sendMessage(lang("command.no-permission"));
			return;
		}

		if (args.length >= 3 && args[2].equalsIgnoreCase("nearby")) {
			if (!(sender instanceof Player player)) {
				sender.sendMessage(lang("command.player-only"));
				return;
			}

			ChunkLoaderLimitStatus status = collectChunkLoaderStatusNearby(player);
			sendChunkLoaderStatus(sender, "nearby", status);
			return;
		}

		if (args.length >= 3 && args[2].equalsIgnoreCase("world")) {
			if (!(sender instanceof Player player)) {
				sender.sendMessage(lang("command.player-only"));
				return;
			}

			ChunkLoaderLimitStatus status = collectChunkLoaderStatusInWorld(player.getWorld());
			sendChunkLoaderStatus(sender, player.getWorld().getName(), status);
			return;
		}

		ChunkLoaderLimitStatus status = collectChunkLoaderStatusAll();
		sendChunkLoaderStatus(sender, "all", status);
	}

	private ChunkLoaderLimitStatus collectChunkLoaderStatusAll() {
		ChunkLoaderLimitStatus status = new ChunkLoaderLimitStatus();

		for (World world : plugin.getServer().getWorlds()) {
			collectChunkLoaderStatusFromWorld(world, status);
		}

		return status;
	}

	private ChunkLoaderLimitStatus collectChunkLoaderStatusInWorld(World world) {
		ChunkLoaderLimitStatus status = new ChunkLoaderLimitStatus();
		collectChunkLoaderStatusFromWorld(world, status);
		return status;
	}

	private ChunkLoaderLimitStatus collectChunkLoaderStatusNearby(Player player) {
		ChunkLoaderLimitStatus status = new ChunkLoaderLimitStatus();
		int radius = chunkLoaderLimitManager.getStatusNearbyRadiusBlocks();
		double radiusSquared = radius * radius;

		for (Entity entity : player.getWorld().getEntities()) {
			if (entity.getLocation().distanceSquared(player.getLocation()) > radiusSquared) {
				continue;
			}

			collectChunkLoaderEntityStatus(entity, status);
		}

		return status;
	}

	private void collectChunkLoaderStatusFromWorld(World world, ChunkLoaderLimitStatus status) {
		for (Entity entity : world.getEntities()) {
			collectChunkLoaderEntityStatus(entity, status);
		}
	}

	private void collectChunkLoaderEntityStatus(Entity entity, ChunkLoaderLimitStatus status) {
		if (entity instanceof EnderPearl) {
			if (entity.getPersistentDataContainer().has(
					chunkLoaderLimitManager.getEnderPearlCreatedAtKey(),
					PersistentDataType.LONG
			)) {
				status.addTrackedEnderPearl();
			}

			return;
		}

		if (!entity.getPersistentDataContainer().has(
				chunkLoaderLimitManager.getPortalTeleportCountKey(),
				PersistentDataType.INTEGER
		)) {
			return;
		}

		if (entity instanceof Minecart) {
			status.addTrackedMinecart();
			return;
		}

		if (entity instanceof Boat) {
			status.addTrackedBoat();
			return;
		}

		if (entity instanceof Item) {
			status.addTrackedItem();
			return;
		}

		if (entity instanceof LivingEntity && !(entity instanceof Player)) {
			status.addTrackedLivingEntity();
		}
	}

	private void sendChunkLoaderStatus(
			CommandSender sender,
			String scope,
			ChunkLoaderLimitStatus status
	) {
		sender.sendMessage(lang("chunkloader.status-header", Map.of(
				"scope", scope
		)));
		sender.sendMessage(lang("chunkloader.status-ender-pearls", Map.of(
				"value", status.getTrackedEnderPearls()
		)));
		sender.sendMessage(lang("chunkloader.status-minecarts", Map.of(
				"value", status.getTrackedMinecarts()
		)));
		sender.sendMessage(lang("chunkloader.status-boats", Map.of(
				"value", status.getTrackedBoats()
		)));
		sender.sendMessage(lang("chunkloader.status-items", Map.of(
				"value", status.getTrackedItems()
		)));
		sender.sendMessage(lang("chunkloader.status-living-entities", Map.of(
				"value", status.getTrackedLivingEntities()
		)));
	}

	private void handleChunkLoaderDebug(CommandSender sender) {
		if (!sender.hasPermission("farmlimiter.admin")) {
			sender.sendMessage(lang("command.no-permission"));
			return;
		}

		sender.sendMessage(lang("chunkloader.debug-header"));
		sender.sendMessage(lang("chunkloader.debug-enabled", Map.of(
				"value", chunkLoaderLimitManager.isEnabled()
		)));
		sender.sendMessage(lang("chunkloader.debug-ender-pearl-enabled", Map.of(
				"value", chunkLoaderLimitManager.isEnderPearlEnabled()
		)));
		sender.sendMessage(lang("chunkloader.debug-ender-pearl-lifetime", Map.of(
				"value", chunkLoaderLimitManager.getEnderPearlMaxLifetimeSeconds()
		)));
		sender.sendMessage(lang("chunkloader.debug-portal-entity-enabled", Map.of(
				"value", chunkLoaderLimitManager.isPortalEntityEnabled()
		)));
		sender.sendMessage(lang("chunkloader.debug-minecart-enabled", Map.of(
				"value", chunkLoaderLimitManager.isMinecartPortalLimitEnabled()
		)));
		sender.sendMessage(lang("chunkloader.debug-minecart-max", Map.of(
				"value", chunkLoaderLimitManager.getMinecartMaxPortalTeleports()
		)));
		sender.sendMessage(lang("chunkloader.debug-boat-enabled", Map.of(
				"value", chunkLoaderLimitManager.isBoatPortalLimitEnabled()
		)));
		sender.sendMessage(lang("chunkloader.debug-boat-max", Map.of(
				"value", chunkLoaderLimitManager.getBoatMaxPortalTeleports()
		)));
		sender.sendMessage(lang("chunkloader.debug-item-enabled", Map.of(
				"value", chunkLoaderLimitManager.isItemPortalLimitEnabled()
		)));
		sender.sendMessage(lang("chunkloader.debug-item-max", Map.of(
				"value", chunkLoaderLimitManager.getItemMaxPortalTeleports()
		)));
		sender.sendMessage(lang("chunkloader.debug-living-enabled", Map.of(
				"value", chunkLoaderLimitManager.isLivingEntityPortalLimitEnabled()
		)));
		sender.sendMessage(lang("chunkloader.debug-living-max", Map.of(
				"value", chunkLoaderLimitManager.getLivingEntityMaxPortalTeleports()
		)));
		sender.sendMessage(lang("chunkloader.debug-check-interval", Map.of(
				"value", chunkLoaderLimitManager.getCheckIntervalSeconds()
		)));
		sender.sendMessage(lang("chunkloader.debug-world-filter-mode", Map.of(
				"value", chunkLoaderLimitManager.getWorldFilterMode().name()
		)));
		sender.sendMessage(lang("chunkloader.debug-world-filter-worlds", Map.of(
				"value", chunkLoaderLimitManager.getFilteredWorlds().toString()
		)));
		sender.sendMessage(lang("chunkloader.debug-ignored-entity-types", Map.of(
				"value", chunkLoaderLimitManager.getIgnoredEntityTypes().toString()
		)));
		sender.sendMessage(lang("chunkloader.debug-admin-notify", Map.of(
				"value", chunkLoaderLimitManager.shouldNotifyAdminOnPortalLimit()
		)));
		sender.sendMessage(lang("chunkloader.debug-admin-notify-radius", Map.of(
				"value", chunkLoaderLimitManager.getAdminNotifyRadiusBlocks()
		)));
	}
}