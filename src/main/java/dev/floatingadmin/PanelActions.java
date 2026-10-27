package dev.floatingadmin;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;

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
	private static final Pattern WARP = Pattern.compile("^[A-Za-z0-9_-]{1,20}$");
	private static final Pattern COORD = Pattern.compile("^(~|~?-?\\d{1,8}(\\.\\d{1,3})?)$");
	private static final Set<String> COLORS = Set.of("white", "aqua", "red", "gold", "green", "yellow", "light_purple", "gray", "dark_red", "blue");

	private PanelActions() {}

	/** "Operator" = allowed to use vanilla /gamemode (permission level 2+, or whatever a permission mod grants). */
	static boolean isOperator(CommandDispatcher<CommandSourceStack> dispatcher, CommandSourceStack source) {
		// Must not touch source.getServer(): it is null while the command tree is sent to a joining client.
		CommandNode<CommandSourceStack> node = dispatcher.getRoot().getChild("gamemode");
		return node != null && node.canUse(source);
	}

	static void handle(MinecraftServer server, ServerPlayer player, String data) {
		CommandSourceStack source = player.createCommandSourceStack();
		if (!isOperator(server.getCommands().getDispatcher(), source)) {
			status(player, "You are not an operator.");
			FloatingAdmin.LOGGER.warn("{} tried to use the admin panel without operator permission", name(player));
			return;
		}
		String[] parts = data.split(SEP, -1);
		String action = parts.length > 0 ? parts[0] : "";
		String target = parts.length > 1 ? parts[1] : "";
		String arg = parts.length > 2 ? parts[2] : "";

		// ---- requests: no side effects, just data for the pages ----
		switch (action) {
			case "open" -> { ServerPlayNetworking.send(player, new PanelPayload("open")); return; }
			case "req_stats" -> { send(player, "stats", Stats.snapshot(server)); return; }
			case "req_log" -> { send(player, "log", String.join("\n", Store.recentLog(60))); return; }
			case "req_mutes" -> { send(player, "mutes", String.join("\n", Store.muteLines())); return; }
			case "req_bans" -> { send(player, "bans", String.join("\n", banLines())); return; }
			case "req_warps" -> { send(player, "warps", String.join("\n", Store.warpLines())); return; }
			default -> { }
		}

		if (!validTarget(target)) {
			status(player, "Invalid target.");
			return;
		}

		// ---- custom features that are not plain vanilla commands ----
		if (handleSpecial(server, player, action, target, arg)) {
			return;
		}

		List<String> commands = build(action, target, arg);
		if (commands == null) {
			status(player, "Unknown or invalid action: " + action);
			return;
		}
		logAction(player, action, target, arg);
		for (String command : commands) {
			server.getCommands().performPrefixedCommand(source, command);
		}
		status(player, "Done: " + action.replace('_', ' ') + (target.isEmpty() ? "" : " (" + target + ")"));
		if (action.equals("pardon")) {
			send(player, "bans", String.join("\n", banLines()));
		}
	}

	/** Returns true when the action was fully handled here. */
	private static boolean handleSpecial(MinecraftServer server, ServerPlayer player, String action, String target, String arg) {
		switch (action) {
			case "mute": {
				ServerPlayer victim = find(server, target);
				if (victim == null) {
					status(player, target + " is not online.");
					return true;
				}
				int minutes = clamp(arg, 0, 525600, 0);
				long until = minutes > 0 ? System.currentTimeMillis() + minutes * 60_000L : 0;
				Store.mute(victim.getUUID(), name(victim), until);
				logAction(player, "mute", target, minutes > 0 ? minutes + " min" : "permanent");
				status(player, "Muted " + name(victim) + (minutes > 0 ? " for " + minutes + " min" : " (permanent)"));
				send(player, "mutes", String.join("\n", Store.muteLines()));
				return true;
			}
			case "unmute": {
				boolean removed = NAME.matcher(target).matches() && Store.unmuteName(target);
				logAction(player, "unmute", target, "");
				status(player, removed ? "Unmuted " + target : target + " was not muted.");
				send(player, "mutes", String.join("\n", Store.muteLines()));
				return true;
			}
			case "warp_set": {
				if (!WARP.matcher(arg).matches()) {
					status(player, "Warp names: letters, numbers, - and _ (max 20).");
					return true;
				}
				Store.setWarp(arg, new Store.Warp(player.level().dimension().identifier().toString(), player.getX(), player.getY(),
						player.getZ(), player.getYRot(), player.getXRot()));
				logAction(player, "warp set", "", arg);
				status(player, "Saved warp '" + arg + "' at your position.");
				send(player, "warps", String.join("\n", Store.warpLines()));
				return true;
			}
			case "warp_go": {
				Store.Warp w = WARP.matcher(arg).matches() ? Store.warp(arg) : null;
				if (w == null || !ID.matcher(w.dim()).matches()) {
					status(player, "No warp called '" + arg + "'.");
					return true;
				}
				String cmd = String.format(Locale.ROOT, "execute in %s run tp @s %.2f %.2f %.2f %.1f %.1f", w.dim(), w.x(), w.y(), w.z(), w.yaw(), w.pitch());
				server.getCommands().performPrefixedCommand(player.createCommandSourceStack(), cmd);
				logAction(player, "warp go", "", arg);
				status(player, "Teleported to warp '" + arg + "'.");
				return true;
			}
			case "warp_del": {
				boolean removed = WARP.matcher(arg).matches() && Store.deleteWarp(arg);
				logAction(player, "warp delete", "", arg);
				status(player, removed ? "Deleted warp '" + arg + "'." : "No warp called '" + arg + "'.");
				send(player, "warps", String.join("\n", Store.warpLines()));
				return true;
			}
			case "tp_xyz": {
				String[] c = arg.trim().split(" ");
				if (c.length != 3 || !COORD.matcher(c[0]).matches() || !COORD.matcher(c[1]).matches() || !COORD.matcher(c[2]).matches()) {
					status(player, "Enter three numbers (or ~) for X Y Z.");
					return true;
				}
				server.getCommands().performPrefixedCommand(player.createCommandSourceStack(), "tp @s " + c[0] + " " + c[1] + " " + c[2]);
				logAction(player, "tp xyz", "", arg);
				status(player, "Teleported to " + arg);
				return true;
			}
			case "announce":
			case "title":
			case "actionbar":
			case "announce_both": {
				String[] ca = arg.split(" ", 2);
				String color = COLORS.contains(ca[0]) ? ca[0] : "white";
				String text = COLORS.contains(ca[0]) ? (ca.length > 1 ? ca[1] : "") : arg;
				if (text.isBlank()) {
					status(player, "Type a message first.");
					return true;
				}
				String msg = json(text);
				List<String> cmds = new ArrayList<>();
				if (action.equals("announce") || action.equals("announce_both")) {
					cmds.add("tellraw @a [{\"text\":\"[Announcement] \",\"color\":\"" + color + "\",\"bold\":true},{\"text\":\"" + msg + "\",\"color\":\"white\"}]");
				}
				if (action.equals("title") || action.equals("announce_both")) {
					cmds.add("title @a title {\"text\":\"" + msg + "\",\"color\":\"" + color + "\"}");
				}
				if (action.equals("actionbar")) {
					cmds.add("title @a actionbar {\"text\":\"" + msg + "\",\"color\":\"" + color + "\"}");
				}
				for (String cmd : cmds) {
					server.getCommands().performPrefixedCommand(player.createCommandSourceStack(), cmd);
				}
				logAction(player, action, "@a", text);
				status(player, "Announcement sent.");
				return true;
			}
			default:
				return false;
		}
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
			case "effect_clear_one": return WORD.matcher(arg).matches() ? List.of("effect clear " + t + " " + arg) : null;
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
			case "rule": return rule(arg);
			// ---- teleport presets ----
			case "tp_top": return List.of("execute positioned over world_surface run tp @s ~ ~ ~");
			case "tp_overworld": return List.of("execute in minecraft:overworld positioned over world_surface run tp @s ~ ~ ~");
			case "tp_nether": return List.of("execute in minecraft:the_nether run tp @s ~ 80 ~");
			case "tp_end": return List.of("execute in minecraft:the_end run tp @s 100 49 0");
			// ---- fun ----
			case "cookies": return List.of("give " + t + " cookie 8");
			case "rocket": return List.of("execute at " + t + " run summon firework_rocket ~ ~1 ~");
			case "random_tp": return List.of("spreadplayers ~ ~ 10 1000 false " + t);
			case "gather": return List.of("tp @a @s");
			case "summon": return ID.matcher(arg).matches() ? List.of("execute at " + t + " run summon " + arg + " ~ ~ ~") : null;
			case "clear_title": return List.of("title @a clear");
			case "clear_chat": return List.of("tellraw @a {\"text\":\"" + "\\n".repeat(100) + "\"}");
			case "ping": return List.of("execute as @a at @s run playsound minecraft:block.note_block.pling master @s ~ ~ ~ 1 1");
			default: return null;
		}
	}

	private static List<String> rule(String arg) {
		String[] a = arg.trim().split(" ");
		if (a.length != 2 || !WORD.matcher(a[0]).matches() || !(a[1].equals("true") || a[1].equals("false"))) {
			return null;
		}
		return List.of("gamerule " + a[0] + " " + a[1]);
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

	// ───────────────────────── ban list ─────────────────────────

	/** "name TAB reason" lines from the server's own banned-players.json. */
	private static List<String> banLines() {
		List<String> out = new ArrayList<>();
		Path file = FabricLoader.getInstance().getGameDir().resolve("banned-players.json");
		try {
			if (!Files.exists(file)) {
				return out;
			}
			JsonArray arr = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonArray();
			for (JsonElement el : arr) {
				JsonObject o = el.getAsJsonObject();
				String n = o.has("name") ? o.get("name").getAsString() : "?";
				String reason = o.has("reason") ? o.get("reason").getAsString() : "";
				out.add(n + "\t" + reason.replace('\n', ' ').replace('\t', ' '));
				if (out.size() >= 80) {
					break;
				}
			}
		} catch (Exception e) {
			FloatingAdmin.LOGGER.warn("Could not read banned-players.json: {}", e.getMessage());
		}
		return out;
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

	private static void logAction(ServerPlayer player, String action, String target, String arg) {
		String detail = arg.length() > 60 ? arg.substring(0, 60) + "..." : arg;
		Store.log(name(player), action.replace('_', ' ') + (target.isEmpty() ? "" : " -> " + target) + (detail.isEmpty() ? "" : " (" + detail + ")"));
		FloatingAdmin.LOGGER.info("{} -> {} [{}] {}", name(player), action, target, detail);
	}

	private static void send(ServerPlayer player, String kind, String body) {
		ServerPlayNetworking.send(player, new PanelPayload(kind + SEP + body));
	}

	private static void status(ServerPlayer player, String text) {
		send(player, "status", text);
	}
}
