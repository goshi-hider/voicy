package dev.goshi.voicy.paper.command;

import dev.goshi.voicy.paper.VoicyPaperPlugin;
import dev.goshi.voicy.paper.group.GroupManager;
import dev.goshi.voicy.paper.speaker.SpeakerManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class SpeakerCommand implements CommandExecutor, TabCompleter {
	private final VoicyPaperPlugin plugin;

	public SpeakerCommand(VoicyPaperPlugin plugin) {
		this.plugin = plugin;
	}

	private static Component red(String text) {
		return Component.text(text, NamedTextColor.RED);
	}

	private static Component green(String text) {
		return Component.text(text, NamedTextColor.GREEN);
	}

	private static Component yellow(String text) {
		return Component.text(text, NamedTextColor.YELLOW);
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (args.length == 0) {
			sender.sendMessage(yellow("Usage: /speaker <create|remove|show|list|test|group> ..."));
			return true;
		}
		switch (args[0].toLowerCase()) {
			case "create" -> create(sender, args);
			case "remove" -> remove(sender, args);
			case "show" -> show(sender, args);
			case "list" -> list(sender);
			case "test" -> test(sender);
			case "group" -> group(sender, args);
			default -> sender.sendMessage(red("Unknown subcommand: " + args[0]));
		}
		return true;
	}

	private void create(CommandSender sender, String[] args) {
		if (!(sender instanceof Player player)) {
			sender.sendMessage(red("Only a player can create a speaker (relative ~ coordinates need an origin)."));
			return;
		}
		if (args.length != 4) {
			sender.sendMessage(red("Usage: /speaker create <x> <y> <z> or /speaker create ~ ~ ~"));
			return;
		}
		Location origin = player.getLocation();
		try {
			double x = parseCoordinate(args[1], origin.getX());
			double y = parseCoordinate(args[2], origin.getY());
			double z = parseCoordinate(args[3], origin.getZ());
			World world = player.getWorld();
			int id = plugin.speakerManager().createSpeaker(world, x, y, z);
			plugin.speakerManager().syncToPlayers();
			sender.sendMessage(green("Created speaker " + id + " at " + x + ", " + y + ", " + z));
		} catch (NumberFormatException e) {
			sender.sendMessage(red("Could not parse coordinates: " + e.getMessage()));
		}
	}

	private void remove(CommandSender sender, String[] args) {
		if (args.length != 2) {
			sender.sendMessage(red("Usage: /speaker remove <speaker_id>"));
			return;
		}
		int id = parseInt(sender, args[1]);
		if (id < 0) {
			return;
		}
		if (!plugin.speakerManager().removeSpeaker(id)) {
			sender.sendMessage(red("Speaker not found: " + id));
			return;
		}
		plugin.router().forgetSpeaker(id);
		plugin.groupManager().forgetSpeaker(id);
		plugin.speakerManager().syncToPlayers();
		sender.sendMessage(green("Removed speaker " + id));
	}

	private void show(CommandSender sender, String[] args) {
		if (args.length != 2) {
			sender.sendMessage(red("Usage: /speaker show <speaker_id>"));
			return;
		}
		int id = parseInt(sender, args[1]);
		if (id < 0) {
			return;
		}
		if (!plugin.speakerManager().showSpeaker(id)) {
			sender.sendMessage(red("Speaker not found: " + id));
			return;
		}
		sender.sendMessage(green("Showing speaker " + id));
	}

	private void list(CommandSender sender) {
		var speakers = plugin.speakerManager().speakers();
		if (speakers.isEmpty()) {
			sender.sendMessage(yellow("No speakers created."));
			return;
		}
		StringBuilder builder = new StringBuilder("Speakers:");
		for (var entry : speakers.entrySet()) {
			SpeakerManager.SpeakerEntry speaker = entry.getValue();
			builder.append("\n- ").append(entry.getKey()).append(" @ ")
					.append(speaker.world()).append(' ')
					.append(speaker.x()).append(", ").append(speaker.y()).append(", ").append(speaker.z());
		}
		sender.sendMessage(Component.text(builder.toString()));
	}

	private void test(CommandSender sender) {
		if (!(sender instanceof Player player)) {
			sender.sendMessage(red("Only a player can test a speaker."));
			return;
		}
		int replayed = plugin.router().replayRecentToSpeakers(player);
		if (replayed == 0) {
			sender.sendMessage(red("No voice frames were captured in the last 5 seconds. Speak first, then run /speaker test."));
			return;
		}
		sender.sendMessage(green("Replayed " + replayed + " voice frames through nearby speakers."));
	}

	private void group(CommandSender sender, String[] args) {
		if (args.length < 2) {
			sender.sendMessage(red("Usage: /speaker group <join|leave|list> ..."));
			return;
		}
		switch (args[1].toLowerCase()) {
			case "join" -> groupJoin(sender, args);
			case "leave" -> groupLeave(sender, args);
			case "list" -> groupList(sender);
			default -> sender.sendMessage(red("Unknown subcommand: " + args[1]));
		}
	}

	private void groupJoin(CommandSender sender, String[] args) {
		if (args.length < 3) {
			sender.sendMessage(red("Usage: /speaker group join <group_name> [speaker_id]"));
			return;
		}
		String groupName = args[2];
		GroupManager.Group group = plugin.groupManager().findByName(groupName);
		if (group == null) {
			sender.sendMessage(red("No group named '" + groupName
					+ "'. Create it first from the Groups button in the Voicy menu (default key: V)."));
			return;
		}
		int requestedSpeakerId = args.length >= 4 ? parseInt(sender, args[3]) : -1;
		if (args.length >= 4 && requestedSpeakerId < 0) {
			return;
		}
		int speakerId = requestedSpeakerId > 0 ? requestedSpeakerId : nearestSpeakerId(sender);
		if (speakerId < 0 || plugin.speakerManager().lookupSpeaker(speakerId) == null) {
			sender.sendMessage(red((requestedSpeakerId > 0
					? "Speaker not found: " + requestedSpeakerId
					: "There is no speaker in this dimension. Create one with /speaker create ~ ~ ~")));
			return;
		}
		plugin.groupManager().joinSpeaker(speakerId, group.id());
		sender.sendMessage(green("Speaker " + speakerId + " is now in group '" + group.name()
				+ "' and repeats members who talk to the group."));
	}

	private void groupLeave(CommandSender sender, String[] args) {
		int requestedSpeakerId = args.length >= 3 ? parseInt(sender, args[2]) : -1;
		if (args.length >= 3 && requestedSpeakerId < 0) {
			return;
		}
		int speakerId = requestedSpeakerId > 0 ? requestedSpeakerId : nearestSpeakerId(sender);
		if (speakerId < 0 || !plugin.groupManager().leaveSpeaker(speakerId)) {
			sender.sendMessage(red((requestedSpeakerId > 0
					? "Speaker " + requestedSpeakerId + " is not in a group."
					: "The nearest speaker is not in a group.")));
			return;
		}
		sender.sendMessage(green("Speaker " + speakerId + " left its group."));
	}

	private void groupList(CommandSender sender) {
		Map<Integer, java.util.UUID> assignments = plugin.groupManager().speakerGroups();
		if (assignments.isEmpty()) {
			sender.sendMessage(yellow("No speaker is in a group."));
			return;
		}
		StringBuilder builder = new StringBuilder("Speakers in groups:");
		for (var entry : assignments.entrySet()) {
			GroupManager.Group assigned = plugin.groupManager().groupById(entry.getValue());
			builder.append("\n- speaker ").append(entry.getKey()).append(" -> ")
					.append(assigned == null ? "(group gone)" : assigned.name());
		}
		sender.sendMessage(Component.text(builder.toString()));
	}

	private int nearestSpeakerId(CommandSender sender) {
		if (!(sender instanceof Player player)) {
			return -1;
		}
		Location origin = player.getLocation();
		String worldName = player.getWorld().getName();
		int bestId = -1;
		double bestDistance = Double.MAX_VALUE;
		for (var entry : plugin.speakerManager().speakers().entrySet()) {
			SpeakerManager.SpeakerEntry speaker = entry.getValue();
			if (!speaker.world().equals(worldName)) {
				continue;
			}
			double distance = speaker.distanceTo(origin.getX(), origin.getY(), origin.getZ());
			if (distance < bestDistance) {
				bestDistance = distance;
				bestId = entry.getKey();
			}
		}
		return bestId;
	}

	private double parseCoordinate(String token, double origin) {
		String trimmed = token.trim();
		if (trimmed.isEmpty()) {
			return origin;
		}
		if (trimmed.startsWith("~")) {
			String rest = trimmed.substring(1);
			return rest.isEmpty() ? origin : origin + Double.parseDouble(rest);
		}
		return Double.parseDouble(trimmed);
	}

	private int parseInt(CommandSender sender, String token) {
		try {
			return Integer.parseInt(token);
		} catch (NumberFormatException e) {
			sender.sendMessage(red("Not a number: " + token));
			return -1;
		}
	}

	@Override
	public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
		if (args.length == 1) {
			return filter(args[0], "create", "remove", "show", "list", "test", "group");
		}
		if (args.length == 2 && args[0].equalsIgnoreCase("group")) {
			return filter(args[1], "join", "leave", "list");
		}
		if (args.length == 3 && args[0].equalsIgnoreCase("group") && args[1].equalsIgnoreCase("join")) {
			return filter(args[2], plugin.groupManager().groupNames().toArray(new String[0]));
		}
		return List.of();
	}

	private List<String> filter(String prefix, String... options) {
		String lower = prefix.toLowerCase();
		List<String> result = new ArrayList<>();
		for (String option : options) {
			if (option.toLowerCase().startsWith(lower)) {
				result.add(option);
			}
		}
		return result;
	}
}
