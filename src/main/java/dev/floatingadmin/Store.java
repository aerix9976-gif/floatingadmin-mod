package dev.floatingadmin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.fabricmc.loader.api.FabricLoader;

/** Everything that must survive a restart: mutes, warps and the action log. Plain JSON files in config/aeros-admin/. */
final class Store {
	record Mute(String name, long until) {}

	record Warp(String dim, double x, double y, double z, float yaw, float pitch) {}

	private static final Path DIR = FabricLoader.getInstance().getConfigDir().resolve("aeros-admin");
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss");

	private static final Map<UUID, Mute> MUTES = new LinkedHashMap<>();
	private static final Map<String, Warp> WARPS = new LinkedHashMap<>();
	private static final Deque<String> LOG = new ArrayDeque<>();

	private Store() {}

	// ───────────────────────── load / save ─────────────────────────

	static synchronized void load() {
		try {
			Files.createDirectories(DIR);
		} catch (IOException e) {
			FloatingAdmin.LOGGER.warn("Could not create {}: {}", DIR, e.getMessage());
		}
		JsonObject mutes = read("mutes.json");
		for (Map.Entry<String, JsonElement> e : mutes.entrySet()) {
			try {
				JsonObject o = e.getValue().getAsJsonObject();
				MUTES.put(UUID.fromString(e.getKey()), new Mute(o.get("name").getAsString(), o.get("until").getAsLong()));
			} catch (RuntimeException ignored) {
				// skip a corrupt entry
			}
		}
		JsonObject warps = read("warps.json");
		for (Map.Entry<String, JsonElement> e : warps.entrySet()) {
			try {
				JsonObject o = e.getValue().getAsJsonObject();
				WARPS.put(e.getKey(), new Warp(o.get("dim").getAsString(), o.get("x").getAsDouble(), o.get("y").getAsDouble(),
						o.get("z").getAsDouble(), o.get("yaw").getAsFloat(), o.get("pitch").getAsFloat()));
			} catch (RuntimeException ignored) {
				// skip a corrupt entry
			}
		}
		Path log = DIR.resolve("actions.log");
		try {
			if (Files.exists(log)) {
				List<String> lines = Files.readAllLines(log, StandardCharsets.UTF_8);
				for (int i = Math.max(0, lines.size() - 300); i < lines.size(); i++) {
					LOG.addLast(lines.get(i));
				}
			}
		} catch (IOException e) {
			FloatingAdmin.LOGGER.warn("Could not read actions.log: {}", e.getMessage());
		}
	}

	private static JsonObject read(String file) {
		Path p = DIR.resolve(file);
		try {
			if (Files.exists(p)) {
				return JsonParser.parseString(Files.readString(p, StandardCharsets.UTF_8)).getAsJsonObject();
			}
		} catch (IOException | RuntimeException e) {
			FloatingAdmin.LOGGER.warn("Could not read {}: {}", file, e.getMessage());
		}
		return new JsonObject();
	}

	private static void write(String file, JsonObject json) {
		try {
			Files.createDirectories(DIR);
			Files.writeString(DIR.resolve(file), json.toString(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			FloatingAdmin.LOGGER.warn("Could not save {}: {}", file, e.getMessage());
		}
	}

	// ───────────────────────── mutes ─────────────────────────

	static synchronized void mute(UUID id, String name, long until) {
		MUTES.put(id, new Mute(name, until));
		saveMutes();
	}

	/** Removes a mute by player name (works for offline players). Returns true if one existed. */
	static synchronized boolean unmuteName(String name) {
		boolean removed = MUTES.values().removeIf(m -> m.name().equalsIgnoreCase(name));
		if (removed) {
			saveMutes();
		}
		return removed;
	}

	static synchronized boolean unmute(UUID id) {
		boolean removed = MUTES.remove(id) != null;
		if (removed) {
			saveMutes();
		}
		return removed;
	}

	/** Milliseconds left (Long.MAX_VALUE = permanent), 0 if not muted. */
	static synchronized long muteLeft(UUID id) {
		Mute m = MUTES.get(id);
		if (m == null) {
			return 0;
		}
		if (m.until() == 0) {
			return Long.MAX_VALUE;
		}
		long left = m.until() - System.currentTimeMillis();
		if (left <= 0) {
			MUTES.remove(id);
			saveMutes();
			return 0;
		}
		return left;
	}

	static synchronized List<String> muteLines() {
		List<String> out = new ArrayList<>();
		long now = System.currentTimeMillis();
		for (Mute m : MUTES.values()) {
			String info = m.until() == 0 ? "permanent" : Util.duration(Math.max(0, m.until() - now)) + " left";
			out.add(m.name() + "\t" + info);
		}
		return out;
	}

	private static void saveMutes() {
		JsonObject root = new JsonObject();
		for (Map.Entry<UUID, Mute> e : MUTES.entrySet()) {
			JsonObject o = new JsonObject();
			o.addProperty("name", e.getValue().name());
			o.addProperty("until", e.getValue().until());
			root.add(e.getKey().toString(), o);
		}
		write("mutes.json", root);
	}

	// ───────────────────────── warps ─────────────────────────

	static synchronized void setWarp(String name, Warp w) {
		WARPS.put(name, w);
		saveWarps();
	}

	static synchronized boolean deleteWarp(String name) {
		boolean removed = WARPS.remove(name) != null;
		if (removed) {
			saveWarps();
		}
		return removed;
	}

	static synchronized Warp warp(String name) {
		return WARPS.get(name);
	}

	static synchronized List<String> warpLines() {
		List<String> out = new ArrayList<>();
		for (Map.Entry<String, Warp> e : WARPS.entrySet()) {
			Warp w = e.getValue();
			String dim = w.dim().startsWith("minecraft:") ? w.dim().substring(10) : w.dim();
			out.add(e.getKey() + "\t" + dim + "  " + Math.round(w.x()) + " " + Math.round(w.y()) + " " + Math.round(w.z()));
		}
		return out;
	}

	private static void saveWarps() {
		JsonObject root = new JsonObject();
		for (Map.Entry<String, Warp> e : WARPS.entrySet()) {
			Warp w = e.getValue();
			JsonObject o = new JsonObject();
			o.addProperty("dim", w.dim());
			o.addProperty("x", w.x());
			o.addProperty("y", w.y());
			o.addProperty("z", w.z());
			o.addProperty("yaw", w.yaw());
			o.addProperty("pitch", w.pitch());
			root.add(e.getKey(), o);
		}
		write("warps.json", root);
	}

	// ───────────────────────── action log ─────────────────────────

	static synchronized void log(String who, String text) {
		String line = LocalDateTime.now().format(TIME) + "  " + who + ": " + text;
		LOG.addLast(line);
		while (LOG.size() > 300) {
			LOG.removeFirst();
		}
		try {
			Files.createDirectories(DIR);
			Files.writeString(DIR.resolve("actions.log"), line + System.lineSeparator(), StandardCharsets.UTF_8,
					StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		} catch (IOException e) {
			FloatingAdmin.LOGGER.warn("Could not write actions.log: {}", e.getMessage());
		}
	}

	/** Newest first, at most {@code max} lines. */
	static synchronized List<String> recentLog(int max) {
		List<String> out = new ArrayList<>();
		java.util.Iterator<String> it = LOG.descendingIterator();
		while (it.hasNext() && out.size() < max) {
			out.add(it.next());
		}
		return out;
	}
}
