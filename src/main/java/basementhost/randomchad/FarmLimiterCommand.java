package basementhost.randomchad;

import basementhost.randomchad.fish.FishManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Chunk;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class FarmLimiterCommand implements CommandExecutor, TabCompleter {

	private final FarmLimiterPlugin plugin;
	private final FishManager fishManager;

	public FarmLimiterCommand(FarmLimiterPlugin plugin, FishManager fishManager) {
		this.plugin = plugin;
		this.fishManager = fishManager;
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

		plugin.reloadConfig();

		fishManager.load();

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

		int remainingFish = fishManager.getRemainingFish(chunk);
		int maxFish = fishManager.getMaxFish();
		int regenAmount = fishManager.getRegenAmount();
		int regenIntervalSeconds = fishManager.getRegenIntervalSeconds();

		player.sendMessage(Component.text("Current chunk fishing resource:"));
		player.sendMessage(Component.text("World: " + chunk.getWorld().getName()));
		player.sendMessage(Component.text("Chunk X: " + chunk.getX() + ", Z: " + chunk.getZ()));
		player.sendMessage(Component.text("Fish: " + remainingFish + " / " + maxFish));
		player.sendMessage(Component.text("Regen: +" + regenAmount + " every " + regenIntervalSeconds + " seconds"));
	}

	private void handleSave(CommandSender sender) {
		if (!sender.hasPermission("farmlimiter.admin")) {
			sender.sendMessage(Component.text("You do not have permission to use this command."));
			return;
		}

		fishManager.save();

		sender.sendMessage(Component.text("FarmLimiter data saved."));
	}

	private void sendHelp(CommandSender sender) {
		sender.sendMessage(Component.text("FarmLimiter commands:"));
		sender.sendMessage(Component.text("/farmlimiter help - Show help"));
		sender.sendMessage(Component.text("/farmlimiter fish - Show current chunk fish amount"));
		sender.sendMessage(Component.text("/farmlimiter save - Save plugin data"));
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

			if (sender.hasPermission("farmlimiter.admin")) {
				suggestions.add("save");
				suggestions.add("reload");
			}

			String input = args[0].toLowerCase();

			return suggestions.stream()
					.filter(s -> s.startsWith(input))
					.toList();
		}

		return new ArrayList<>();
	}
}