package dev.floatingadmin;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

/** Measures tick time ourselves from Fabric's tick events and builds the numbers the Stats page shows. */
final class Stats {
	private static long tickStart = 0;
	private static long lastStart = 0;
	private static double intervalMs = 50.0;
	private static double msptMs = 0.0;

	private Stats() {}

	static void register() {
		ServerTickEvents.START_SERVER_TICK.register(server -> {
			long now = System.nanoTime();
			if (lastStart != 0) {
				double interval = (now - lastStart) / 1_000_000.0;
				intervalMs = intervalMs * 0.97 + Math.min(interval, 2000.0) * 0.03;
			}
			lastStart = now;
			tickStart = now;
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			double duration = (System.nanoTime() - tickStart) / 1_000_000.0;
			msptMs = msptMs * 0.97 + duration * 0.03;
		});
	}

	/** tps | mspt | usedMB | maxMB | players | maxPlayers | entities | per-world "name=count" list */
	static String snapshot(MinecraftServer server) {
		double tps = Math.min(20.0, 1000.0 / Math.max(1.0, intervalMs));
		Runtime rt = Runtime.getRuntime();
		long used = (rt.totalMemory() - rt.freeMemory()) / 1048576L;
		long max = rt.maxMemory() / 1048576L;
		int total = 0;
		StringBuilder worlds = new StringBuilder();
		for (ServerLevel level : server.getAllLevels()) {
			int count = 0;
			for (Entity ignored : level.getAllEntities()) {
				count++;
			}
			total += count;
			if (worlds.length() > 0) {
				worlds.append(',');
			}
			worlds.append(level.dimension().identifier().getPath()).append('=').append(count);
		}
		return String.format(java.util.Locale.ROOT, "%.1f|%.1f|%d|%d|%d|%d|%d|%s", tps, msptMs, used, max,
				server.getPlayerList().getPlayers().size(), server.getMaxPlayers(), total, worlds);
	}
}
