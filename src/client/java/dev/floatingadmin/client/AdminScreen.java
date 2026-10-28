package dev.floatingadmin.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Aero's Admin Panel. One floating window: drag the title bar to move it, scroll the mouse wheel over a list to
 * change pages, Esc hides it and K brings it back exactly where it was. Drawn entirely with the crimson sprites.
 */
final class AdminScreen extends Screen {
	private static final Identifier PANEL = Identifier.fromNamespaceAndPath(FloatingAdminClient.MOD_ID, "panel");
	private static final int GAP = 4;

	private static final String[] PAGE_NAMES = {"Dashboard", "Self", "Players", "Items", "Forge (auto-enchant)", "Inventory Viewer", "Kits", "World",
			"Announce", "Fun", "Teleport & Warps", "Effects", "Game Rules", "Bans & Mutes", "Action Log"};
	private static final String[] PAGE_ICONS = {"tab_stats", "tab_self", "tab_players", "tab_items", "", "tab_inventory", "tab_kits", "tab_world",
			"tab_announce", "tab_fun", "tab_teleport", "tab_effects", "tab_rules", "tab_bans", "tab_log"};

	private static final String[] FX_IDS = {"speed", "slowness", "haste", "mining_fatigue", "strength", "instant_health", "instant_damage",
			"jump_boost", "nausea", "regeneration", "resistance", "fire_resistance", "water_breathing", "invisibility", "blindness",
			"night_vision", "hunger", "weakness", "poison", "wither", "health_boost", "absorption", "saturation", "glowing",
			"levitation", "luck", "unluck", "slow_falling", "conduit_power", "dolphins_grace", "darkness", "hero_of_the_village"};
	private static final String[] FX_NAMES = {"Speed", "Slowness", "Haste", "Mining Fat", "Strength", "Instant HP", "Instant Dmg",
			"Jump Boost", "Nausea", "Regen", "Resistance", "Fire Res", "Water Br", "Invisible", "Blindness",
			"Night Vis", "Hunger", "Weakness", "Poison", "Wither", "HP Boost", "Absorption", "Saturation", "Glowing",
			"Levitation", "Luck", "Bad Luck", "Slow Fall", "Conduit", "Dolphin", "Darkness", "Hero"};
	private static final String[] DUR_NAMES = {"15s", "30s", "1m", "5m", "10m", "30m", "1h", "Forever"};
	private static final int[] DUR_SECS = {15, 30, 60, 300, 600, 1800, 3600, -1};

	private static final String[] RULE_IDS = {"advance_time", "advance_weather", "spawn_mobs", "fire_damage", "keep_inventory", "mob_griefing",
			"pvp", "fall_damage", "drowning_damage", "immediate_respawn", "show_death_messages", "spawn_phantoms"};
	private static final String[] RULE_NAMES = {"Day/Night Cycle", "Weather Cycle", "Mob Spawning", "Fire Damage", "Keep Inventory", "Mob Griefing",
			"PvP", "Fall Damage", "Drowning Damage", "Instant Respawn", "Death Messages", "Phantoms"};

	private static final String[] COLOR_IDS = {"white", "aqua", "red", "gold", "green", "yellow", "light_purple", "gray"};
	private static final String[] COLOR_NAMES = {"White", "Aqua", "Red", "Gold", "Green", "Yellow", "Pink", "Gray"};
	private static final int[] COLOR_ARGB = {0xFFFFFFFF, 0xFF55FFFF, 0xFFFF5555, 0xFFFFAA00, 0xFF55FF55, 0xFFFFFF55, 0xFFFF55FF, 0xFFAAAAAA};

	private static final String[] TIERS = {"netherite", "diamond", "iron", "golden", "stone", "wooden", "chainmail", "leather"};
	private static final String[] TIER_NAMES = {"Netherite", "Diamond", "Iron", "Gold", "Stone", "Wood", "Chain", "Leather"};
	private static final String[] TIERED = {"sword", "axe", "pickaxe", "shovel", "hoe", "helmet", "chestplate", "leggings", "boots"};
	private static final String[] SOLO = {"bow", "crossbow", "trident", "mace", "shield", "elytra", "fishing_rod", "shears"};

	private record Act(String label, int theme, boolean confirm, String tip, String action, String target, String arg) {}

	private record Label(String text, int x, int y, int color) {}

	private final List<Label> labels = new ArrayList<>();
	private final List<AbstractWidget> widgets = new ArrayList<>();
	private int px, py, pw, ph;
	private int cx, cy, cw, ch;
	private int pagerPages = 1;
	private IntSupplier pagerGet = null;
	private IntConsumer pagerSet = null;
	private EditBox searchBox;
	private EditBox box1;
	private EditBox box2;
	private EditBox box3;
	private int lastMouseX = 0;
	private int lastMouseY = 0;
	private boolean dragging = false;
	private double dragRemX = 0;
	private double dragRemY = 0;

	AdminScreen() {
		super(Component.literal("Aero's Admin Panel"));
	}

	// ───────────────────────── layout ─────────────────────────

	@Override
	protected void init() {
		labels.clear();
		widgets.clear();
		pagerGet = null;
		pagerSet = null;
		pagerPages = 1;
		pw = Math.min(420, this.width - 8);
		ph = Math.min(224, this.height - 8);
		int baseX = (this.width - pw) / 2;
		int baseY = (this.height - ph) / 2;
		px = Math.max(0, Math.min(this.width - pw, baseX + Session.offX));
		py = Math.max(0, Math.min(this.height - ph, baseY + Session.offY));
		Session.offX = px - baseX;
		Session.offY = py - baseY;
		cx = px + 12;
		cw = pw - 24;
		cy = py + 46;
		ch = ph - 46 - 22;

		add(px + pw - 22, py + 2, 14, 13, "", 0, false, this::onClose).sprite("icon_close").tooltip("Hide (Esc). Press K to bring it back.");
		buildTabs();
		if (Session.pending != null) {
			buildPicker();
			return;
		}
		switch (Session.page) {
			case DASH -> buildDash();
			case SELF -> buildSelf();
			case PLAYERS -> buildPlayers();
			case ITEMS -> buildItems();
			case FORGE -> buildForge();
			case INVENTORY -> buildInventory();
			case KITS -> buildKits();
			case WORLD -> buildWorld();
			case ANNOUNCE -> buildAnnounce();
			case FUN -> buildFun();
			case TELEPORT -> buildTeleport();
			case EFFECTS -> buildEffects();
			case RULES -> buildRules();
			case BANS -> buildBans();
			case LOG -> buildLog();
		}
	}

	private void buildTabs() {
		Session.Page[] pages = Session.Page.values();
		int n = pages.length;
		int w = (cw - 2 * (n - 1)) / n;
		int used = n * w + 2 * (n - 1);
		int x0 = cx + (cw - used) / 2;
		for (int i = 0; i < n; i++) {
			Session.Page p = pages[i];
			FancyButton tab = add(x0 + i * (w + 2), py + 20, w, 20, "", i % 4, false, () -> switchPage(p));
			if (PAGE_ICONS[i].isEmpty()) {
				ItemList.Entry sword = ItemList.byId("netherite_sword");
				if (sword != null) {
					tab.icon(ItemList.stack(sword));
				}
			} else {
				tab.sprite(PAGE_ICONS[i]);
			}
			tab.tooltip(PAGE_NAMES[i]).highlighted(Session.pending == null && Session.page == p);
		}
	}

	private void switchPage(Session.Page page) {
		Session.pending = null;
		Session.page = page;
		Session.listPage = 0;
		Net.requestFor(page);
		reopen();
	}

	private void reopen() {
		this.minecraft.gui.setScreen(new AdminScreen());
	}

	/** Called by the network receiver when a list (log, bans, warps, inventory ...) arrived. */
	void dataArrived() {
		reopen();
	}

	private FancyButton add(int x, int y, int w, int h, String label, int theme, boolean confirm, Runnable r) {
		FancyButton b = this.addRenderableWidget(new FancyButton(x, y, w, h, label, theme, confirm, r));
		widgets.add(b);
		return b;
	}

	private EditBox edit(int x, int y, int w, int h, String hint, String value, int max, Consumer<String> responder) {
		EditBox box = new EditBox(this.font, x, y, w, h, Component.literal(hint));
		box.setMaxLength(max);
		box.setValue(value);
		box.setResponder(responder);
		this.addRenderableWidget(box);
		widgets.add(box);
		return box;
	}

	private void label(String text, int x, int y, int color) {
		labels.add(new Label(text, x, y, color));
	}

	/** Lays actions out in a grid inside the content area. */
	private void grid(List<Act> acts, int cols, int rows, int yOffset) {
		int availH = ch - yOffset;
		int bw = (cw - GAP * (cols - 1)) / cols;
		int bh = Math.min(26, (availH - GAP * (rows - 1)) / rows);
		for (int i = 0; i < acts.size(); i++) {
			Act a = acts.get(i);
			int col = i % cols;
			int row = i / cols;
			FancyButton b = add(cx + col * (bw + GAP), cy + yOffset + row * (bh + GAP), bw, bh, a.label(),
					a.theme() >= 0 ? a.theme() : (col % 4), a.confirm(), () -> Net.act(a.action(), a.target(), a.arg()));
			if (a.tip() != null) {
				b.tooltip(a.tip());
			}
		}
	}

	private static Act act(String label, String action, String target) {
		return new Act(label, -1, false, null, action, target, "");
	}

	private static Act act(String label, String action, String target, String arg) {
		return new Act(label, -1, false, null, action, target, arg);
	}

	private static Act danger(String label, String action, String target) {
		return new Act(label, 0, true, "Click twice to confirm", action, target, "");
	}

	private static Act danger(String label, String action, String target, String arg) {
		return new Act(label, 0, true, "Click twice to confirm", action, target, arg);
	}

	private void addPager(IntSupplier get, IntConsumer set) {
		this.pagerGet = get;
		this.pagerSet = set;
		int y = py + ph - 21;
		add(px + pw - 14 - 30, y, 30, 16, ">", 3, false, () -> {
			if (get.getAsInt() < pagerPages - 1) {
				set.accept(get.getAsInt() + 1);
				reopen();
			}
		}).tooltip("Next page (or scroll the mouse wheel)");
		add(px + pw - 14 - 30 - 34, y, 30, 16, "<", 0, false, () -> {
			if (get.getAsInt() > 0) {
				set.accept(get.getAsInt() - 1);
				reopen();
			}
		}).tooltip("Previous page (or scroll the mouse wheel)");
	}

	private static String shorten(String s, int max) {
		return s.length() <= max ? s : s.substring(0, Math.max(1, max - 1)) + "…";
	}

	private String fit(String text, int maxPx) {
		String t = text;
		while (t.length() > 4 && this.font.width(t) > maxPx) {
			t = t.substring(0, t.length() - 2) + "…";
		}
		return t;
	}

	private static String[] splitTab(String line) {
		String[] p = line.split("\t", 2);
		return new String[] {p[0], p.length > 1 ? p[1] : ""};
	}

	private static String myName() {
		Minecraft mc = Minecraft.getInstance();
		return mc.player != null ? mc.player.getName().getString() : "";
	}

	// ───────────────────────── player list (used by Players page and the picker) ─────────────────────────

	private List<String> onlineNames() {
		List<String> names = new ArrayList<>();
		ClientPacketListener connection = Minecraft.getInstance().getConnection();
		if (connection != null) {
			for (PlayerInfo info : connection.getOnlinePlayers()) {
				names.add(info.getProfile().name());
			}
		}
		names.sort(String.CASE_INSENSITIVE_ORDER);
		return names;
	}

	/**
	 * Search box + rows of players. When {@code special} is true, rows for "Me" and "Everyone" are added on top.
	 * onPick receives the chosen value: a player name, "@s" or "@a".
	 */
	private void buildPlayerList(int yOff, boolean special, String actionHint, Consumer<String> onPick) {
		List<String> all = onlineNames();
		String q = Session.playerSearch.toLowerCase(Locale.ROOT).trim();
		List<String[]> entries = new ArrayList<>();
		if (special && q.isEmpty()) {
			entries.add(new String[] {"Me (yourself)", "@s"});
			entries.add(new String[] {"Everyone online", "@a"});
		}
		for (String n : all) {
			if (q.isEmpty() || n.toLowerCase(Locale.ROOT).contains(q)) {
				entries.add(new String[] {n, n});
			}
		}
		int fieldH = 18;
		int findW = 44;
		int editW = cw - 2 * findW - 2 * GAP;
		searchBox = edit(cx, cy + yOff, editW, fieldH, "Search players", Session.playerSearch, 16, v -> Session.playerSearch = v);
		add(cx + editW + GAP, cy + yOff, findW, fieldH, "Find", 3, false, () -> {
			Session.playerSearch = searchBox.getValue();
			Session.playerPage = 0;
			reopen();
		}).tooltip("Filter the list by name");
		add(cx + editW + GAP + findW + GAP, cy + yOff, findW, fieldH, "All", 0, false, () -> {
			Session.playerSearch = "";
			Session.playerPage = 0;
			reopen();
		}).tooltip("Show every online player");

		int rowH = 18;
		int top = cy + yOff + fieldH + GAP;
		int perPage = Math.max(3, (cy + ch - top + 2) / (rowH + 2));
		pagerPages = Math.max(1, (entries.size() + perPage - 1) / perPage);
		Session.playerPage = Math.max(0, Math.min(Session.playerPage, pagerPages - 1));
		int start = Session.playerPage * perPage;
		for (int i = 0; i < perPage && start + i < entries.size(); i++) {
			String[] e = entries.get(start + i);
			add(cx, top + i * (rowH + 2), cw, rowH, e[0], 0, false, () -> onPick.accept(e[1]))
					.look(FancyButton.Look.ROW).left(true).info(actionHint).tooltip(e[0]);
		}
		if (entries.isEmpty()) {
			label("No players match.", cx + 6, top + 6, 0xFF8A8D96);
		}
		Session.status = (entries.size()) + " shown - scroll the mouse wheel for more";
		addPager(() -> Session.playerPage, v -> Session.playerPage = v);
	}

	// ───────────────────────── "who should get it?" picker ─────────────────────────

	private void buildPicker() {
		Session.Pending p = Session.pending;
		String what = switch (p.kind()) {
			case "give" -> {
				String[] b = p.b().split(" ");
				ItemList.Entry e = ItemList.byId(p.a());
				String ench = b.length > 1 && b[1].equals("1") ? " (best enchantments)" : b.length > 1 && b[1].equals("2") ? " (overpowered enchantments)" : "";
				yield "Give " + (e != null ? e.pretty() : p.a()) + " x" + b[0] + ench + " to:";
			}
			case "kit" -> "Give kit '" + p.a() + "' to:";
			default -> "Whose inventory do you want to see?";
		};
		label(what, cx + 2, cy - 2, 0xFFFF4D5E);
		add(cx + cw - 56, cy - 5, 56, 14, "Cancel", 0, false, () -> {
			Session.pending = null;
			reopen();
		});
		boolean special = !p.kind().equals("inv");
		buildPlayerList(10, special, "Choose >", value -> {
			Session.Pending done = Session.pending;
			Session.pending = null;
			switch (done.kind()) {
				case "give" -> {
					String[] b = done.b().split(" ");
					doGive(value, done.a(), Integer.parseInt(b[0]), b.length > 1 ? Integer.parseInt(b[1]) : 0);
				}
				case "kit" -> Net.act("kit_give", value, done.a());
				default -> {
					Session.invName = value.equals("@s") ? myName() : value;
					Net.request("req_inv", Session.invName, Session.invMode);
				}
			}
			reopen();
		});
	}

	private static void doGive(String who, String id, int amount, int ench) {
		if (ench > 0) {
			Net.act("forge", who, id + " " + ench + " " + Math.min(amount, 64));
		} else {
			Net.act("give", who, id + " " + amount);
		}
	}

	/** Resolves a target mode (0 ask, 1 me, 2 selected) and either gives straight away or opens the picker. */
	private void giveOrAsk(int targetMode, String id, int amount, int ench) {
		int mode = targetMode == 2 && Session.selectedPlayer == null ? 1 : targetMode;
		if (mode == 0) {
			Session.pending = new Session.Pending("give", id, amount + " " + ench);
			reopen();
			return;
		}
		doGive(mode == 2 ? Session.selectedPlayer : "@s", id, amount, ench);
	}

	// ───────────────────────── DASHBOARD ─────────────────────────

	private void buildDash() {
		int leftW = 210;
		label("SERVER", cx + 2, cy + 1, 0xFFFF4D5E);
		label("RECENT ACTIONS", cx + leftW + 14, cy + 1, 0xFFFF4D5E);
		String[] ql = {"Day", "Clear Sky", "Heal Me", "Creative", "Survival", "Save World"};
		String[][] qa = {{"time_day", ""}, {"weather_clear", ""}, {"heal", "@s"}, {"gm_creative", "@s"}, {"gm_survival", "@s"}, {"save", ""}};
		int qw = (cw - GAP * 5) / 6;
		for (int i = 0; i < 6; i++) {
			String[] a = qa[i];
			add(cx + i * (qw + GAP), cy + ch - 22, qw, 22, ql[i], i, false, () -> Net.act(a[0], a[1], "")).tooltip("Quick action");
		}
	}

	private static double parse(String s) {
		try {
			return Double.parseDouble(s);
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	private void drawBar(GuiGraphicsExtractor g, int x, int y, int w, double fraction, int color) {
		g.fill(x, y, x + w, y + 8, 0xFF1A1A20);
		int fill = (int) Math.round((w - 2) * Math.max(0.0, Math.min(1.0, fraction)));
		if (fill > 0) {
			g.fill(x + 1, y + 1, x + 1 + fill, y + 7, color);
		}
		g.fill(x, y, x + w, y + 1, 0xFF3A3B40);
		g.fill(x, y + 7, x + w, y + 8, 0xFF050507);
	}

	private void drawDash(GuiGraphicsExtractor g) {
		String[] s = Session.stats;
		int x = cx + 4;
		int y = cy + 14;
		int barW = 196;
		if (s == null || s.length < 8) {
			g.text(this.font, Component.literal(Net.available() ? "Waiting for the server..." : "This server doesn't have the mod."), x, y, 0xFF8A8D96, false);
		} else {
			double tps = parse(s[0]);
			double mspt = parse(s[1]);
			double used = parse(s[2]);
			double max = Math.max(1, parse(s[3]));
			g.text(this.font, Component.literal(String.format(Locale.ROOT, "TPS  %.1f / 20", tps)), x, y, 0xFFFFFFFF, true);
			drawBar(g, x, y + 10, barW, tps / 20.0, tps >= 18 ? 0xFFC8102E : tps >= 12 ? 0xFFFFAA00 : 0xFFFF5555);
			y += 24;
			g.text(this.font, Component.literal(String.format(Locale.ROOT, "Tick time  %.1f ms / 50", mspt)), x, y, 0xFFFFFFFF, true);
			drawBar(g, x, y + 10, barW, mspt / 50.0, mspt < 40 ? 0xFF8A8D96 : 0xFFFF5555);
			y += 24;
			g.text(this.font, Component.literal(String.format(Locale.ROOT, "Memory  %d / %d MB", (long) used, (long) max)), x, y, 0xFFFFFFFF, true);
			drawBar(g, x, y + 10, barW, used / max, used / max < 0.85 ? 0xFF8A8D96 : 0xFFFF5555);
			y += 24;
			g.text(this.font, Component.literal("Players  " + s[4] + " / " + s[5] + "     Entities  " + s[6]), x, y, 0xFFFFFFFF, true);
			y += 12;
			for (String w : s[7].split(",")) {
				if (!w.isBlank() && y < cy + ch - 28) {
					String[] kv = w.split("=", 2);
					g.text(this.font, Component.literal("  " + kv[0] + ": " + (kv.length > 1 ? kv[1] : "?")), x, y, 0xFF9A9CA6, false);
					y += 10;
				}
			}
		}
		int lx = cx + 224;
		int ly = cy + 14;
		int lw = cw - 226;
		if (Session.log.isEmpty()) {
			g.text(this.font, Component.literal("Nothing logged yet."), lx, ly, 0xFF8A8D96, false);
		}
		for (int i = 0; i < Session.log.size() && ly < cy + ch - 30; i++) {
			if (i % 2 == 0) {
				g.fill(lx - 2, ly - 1, lx + lw, ly + 10, 0x40000000);
			}
			g.text(this.font, Component.literal(fit(Session.log.get(i), lw - 4)), lx, ly, 0xFFD8D9DE, false);
			ly += 12;
		}
	}

	// ───────────────────────── SELF / WORLD / FUN ─────────────────────────

	private void buildSelf() {
		String me = "@s";
		List<Act> acts = List.of(
				act("Survival", "gm_survival", me), act("Creative", "gm_creative", me), act("Adventure", "gm_adventure", me),
				act("Spectator", "gm_spectator", me), act("Heal", "heal", me), act("Feed", "feed", me),
				act("God On", "god_on", me), act("God Off", "god_off", me), act("Vanish On", "vanish_on", me),
				act("Vanish Off", "vanish_off", me), act("Speed", "effect", me, "speed 3"), act("Night Vis", "effect", me, "night_vision 0"),
				act("Jump", "effect", me, "jump_boost 4"), act("Haste", "effect", me, "haste 4"), act("Strength", "effect", me, "strength 4"),
				act("Fire Res", "effect", me, "fire_resistance 0"), act("Water Br", "effect", me, "water_breathing 0"),
				act("Clear FX", "clear_fx", me), act("+10 Lvls", "levels", me, "10"), act("Launch", "launch", me));
		grid(acts, 6, 4, 0);
	}

	private void buildWorld() {
		List<Act> acts = List.of(
				act("Clear", "weather_clear", ""), act("Rain", "weather_rain", ""), act("Thunder", "weather_thunder", ""),
				act("Day", "time_day", ""), act("Noon", "time_noon", ""), act("Night", "time_night", ""),
				act("Midnight", "time_midnight", ""), act("Sunrise", "time_sunrise", ""), act("Peaceful", "diff_peaceful", ""),
				act("Easy", "diff_easy", ""), act("Normal", "diff_normal", ""), act("Hard", "diff_hard", ""),
				act("Set Spawn", "set_spawn", ""), act("Kill Items", "kill_items", ""), act("Kill XP", "kill_xp", ""),
				danger("Kill Mobs", "kill_hostiles", ""), act("Save", "save", ""), act("Tick Stop", "tick_freeze", ""),
				act("Tick Go", "tick_run", ""), act("Border 2k", "border", "", "2000"));
		grid(acts, 6, 4, 0);
	}

	private void buildFun() {
		List<Act> acts = List.of(
				act("Lightning", "smite", "@s"), act("Cookies All", "cookies", "@a"), act("Heal All", "heal", "@a"),
				act("Feed All", "feed", "@a"), act("Rocket", "rocket", "@s"), act("Random TP", "random_tp", "@s"),
				act("Glow All", "effect", "@a", "glowing 0 120"), act("No FX All", "clear_fx", "@a"), danger("Gather", "gather", "@s"),
				danger("All Creat", "gm_creative", "@a"), danger("All Surv", "gm_survival", "@a"), act("List On", "wl_on", ""),
				act("List Off", "wl_off", ""), act("TNT", "summon", "@s", "tnt"), danger("Stop", "stop", ""),
				act("Creeper", "summon", "@s", "creeper"), act("Golem", "summon", "@s", "iron_golem"), act("Horse", "summon", "@s", "horse"),
				act("Villager", "summon", "@s", "villager"), act("Pig", "summon", "@s", "pig"));
		grid(acts, 6, 4, 0);
	}

	// ───────────────────────── PLAYERS ─────────────────────────

	private void buildPlayers() {
		String sel = Session.selectedPlayer;
		if (sel == null) {
			buildPlayerList(0, false, "Manage >", name -> {
				Session.selectedPlayer = name;
				reopen();
			});
			return;
		}
		String p = sel;
		label("Managing  " + p, cx + 2, cy + 1, 0xFFFF4D5E);
		List<Act> acts = List.of(
				act("Heal", "heal", p), act("Feed", "feed", p), danger("Kill", "kill", p), act("TP To", "tp_to", p), act("Bring", "bring", p),
				act("Survival", "gm_survival", p), act("Creative", "gm_creative", p), act("Adventure", "gm_adventure", p),
				act("Spectator", "gm_spectator", p), act("Spy", "spy", p),
				act("Freeze", "freeze", p), act("Unfreeze", "unfreeze", p), act("Mute 10m", "mute", p, "10"),
				act("Mute Perm", "mute", p, "0"), act("Unmute", "unmute", p),
				danger("Clear Inv", "clear_inv", p), danger("Kick", "kick", p), danger("Ban", "ban", p), act("Pardon", "pardon", p),
				danger("Op", "op", p),
				danger("Deop", "deop", p), act("Smite", "smite", p));
		grid(acts, 5, 5, 14);
		int bw = (cw - GAP * 4) / 5;
		int bh = Math.min(26, (ch - 14 - GAP * 4) / 5);
		int y = cy + 14 + 4 * (bh + GAP);
		add(cx + 2 * (bw + GAP), y, bw, bh, "Inventory", 1, false, () -> {
			Session.invName = p;
			switchPage(Session.Page.INVENTORY);
		}).tooltip("View and take items from " + p);
		add(cx + 3 * (bw + GAP), y, bw, bh, "Give Item", 2, false, () -> {
			Session.itemTarget = 2;
			switchPage(Session.Page.ITEMS);
		}).tooltip("Open the item giver targeting " + p);
		add(cx + 4 * (bw + GAP), y, bw, bh, "Back", 3, false, () -> {
			Session.selectedPlayer = null;
			Session.fxToSelected = false;
			if (Session.itemTarget == 2) {
				Session.itemTarget = 1;
			}
			reopen();
		}).tooltip("Back to the player list");
	}

	// ───────────────────────── ITEMS ─────────────────────────

	private void buildItems() {
		int fieldH = 18;
		int amountW = 28;
		int targetW = 76;
		int enchW = 62;
		int findW = 34;
		int editW = cw - (4 * amountW + 3 * 2) - targetW - enchW - findW - 4 * GAP;

		searchBox = edit(cx, cy, editW, fieldH, "Search items", Session.search, 40, v -> Session.search = v);
		add(cx + editW + GAP, cy, findW, fieldH, "Find", 3, false, () -> {
			Session.itemPage = 0;
			Session.search = searchBox.getValue();
			reopen();
		}).tooltip("Search by item id");

		int[] amounts = {1, 16, 32, 64};
		int ax = cx + editW + GAP + findW + GAP;
		for (int i = 0; i < amounts.length; i++) {
			int a = amounts[i];
			add(ax + i * (amountW + 2), cy, amountW, fieldH, String.valueOf(a), 1, false, () -> {
				Session.amount = a;
				reopen();
			}).highlighted(Session.amount == a).tooltip("Give " + a + " at a time");
		}
		String[] enchNames = {"Ench: Off", "Ench: Best", "Ench: OP"};
		int ex = ax + 4 * amountW + 3 * 2 + GAP;
		add(ex, cy, enchW, fieldH, enchNames[Session.itemEnch], 2, false, () -> {
			Session.itemEnch = (Session.itemEnch + 1) % 3;
			reopen();
		}).tooltip("Best = best vanilla enchantments for gear. OP = double levels. Only affects tools, weapons and armor.");
		String tl = Session.targetLabel(Session.itemTarget);
		add(cx + cw - targetW, cy, targetW, fieldH, "To: " + (Session.itemTarget == 2 && Session.selectedPlayer != null
				? shorten(Session.selectedPlayer, 7) : tl), 0, false, () -> {
			Session.itemTarget = (Session.itemTarget + 1) % 3;
			if (Session.itemTarget == 2 && Session.selectedPlayer == null) {
				Session.itemTarget = 0;
			}
			reopen();
		}).tooltip("Ask = a player menu opens after you click an item. Me = yourself. Selected = the player chosen on the Players page.");

		int chipY = cy + fieldH + GAP;
		int chipH = 14;
		int chips = ItemList.CAT_LABELS.length;
		int chipW = (cw - GAP * (chips - 1)) / chips;
		for (int i = 0; i < chips; i++) {
			int cat = i;
			add(cx + i * (chipW + GAP), chipY, chipW, chipH, ItemList.CAT_LABELS[i], i % 4, false, () -> {
				Session.itemCat = cat;
				Session.itemPage = 0;
				reopen();
			}).tooltip(ItemList.CAT_NAMES[i]).highlighted(Session.itemCat == i);
		}

		List<ItemList.Entry> items = ItemList.filter(Session.search, Session.itemCat);
		int cell = 24;
		int pitch = cell + 2;
		int cols = Math.max(6, (cw + 2) / pitch);
		int top = chipY + chipH + GAP + 2;
		int rows = Math.max(2, (cy + ch - top + 2) / pitch);
		int perPage = cols * rows;
		int gridW = cols * pitch - 2;
		int left = cx + (cw - gridW) / 2;
		pagerPages = Math.max(1, (items.size() + perPage - 1) / perPage);
		Session.itemPage = Math.max(0, Math.min(Session.itemPage, pagerPages - 1));
		int start = Session.itemPage * perPage;
		for (int i = 0; i < perPage && start + i < items.size(); i++) {
			ItemList.Entry e = items.get(start + i);
			add(left + (i % cols) * pitch, top + (i / cols) * pitch, cell, cell, "", 0, false,
					() -> giveOrAsk(Session.itemTarget, e.id().toString(), Session.amount, Session.itemEnch))
					.look(FancyButton.Look.SLOT).icon(ItemList.stack(e)).tooltip(e.pretty() + "  (" + e.id() + ")");
		}
		if (items.isEmpty()) {
			label("No items match.", cx + 6, top + 6, 0xFF8A8D96);
			Session.status = "No items match that search.";
		} else {
			Session.status = items.size() + " items - scroll the mouse wheel to change pages";
		}
		addPager(() -> Session.itemPage, v -> Session.itemPage = v);
	}

	// ───────────────────────── FORGE ─────────────────────────

	private void buildForge() {
		label("Pick a tier, then click a piece: it is made with the best enchantments and you choose who gets it.", cx + 2, cy + 1, 0xFF8A8D96);
		int chips = TIERS.length;
		int chipW = (cw - GAP * (chips - 1)) / chips;
		for (int i = 0; i < chips; i++) {
			int t = i;
			add(cx + i * (chipW + GAP), cy + 12, chipW, 16, TIER_NAMES[i], i % 4, false, () -> {
				Session.forgeTier = t;
				reopen();
			}).highlighted(Session.forgeTier == i).tooltip(TIER_NAMES[i] + " tier (tools and armor)");
		}
		int ow = 118;
		add(cx, cy + 32, ow, 18, Session.forgeMode == 1 ? "Enchants: Best" : "Enchants: OP", 2, false, () -> {
			Session.forgeMode = Session.forgeMode == 1 ? 2 : 1;
			reopen();
		}).tooltip("Best = highest vanilla levels. OP = double those levels.");
		add(cx + ow + GAP, cy + 32, ow, 18, "To: " + (Session.forgeTarget == 2 && Session.selectedPlayer != null
				? shorten(Session.selectedPlayer, 9) : Session.targetLabel(Session.forgeTarget)), 0, false, () -> {
			Session.forgeTarget = (Session.forgeTarget + 1) % 3;
			if (Session.forgeTarget == 2 && Session.selectedPlayer == null) {
				Session.forgeTarget = 0;
			}
			reopen();
		}).tooltip("Ask = a player menu opens. Me = yourself. Selected = the player from the Players page.");

		List<String> ids = new ArrayList<>();
		for (String type : TIERED) {
			String id = TIERS[Session.forgeTier] + "_" + type;
			if (ItemList.byId(id) != null) {
				ids.add(id);
			}
		}
		for (String solo : SOLO) {
			if (ItemList.byId(solo) != null) {
				ids.add(solo);
			}
		}
		int cell = 30;
		int pitch = cell + 4;
		int cols = Math.max(4, (cw + 4) / pitch);
		int top = cy + 56;
		for (int i = 0; i < ids.size(); i++) {
			ItemList.Entry e = ItemList.byId(ids.get(i));
			int x = cx + (i % cols) * pitch;
			int y = top + (i / cols) * pitch;
			if (y + cell > cy + ch) {
				break;
			}
			add(x, y, cell, cell, "", 0, false, () -> giveOrAsk(Session.forgeTarget, e.id().toString(), 1, Session.forgeMode))
					.look(FancyButton.Look.SLOT).icon(ItemList.stack(e)).tooltip(e.pretty() + " - click to forge it");
		}
		if (ids.isEmpty()) {
			label("This tier has no items.", cx + 6, top + 6, 0xFF8A8D96);
		}
	}

	// ───────────────────────── INVENTORY VIEWER ─────────────────────────

	private void buildInventory() {
		String name = Session.invName;
		int h = 18;
		int nameW = 150;
		add(cx, cy, nameW, h, name == null ? "Pick a player..." : "Player: " + shorten(name, 13), 0, false, () -> {
			Session.pending = new Session.Pending("inv", "", "");
			reopen();
		}).tooltip("Choose whose inventory to view");
		int tabW = 70;
		add(cx + nameW + GAP, cy, tabW, h, "Inventory", 1, false, () -> {
			Session.invMode = "inv";
			Session.invSlots = new java.util.HashMap<>();
			Net.requestFor(Session.Page.INVENTORY);
			reopen();
		}).highlighted(Session.invMode.equals("inv"));
		add(cx + nameW + GAP + tabW + GAP, cy, tabW, h, "Ender Chest", 2, false, () -> {
			Session.invMode = "ender";
			Session.invSlots = new java.util.HashMap<>();
			Net.requestFor(Session.Page.INVENTORY);
			reopen();
		}).highlighted(Session.invMode.equals("ender"));
		String opLabel = Session.invOp.equals("copy") ? "Click: Copy" : Session.invOp.equals("take") ? "Click: Take" : "Click: Delete";
		add(cx + nameW + 2 * (tabW + GAP) + GAP, cy, 92, h, opLabel, Session.invOp.equals("delete") ? 0 : 3, false, () -> {
			Session.invOp = Session.invOp.equals("copy") ? "take" : Session.invOp.equals("take") ? "delete" : "copy";
			reopen();
		}).tooltip("Copy = you get a copy. Take = it moves to you. Delete = it is destroyed.");
		add(cx + cw - 56, cy, 56, h, "Refresh", 3, false, () -> {
			Net.requestFor(Session.Page.INVENTORY);
			Session.status = "Refreshing...";
		});

		if (name == null) {
			label("Choose a player to see their inventory and ender chest.", cx + 4, cy + 30, 0xFF8A8D96);
			return;
		}
		int cell = 22;
		int pitch = 24;
		int top = cy + 26;
		int left = cx + 4;
		if (Session.invMode.equals("ender")) {
			for (int i = 0; i < 27; i++) {
				invSlot(left + (i % 9) * pitch, top + (i / 9) * pitch, cell, i, name);
			}
			return;
		}
		for (int i = 0; i < 27; i++) {
			invSlot(left + (i % 9) * pitch, top + (i / 9) * pitch, cell, 9 + i, name);
		}
		for (int i = 0; i < 9; i++) {
			invSlot(left + i * pitch, top + 3 * pitch + 6, cell, i, name);
		}
		int ax = left + 9 * pitch + 14;
		String[] armorNames = {"Helmet", "Chestplate", "Leggings", "Boots", "Off hand"};
		for (int i = 0; i < 5; i++) {
			invSlot(ax, top + i * pitch, cell, 100 + i, name);
			label(armorNames[i], ax + pitch + 2, top + i * pitch + 7, 0xFF8A8D96);
		}
	}

	private void invSlot(int x, int y, int size, int slot, String name) {
		String[] data = Session.invSlots.get(slot);
		FancyButton b = add(x, y, size, size, "", 0, false, () -> {
			if (Session.invSlots.get(slot) != null) {
				Net.act("inv_op", name, Session.invMode + " " + slot + " " + Session.invOp);
			}
		}).look(FancyButton.Look.SLOT);
		if (data != null) {
			ItemList.Entry e = ItemList.byId(data[1]);
			if (e != null) {
				b.icon(ItemList.stack(e)).tooltip(e.pretty() + " x" + data[0] + "  - click to " + Session.invOp);
			}
			if (!data[0].equals("1")) {
				b.badge(data[0]);
			}
		}
	}

	// ───────────────────────── KITS ─────────────────────────

	private void buildKits() {
		label("Save everything you are carrying (with enchantments) as a kit.", cx + 2, cy + 1, 0xFF8A8D96);
		int saveW = 130;
		int targetW = 100;
		box1 = edit(cx, cy + 12, cw - saveW - targetW - 2 * GAP, 18, "Kit name", Session.kitName, 20, v -> Session.kitName = v);
		add(cx + cw - saveW - targetW - GAP, cy + 12, saveW, 18, "Save My Inventory", 2, false, () -> {
			String n = box1.getValue().trim();
			if (n.isEmpty()) {
				Session.status = "Type a kit name first.";
			} else {
				Net.act("kit_save", "", n);
			}
		}).tooltip("Saves your whole inventory, armor and off-hand under this name");
		add(cx + cw - targetW, cy + 12, targetW, 18, "To: " + (Session.kitTarget == 2 && Session.selectedPlayer != null
				? shorten(Session.selectedPlayer, 8) : Session.targetLabel(Session.kitTarget)), 0, false, () -> {
			Session.kitTarget = (Session.kitTarget + 1) % 3;
			if (Session.kitTarget == 2 && Session.selectedPlayer == null) {
				Session.kitTarget = 0;
			}
			reopen();
		}).tooltip("Ask = a player menu opens. Me = yourself. Selected = the player from the Players page.");

		int rowH = 18;
		int top = cy + 36;
		int perPage = Math.max(3, (cy + ch - top + 2) / (rowH + 2));
		List<String> kits = Session.kits;
		pagerPages = Math.max(1, (kits.size() + perPage - 1) / perPage);
		Session.kitPage = Math.max(0, Math.min(Session.kitPage, pagerPages - 1));
		int start = Session.kitPage * perPage;
		int giveW = 50;
		int delW = 36;
		for (int i = 0; i < perPage && start + i < kits.size(); i++) {
			String[] k = splitTab(kits.get(start + i));
			int y = top + i * (rowH + 2);
			add(cx, y, cw - giveW - delW - 2 * GAP, rowH, k[0], 0, false, () -> { })
					.look(FancyButton.Look.ROW).left(true).info(k[1]);
			add(cx + cw - giveW - delW - GAP, y, giveW, rowH, "Give", 3, false, () -> giveKit(k[0])).tooltip("Give this kit");
			add(cx + cw - delW, y, delW, rowH, "Del", 0, true, () -> Net.act("kit_del", "", k[0])).tooltip("Delete this kit (click twice)");
		}
		if (kits.isEmpty()) {
			label("No kits yet. Fill your inventory, type a name and press Save My Inventory.", cx + 6, top + 6, 0xFF8A8D96);
		}
		addPager(() -> Session.kitPage, v -> Session.kitPage = v);
	}

	private void giveKit(String name) {
		int mode = Session.kitTarget == 2 && Session.selectedPlayer == null ? 1 : Session.kitTarget;
		if (mode == 0) {
			Session.pending = new Session.Pending("kit", name, "");
			reopen();
		} else {
			Net.act("kit_give", mode == 2 ? Session.selectedPlayer : "@s", name);
		}
	}

	// ───────────────────────── ANNOUNCE ─────────────────────────

	private void buildAnnounce() {
		label("Message to everyone (plain text)", cx + 2, cy + 1, 0xFF8A8D96);
		box1 = edit(cx, cy + 12, cw, 18, "Announcement", Session.announceText, 120, v -> Session.announceText = v);

		label("Color", cx + 2, cy + 35, 0xFF8A8D96);
		int chips = COLOR_IDS.length;
		int chipW = (cw - GAP * (chips - 1)) / chips;
		for (int i = 0; i < chips; i++) {
			String id = COLOR_IDS[i];
			add(cx + i * (chipW + GAP), cy + 45, chipW, 15, COLOR_NAMES[i], 0, false, () -> {
				Session.announceColor = id;
				reopen();
			}).color(COLOR_ARGB[i]).highlighted(Session.announceColor.equals(id)).tooltip("Use " + COLOR_NAMES[i].toLowerCase(Locale.ROOT));
		}

		String[] labels2 = {"Chat", "Title", "Action Bar", "Chat + Title"};
		String[] actions = {"announce", "title", "actionbar", "announce_both"};
		String[] tips = {"Message in everyone's chat", "Big title on everyone's screen", "Text above everyone's hotbar", "Both at once"};
		int bw = (cw - GAP * 3) / 4;
		for (int i = 0; i < 4; i++) {
			String action = actions[i];
			add(cx + i * (bw + GAP), cy + 66, bw, 22, labels2[i], i, false, () -> {
				String m = box1.getValue().trim();
				if (m.isEmpty()) {
					Session.status = "Type a message first.";
				} else {
					Net.act(action, "", Session.announceColor + " " + m);
				}
			}).tooltip(tips[i]);
		}

		label("Quick messages (click to fill the box)", cx + 2, cy + 94, 0xFF8A8D96);
		String[] presets = {"Server restarting in 5 minutes!", "Event starting soon - join us!", "Please be respectful to everyone.",
				"Maintenance in 10 minutes.", "Welcome to the server!", "Read the rules before playing."};
		String[] short2 = {"Restart 5m", "Event Soon", "Be Respectful", "Maintenance", "Welcome", "Read Rules"};
		int pw3 = (cw - GAP * 2) / 3;
		for (int i = 0; i < presets.length; i++) {
			String text = presets[i];
			add(cx + (i % 3) * (pw3 + GAP), cy + 105 + (i / 3) * 22, pw3, 18, short2[i], i % 4, false, () -> {
				Session.announceText = text;
				reopen();
			}).tooltip(text);
		}

		int ew = (cw - GAP * 2) / 3;
		int ey = cy + ch - 22;
		add(cx, ey, ew, 20, "Clear Chat", 0, true, () -> Net.act("clear_chat", "", "")).tooltip("Click twice: scroll everyone's chat away");
		add(cx + ew + GAP, ey, ew, 20, "Clear Title", 1, false, () -> Net.act("clear_title", "", "")).tooltip("Remove any title on screen");
		add(cx + 2 * (ew + GAP), ey, ew, 20, "Ping Sound", 2, false, () -> Net.act("ping", "", "")).tooltip("Play a note to get everyone's attention");
	}

	// ───────────────────────── TELEPORT ─────────────────────────

	private void buildTeleport() {
		label("Coordinates (use ~ for 'here')", cx + 2, cy + 1, 0xFF8A8D96);
		int bw = 70;
		box1 = edit(cx, cy + 12, bw, 18, "X", Session.tpX, 10, v -> Session.tpX = v);
		box2 = edit(cx + bw + GAP, cy + 12, bw, 18, "Y", Session.tpY, 10, v -> Session.tpY = v);
		box3 = edit(cx + 2 * (bw + GAP), cy + 12, bw, 18, "Z", Session.tpZ, 10, v -> Session.tpZ = v);
		add(cx + 3 * (bw + GAP), cy + 12, 60, 18, "Go", 3, false, () -> {
			String x = box1.getValue().trim().isEmpty() ? "~" : box1.getValue().trim();
			String y = box2.getValue().trim().isEmpty() ? "~" : box2.getValue().trim();
			String z = box3.getValue().trim().isEmpty() ? "~" : box3.getValue().trim();
			Net.act("tp_xyz", "@s", x + " " + y + " " + z);
		}).tooltip("Teleport to these coordinates");

		int qx = cx + 3 * (bw + GAP) + 60 + GAP + 6;
		int qw = (cx + cw - qx - GAP * 3) / 4;
		String[] ql = {"Surface", "Overworld", "Nether", "End"};
		String[] qa = {"tp_top", "tp_overworld", "tp_nether", "tp_end"};
		String[] qt = {"Up to the surface above you", "Jump to the overworld surface", "Jump to the Nether (careful, lava)", "Jump to the End platform"};
		for (int i = 0; i < 4; i++) {
			String action = qa[i];
			add(qx + i * (qw + GAP), cy + 12, qw, 18, ql[i], i, false, () -> Net.act(action, "@s", "")).tooltip(qt[i]);
		}

		label("Warps (saved places)", cx + 2, cy + 38, 0xFF8A8D96);
		int saveW = 80;
		box1 = edit(cx, cy + 48, cw - saveW - GAP, 18, "Warp name", Session.warpName, 20, v -> Session.warpName = v);
		add(cx + cw - saveW, cy + 48, saveW, 18, "Save Here", 2, false, () -> {
			String n = box1.getValue().trim();
			if (n.isEmpty()) {
				Session.status = "Type a warp name first.";
			} else {
				Net.act("warp_set", "", n);
			}
		}).tooltip("Save your current position under this name");

		int rowH = 18;
		int top = cy + 70;
		int perPage = Math.max(2, (cy + ch - top + 2) / (rowH + 2));
		List<String> warps = Session.warps;
		pagerPages = Math.max(1, (warps.size() + perPage - 1) / perPage);
		Session.warpPage = Math.max(0, Math.min(Session.warpPage, pagerPages - 1));
		int start = Session.warpPage * perPage;
		int delW = 36;
		for (int i = 0; i < perPage && start + i < warps.size(); i++) {
			String[] w = splitTab(warps.get(start + i));
			int y = top + i * (rowH + 2);
			add(cx, y, cw - delW - GAP, rowH, w[0], 0, false, () -> Net.act("warp_go", "", w[0]))
					.look(FancyButton.Look.ROW).left(true).info(w[1]).tooltip("Teleport to " + w[0]);
			add(cx + cw - delW, y, delW, rowH, "Del", 0, true, () -> Net.act("warp_del", "", w[0])).tooltip("Delete this warp (click twice)");
		}
		if (warps.isEmpty()) {
			label("No warps yet. Stand somewhere, name it and press Save Here.", cx + 6, top + 6, 0xFF8A8D96);
		}
		addPager(() -> Session.warpPage, v -> Session.warpPage = v);
	}

	// ───────────────────────── EFFECTS ─────────────────────────

	private void buildEffects() {
		int cols = 8;
		int bw = (cw - GAP * (cols - 1)) / cols;
		int bh = 18;
		for (int i = 0; i < FX_IDS.length; i++) {
			String id = FX_IDS[i];
			add(cx + (i % cols) * (bw + GAP), cy + (i / cols) * (bh + 2), bw, bh, FX_NAMES[i], i % 4, false, () -> {
				Session.fxId = id;
				reopen();
			}).highlighted(Session.fxId.equals(id)).tooltip(id);
		}

		int cy2 = cy + 4 * (bh + 2) + 8;
		int sw = 18;
		label("Level", cx + 2, cy2 + 5, 0xFF8A8D96);
		add(cx + 34, cy2, sw, sw, "", 0, false, () -> {
			Session.fxLevel = Math.max(1, Session.fxLevel - 1);
			reopen();
		}).base("step_minus");
		label(String.valueOf(Session.fxLevel), cx + 34 + sw + 6, cy2 + 5, 0xFFFFFFFF);
		add(cx + 34 + sw + 24, cy2, sw, sw, "", 0, false, () -> {
			Session.fxLevel = Math.min(20, Session.fxLevel + 1);
			reopen();
		}).base("step_plus");
		int dx = cx + 34 + sw + 24 + sw + 20;
		label("Time", dx, cy2 + 5, 0xFF8A8D96);
		add(dx + 28, cy2, sw, sw, "", 0, false, () -> {
			Session.fxDur = Math.max(0, Session.fxDur - 1);
			reopen();
		}).base("step_minus");
		label(DUR_NAMES[Session.fxDur], dx + 28 + sw + 5, cy2 + 5, 0xFFFFFFFF);
		add(dx + 28 + sw + 5 + 44, cy2, sw, sw, "", 0, false, () -> {
			Session.fxDur = Math.min(DUR_NAMES.length - 1, Session.fxDur + 1);
			reopen();
		}).base("step_plus");
		String who = Session.fxToSelected && Session.selectedPlayer != null ? shorten(Session.selectedPlayer, 9) : "Me";
		int tw = 90;
		add(cx + cw - tw, cy2, tw, sw, "To: " + who, 0, false, () -> {
			if (Session.selectedPlayer == null) {
				Session.status = "Pick a player on the Players page first.";
			} else {
				Session.fxToSelected = !Session.fxToSelected;
			}
			reopen();
		}).tooltip("Switch between you and the selected player");

		int by = cy2 + sw + 8;
		int aw = (cw - GAP * 2) / 3;
		add(cx, by, aw, 22, "Apply Effect", 3, false, () -> {
			String target = Session.fxToSelected && Session.selectedPlayer != null ? Session.selectedPlayer : "@s";
			int secs = DUR_SECS[Session.fxDur];
			Net.act("effect", target, Session.fxId + " " + (Session.fxLevel - 1) + " " + (secs < 0 ? "inf" : String.valueOf(secs)));
		}).tooltip("Give the chosen effect");
		add(cx + aw + GAP, by, aw, 22, "Remove This", 1, false, () -> {
			String target = Session.fxToSelected && Session.selectedPlayer != null ? Session.selectedPlayer : "@s";
			Net.act("effect_clear_one", target, Session.fxId);
		}).tooltip("Remove only the chosen effect");
		add(cx + 2 * (aw + GAP), by, aw, 22, "Clear All", 0, false, () -> {
			String target = Session.fxToSelected && Session.selectedPlayer != null ? Session.selectedPlayer : "@s";
			Net.act("clear_fx", target, "");
		}).tooltip("Remove every effect");
	}

	// ───────────────────────── RULES ─────────────────────────

	private void buildRules() {
		label("Game rules - press On or Off. (The current value isn't shown.)", cx + 2, cy + 1, 0xFF8A8D96);
		int colW = (cw - GAP) / 2;
		int btn = 38;
		for (int i = 0; i < RULE_IDS.length; i++) {
			String id = RULE_IDS[i];
			int x = cx + (i % 2) * (colW + GAP);
			int y = cy + 14 + (i / 2) * 23;
			add(x, y, colW - 2 * btn - 4, 20, RULE_NAMES[i], 0, false, () -> Net.act("rule", "", id + " true"))
					.look(FancyButton.Look.ROW).left(true).tooltip(id);
			add(x + colW - 2 * btn - 2, y, btn, 20, "On", 3, false, () -> Net.act("rule", "", id + " true")).tooltip("Turn " + id + " on");
			add(x + colW - btn, y, btn, 20, "Off", 0, false, () -> Net.act("rule", "", id + " false")).tooltip("Turn " + id + " off");
		}
	}

	// ───────────────────────── BANS & MUTES ─────────────────────────

	private void buildBans() {
		int tabW = (cw - 2 * GAP) / 3;
		add(cx, cy, tabW, 18, "Mutes (" + Session.mutes.size() + ")", 0, false, () -> {
			Session.bansTab = 0;
			Session.listPage = 0;
			reopen();
		}).highlighted(Session.bansTab == 0);
		add(cx + tabW + GAP, cy, tabW, 18, "Bans (" + Session.bans.size() + ")", 1, false, () -> {
			Session.bansTab = 1;
			Session.listPage = 0;
			reopen();
		}).highlighted(Session.bansTab == 1);
		add(cx + 2 * (tabW + GAP), cy, tabW, 18, "Refresh", 3, false, () -> {
			Net.requestFor(Session.Page.BANS);
			Session.status = "Refreshing...";
		});

		List<String> list = Session.bansTab == 0 ? Session.mutes : Session.bans;
		int rowH = 18;
		int top = cy + 22;
		int perPage = Math.max(3, (cy + ch - top + 2) / (rowH + 2));
		pagerPages = Math.max(1, (list.size() + perPage - 1) / perPage);
		Session.listPage = Math.max(0, Math.min(Session.listPage, pagerPages - 1));
		int start = Session.listPage * perPage;
		int liftW = 44;
		for (int i = 0; i < perPage && start + i < list.size(); i++) {
			String[] e = splitTab(list.get(start + i));
			int y = top + i * (rowH + 2);
			String action = Session.bansTab == 0 ? "unmute" : "pardon";
			add(cx, y, cw - liftW - GAP, rowH, e[0], 0, false, () -> { })
					.look(FancyButton.Look.ROW).left(true).info(e[1]).tooltip(e[1].isEmpty() ? e[0] : e[0] + ": " + e[1]);
			add(cx + cw - liftW, y, liftW, rowH, "Lift", 2, false, () -> Net.act(action, e[0], "")).tooltip(Session.bansTab == 0 ? "Unmute " + e[0] : "Unban " + e[0]);
		}
		if (list.isEmpty()) {
			label(Session.bansTab == 0 ? "Nobody is muted." : "Nobody is banned.", cx + 6, top + 6, 0xFF8A8D96);
		}
		addPager(() -> Session.listPage, v -> Session.listPage = v);
	}

	// ───────────────────────── LOG ─────────────────────────

	private void buildLog() {
		int perPage = Math.max(4, (ch - 22) / 12);
		pagerPages = Math.max(1, (Session.log.size() + perPage - 1) / perPage);
		Session.listPage = Math.max(0, Math.min(Session.listPage, pagerPages - 1));
		add(cx, cy, 60, 16, "Refresh", 3, false, () -> {
			Net.requestFor(Session.Page.LOG);
			Session.status = "Refreshing...";
		});
		label("Newest first - " + Session.log.size() + " entries kept", cx + 68, cy + 4, 0xFF8A8D96);
		addPager(() -> Session.listPage, v -> Session.listPage = v);
	}

	private void drawLog(GuiGraphicsExtractor g) {
		int perPage = Math.max(4, (ch - 22) / 12);
		int start = Session.listPage * perPage;
		int y = cy + 22;
		if (Session.log.isEmpty()) {
			g.text(this.font, Component.literal("Nothing logged yet."), cx + 4, y + 2, 0xFF8A8D96, false);
			return;
		}
		for (int i = 0; i < perPage && start + i < Session.log.size(); i++) {
			if (i % 2 == 0) {
				g.fill(cx, y - 1, cx + cw, y + 11, 0x40000000);
			}
			g.text(this.font, Component.literal(fit(Session.log.get(start + i), cw - 8)), cx + 4, y + 1, 0xFFD8D9DE, false);
			y += 12;
		}
	}

	// ───────────────────────── input: drag the window, scroll the lists ─────────────────────────

	private boolean inTitleBar(int x, int y) {
		return x >= px && x <= px + pw && y >= py && y <= py + 17;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		dragging = false;
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}
		if (inTitleBar(lastMouseX, lastMouseY)) {
			dragging = true;
			dragRemX = 0;
			dragRemY = 0;
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (!dragging) {
			return super.mouseDragged(event, dx, dy);
		}
		dragRemX += dx;
		dragRemY += dy;
		int mx = (int) dragRemX;
		int my = (int) dragRemY;
		dragRemX -= mx;
		dragRemY -= my;
		int nx = Math.max(0, Math.min(this.width - pw, px + mx));
		int ny = Math.max(0, Math.min(this.height - ph, py + my));
		int sx = nx - px;
		int sy = ny - py;
		if (sx != 0 || sy != 0) {
			for (AbstractWidget w : widgets) {
				w.setX(w.getX() + sx);
				w.setY(w.getY() + sy);
			}
			List<Label> moved = new ArrayList<>();
			for (Label l : labels) {
				moved.add(new Label(l.text(), l.x() + sx, l.y() + sy, l.color()));
			}
			labels.clear();
			labels.addAll(moved);
			px += sx;
			py += sy;
			cx += sx;
			cy += sy;
			Session.offX += sx;
			Session.offY += sy;
		}
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		dragging = false;
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (pagerGet != null && pagerSet != null && pagerPages > 1 && scrollY != 0) {
			int now = pagerGet.getAsInt();
			int next = scrollY < 0 ? Math.min(pagerPages - 1, now + 1) : Math.max(0, now - 1);
			if (next != now) {
				pagerSet.accept(next);
				reopen();
			}
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	// ───────────────────────── rendering ─────────────────────────

	/** No dimming and no blur: the game stays fully visible behind the floating window. */
	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		lastMouseX = mouseX;
		lastMouseY = mouseY;
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, PANEL, px, py, pw, ph);

		graphics.text(this.font, Component.literal("AERO'S ADMIN PANEL"), px + 12, py + 4, 0xFFFF4D5E, true);
		String right = Session.pending != null ? "Choose a player"
				: Session.selectedPlayer != null && Session.page == Session.Page.PLAYERS ? "Player: " + Session.selectedPlayer
				: PAGE_NAMES[Session.page.ordinal()];
		graphics.text(this.font, Component.literal(right), px + pw - 30 - this.font.width(right), py + 4, 0xFFC9CBD2, true);

		if (Session.pending == null && Session.page == Session.Page.DASH) {
			long now = System.currentTimeMillis();
			if (now - Session.statsAt > 1000) {
				Session.statsAt = now;
				Net.request("req_stats");
			}
			if (now - Session.logAt > 5000) {
				Session.logAt = now;
				Net.request("req_log");
			}
		}

		super.extractRenderState(graphics, mouseX, mouseY, delta);

		for (Label l : labels) {
			graphics.text(this.font, Component.literal(l.text()), l.x(), l.y(), l.color(), false);
		}
		if (Session.pending == null) {
			if (Session.page == Session.Page.DASH) {
				drawDash(graphics);
			} else if (Session.page == Session.Page.LOG) {
				drawLog(graphics);
			}
		}

		boolean paged = pagerGet != null && pagerPages > 1;
		int statusMax = pw - 28 - (paged ? 100 : 0);
		graphics.text(this.font, Component.literal(fit(Session.status, statusMax)), cx + 2, py + ph - 17, 0xFF9A9CA6, false);
		if (paged) {
			String pageText = (pagerGet.getAsInt() + 1) + "/" + pagerPages;
			graphics.text(this.font, Component.literal(pageText), px + pw - 14 - 30 - 34 - 4 - this.font.width(pageText),
					py + ph - 17, 0xFFFFFFFF, true);
		}
	}
}
