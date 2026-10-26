package dev.floatingadmin;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import com.mojang.brigadier.tree.CommandNode;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/**
 * Server-side brain. The client only sends an action id; the server checks that the sender is an
 * operator and builds the commands itself, so nothing the client sends is ever run as raw text.
 * Commands run as the player, so vanilla permission levels still apply (e.g. /stop needs level 4).
 */
final class PanelActions {
	private static final String SEP = "\u001f";
	private static final Pattern NAME = Pattern.compile("^[A-Za-z0-9_]{1,16}$");
	private static final Pattern ID = Pattern.compile("^[a-z0-9_.-]+(:[a-z0-9_./-]+)?$");
	private static final Pattern WORD = Pattern.compile("^[a-z_]{3,40}$");

	private PanelActions() {}

	/** "Operator" = allowed to use vanilla /gamemode (permission level 2+, or whatever a permission mod grants). */
	static boolean isOperator(MinecraftServer server, CommandSourceStack source) {
		CommandNode<CommandSourceStack> node = server.getCommands().getDispatcher().getRoot().getChild("gamemode");
		return node != null && node.canUse(source);
	}

	static void handle(MinecraftServer server, ServerPlayer player, String data) {
		CommandSourceStack source = player.createCommandSourceStack();
		if (!isOperator(server, source)) {
			status(player, "You are not an operator.");
			FloatingAdmin.LOGGER.warn("{} tried to use the admin panel without operator permission", name(player));
			return;
		}
		String[] parts = data.split(SEP, -1);
		String action = parts.length > 0 ? parts[0] : "";
		String target = parts.length > 1 ? parts[1] : "";
		String arg = parts.length > 2 ? parts[2] : "";

		if (action.equals("open")) {
			ServerPlayNetworking.send(player, new PanelPayload("open"));
			return;
		}
		if (!validTarget(target)) {
			status(player, "Invalid target.");
			return;
		}

		// custom server-side features (not vanilla commands)
		if (action.equals("mute") || action.equals("unmute")) {
			ServerPlayer victim = find(server, target);
			if (victim == null) {
				status(player, target + " is not online.");
				return;
			}
			if (action.equals("mute")) {
				FloatingAdmin.MUTED.add(victim.getUUID());
			} else {
				FloatingAdmin.MUTED.remove(victim.getUUID());
			}
			FloatingAdmin.LOGGER.info("{} -> {} {}", name(player), action, target);
			status(player, (action.equals("mute") ? "Muted " : "Unmuted ") + name(victim));
			return;
		}

		List<String> commands = build(action, target, arg);
		if (commands == null) {
			status(player, "Unknown or invalid action: " + action);
			return;
		}
		FloatingAdmin.LOGGER.info("{} -> {} [{}] {}", name(player), action, target, arg);
		for (String command : commands) {
			server.getCommands().performPrefixedCommand(source, command);
		}
		status(player, "Done: " + action.replace('_', ' ') + (target.isEmpty() ? "" : " (" + target + ")"));
	}

	// ───────────────────────── action table ─────────────────────────

	private static List<String> build(String action, String t, String arg) {
		boolean named = NAME.matcher(t).matches();
		switch (action) {
			// ---- players & self ----
			case "heal": return List.of("effect give " + t + " instant_health 1 10 true", "effect give " + t + " saturation 1 20 true");
			case "feed": return List.of("effect give " + t + " saturation 1 20 true");
			case "kill": return List.of("kill " + t);
			case "gm_survival": return List.of("gamemode survival " + t);
			case "gm_creative": return List.of("gamemode creative " + t);
			case "gm_adventure": return List.of("gamemode adventure " + t);
			case "gm_spectator": return List.of("gamemode spectator " + t);
			case "god_on": return List.of("effect give " + t + " resistance infinite 4 true", "effect give " + t + " regeneration infinite 4 true");
			case "god_off": return List.of("effect clear " + t + " resistance", "effect clear " + t + " regeneration");
			case "vanish_on": return List.of("effect give " + t + " invisibility infinite 0 true");
			case "vanish_off": return List.of("effect clear " + t + " invisibility");
			case "freeze": return List.of("effect give " + t + " slowness infinite 255 true", "effect give " + t + " jump_boost infinite 128 true");
			case "unfreeze": return List.of("effect clear " + t + " slowness", "effect clear " + t + " jump_boost");
			case "clear_inv": return List.of("clear " + t);
			case "clear_fx": return List.of("effect clear " + t);
			case "smite": return List.of("execute at " + t + " run summon lightning_bolt ~ ~ ~");
			case "launch": return List.of("effect give " + t + " levitation 2 40 true", "effect give " + t + " slow_falling 25 0 true");
			case "levels": return List.of("xp add " + t + " " + clamp(arg, -100000, 100000, 10) + " levels");
			case "effect": return effect(t, arg);
			case "give": return give(t, arg);
			case "enchant": return enchant(t, arg);
			// ---- named-player only ----
			case "tp_to": return named ? List.of("tp @s " + t) : null;
			case "bring": return named ? List.of("tp " + t + " @s") : null;
			case "spy": return named ? List.of("gamemode spectator @s", "spectate " + t + " @s") : null;
			case "kick": return named ? List.of("kick " + t + " " + reason(arg, "Kicked by an operator")) : null;
			case "ban": return named ? List.of("ban " + t + " " + reason(arg, "Banned by an operator")) : null;
			case "pardon": return named ? List.of("pardon " + t) : null;
			case "op": return named ? List.of("op " + t) : null;
			case "deop": return named ? List.of("deop " + t) : null;
			case "wl_add": return named ? List.of("whitelist add " + t) : null;
			case "wl_remove": return named ? List.of("whitelist remove " + t) : null;
			// ---- world ----
			case "weather_clear": return List.of("weather clear");
			case "weather_rain": return List.of("weather rain");
			case "weather_thunder": return List.of("weather thunder");
			case "time_day": return List.of("time set day");
			case "time_noon": return List.of("time set noon");
			case "time_night": return List.of("time set night");
			case "time_midnight": return List.of("time set midnight");
			case "time_sunrise": return List.of("time set 23000");
			case "diff_peaceful": return List.of("difficulty peaceful");
			case "diff_easy": return List.of("difficulty easy");
			case "diff_normal": return List.of("difficulty normal");
			case "diff_hard": return List.of("difficulty hard");
			case "set_spawn": return List.of("setworldspawn");
			case "kill_items": return List.of("kill @e[type=item]");
			case "kill_xp": return List.of("kill @e[type=experience_orb]");
			case "kill_hostiles": return List.of("kill @e[type=zombie]", "kill @e[type=skeleton]", "kill @e[type=creeper]",
					"kill @e[type=spider]", "kill @e[type=enderman]", "kill @e[type=witch]", "kill @e[type=phantom]",
					"kill @e[type=slime]", "kill @e[type=husk]", "kill @e[type=drowned]", "kill @e[type=pillager]");
			case "save": return List.of("save-all");
			case "tick_freeze": return List.of("tick freeze");
			case "tick_run": return List.of("tick unfreeze");
			case "border": return List.of("worldborder set " + clamp(arg, 1, 59999968, 2000));
			case "wl_on": return List.of("whitelist on");
			case "wl_off": return List.of("whitelist off");
			case "stop": return List.of("stop");
			// ---- fun ----
			case "cookies": return List.of("give " + t + " cookie 8");
			case "rocket": return List.of("execute at " + t + " run summon firework_rocket ~ ~1 ~");
			case "random_tp": return List.of("spreadplayers ~ ~ 10 1000 false " + t);
			case "gather": return List.of("tp @a @s");
			case "summon": return ID.matcher(arg).matches() ? List.of("execute at " + t + " run summon " + arg + " ~ ~ ~") : null;
			case "announce": return arg.isBlank() ? null : List.of("tellraw @a [{\"text\":\"[Announcement] \",\"color\":\"aqua\"},{\"text\":\"" + json(arg) + "\",\"color\":\"white\"}]");
			case "title": return arg.isBlank() ? null : List.of("title @a title {\"text\":\"" + json(arg) + "\",\"color\":\"aqua\"}");
			case "actionbar": return arg.isBlank() ? null : List.of("title @a actionbar {\"text\":\"" + json(arg) + "\",\"color\":\"aqua\"}");
			default: return null;
		}
	}

	private static List<String> effect(String t, String arg) {
		String[] a = arg.trim().split(" ");
		if (a.length < 1 || !WORD.matcher(a[0]).matches()) {
			return null;
		}
		int level = a.length > 1 ? clamp(a[1], 0, 255, 0) : 0;
		String secs = a.length > 2 && !a[2].equals("inf") ? String.valueOf(clamp(a[2], 1, 1000000, 60)) : "infinite";
		return List.of("effect give " + t + " " + a[0] + " " + secs + " " + level + " true");
	}

	private static List<String> give(String t, String arg) {
		String[] a = arg.trim().split(" ");
		if (a.length < 1 || !ID.matcher(a[0]).matches()) {
			return null;
		}
		int amount = a.length > 1 ? clamp(a[1], 1, 2304, 1) : 1;
		return List.of("give " + t + " " + a[0] + " " + amount);
	}

	private static List<String> enchant(String t, String arg) {
		String[] a = arg.trim().split(" ");
		if (a.length < 1 || !ID.matcher(a[0]).matches()) {
			return null;
		}
		int level = a.length > 1 ? clamp(a[1], 1, 255, 1) : 1;
		return List.of("enchant " + t + " " + a[0] + " " + level);
	}

	// ───────────────────────── helpers ─────────────────────────

	private static boolean validTarget(String t) {
		return t.equals("@s") || t.equals("@a") || t.isEmpty() || NAME.matcher(t).matches();
	}

	private static int clamp(String s, int min, int max, int fallback) {
		try {
			return Math.max(min, Math.min(max, Integer.parseInt(s.trim())));
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	/** Plain-text reason for kick/ban: letters, digits and basic punctuation only. */
	private static String reason(String s, String fallback) {
		String r = s.replaceAll("[^A-Za-z0-9 .,!?'_-]", " ").trim();
		if (r.length() > 80) {
			r = r.substring(0, 80);
		}
		return r.isEmpty() ? fallback : r;
	}

	private static String json(String s) {
		String t = s.length() > 150 ? s.substring(0, 150) : s;
		StringBuilder sb = new StringBuilder();
		for (char c : t.toCharArray()) {
			if (c == '\\' || c == '"') {
				sb.append('\\').append(c);
			} else if (c >= 32) {
				sb.append(c);
			}
		}
		return sb.toString();
	}

	private static ServerPlayer find(MinecraftServer server, String name) {
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (name(p).toLowerCase(Locale.ROOT).equals(name.toLowerCase(Locale.ROOT))) {
				return p;
			}
		}
		return null;
	}

	private static String name(ServerPlayer p) {
		return p.getName().getString();
	}

	private static void status(ServerPlayer player, String text) {
		ServerPlayNetworking.send(player, new PanelPayload("status" + SEP + text));
	}
}
