package dev.floatingadmin.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** Aero's Admin Panel: one screen, a sidebar of pages, drawn entirely with the crimson sprites. */
final class AdminScreen extends Screen {
	private static final Identifier PANEL = Identifier.fromNamespaceAndPath(FloatingAdminClient.MOD_ID, "panel");
	private static final int GAP = 4;
	private static final int SIDEBAR = 2 * 22 + 6;

	private static final String[] PAGE_NAMES = {"Self", "Players", "Items", "World", "Announce", "Fun", "Teleport", "Effects", "Rules", "Bans & Mutes", "Server Stats", "Action Log"};
	private static final String[] PAGE_ICONS = {"tab_self", "tab_players", "tab_items", "tab_world", "tab_announce", "tab_fun",
			"tab_teleport", "tab_effects", "tab_rules", "tab_bans", "tab_stats", "tab_log"};

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

	private record Act(String label, int theme, boolean confirm, String tip, String action, String target, String arg) {}

	private record Label(String text, int x, int y, int color) {}

	private final List<Label> labels = new ArrayList<>();
	private int px, py, pw, ph;
	private int cx, cy, cw, ch;
	private int pagerPages = 1;
	private EditBox searchBox;
	private EditBox box1;
	private EditBox box2;
	private EditBox box3;

	AdminScreen() {
		super(Component.literal("Aero's Admin Panel"));
	}

	// ───────────────────────── layout ─────────────────────────

	@Override
	protected void init() {
		labels.clear();
		pw = Math.min(420, this.width - 8);
		ph = Math.min(224, this.height - 8);
		px = (this.width - pw) / 2;
		py = (this.height - ph) / 2;
		cx = px + 10 + SIDEBAR;
		cw = pw - 10 - SIDEBAR - 12;
		cy = py + 22;
		ch = ph - 22 - 22;

		add(px + pw - 22, py + 2, 14, 13, "", 0, false, this::onClose).sprite("icon_close").tooltip("Close (or press Esc)");
		buildSidebar();
		switch (Session.page) {
			case SELF -> buildSelf();
			case PLAYERS -> buildPlayers();
			case ITEMS -> buildItems();
			case WORLD -> buildWorld();
			case ANNOUNCE -> buildAnnounce();
			case FUN -> buildFun();
			case TELEPORT -> buildTeleport();
			case EFFECTS -> buildEffects();
			case RULES -> buildRules();
			case BANS -> buildBans();
			case STATS -> buildStats();
			case LOG -> buildLog();
		}
	}

	private void buildSidebar() {
		Session.Page[] pages = Session.Page.values();
		for (int i = 0; i < pages.length; i++) {
			Session.Page p = pages[i];
			int x = px + 10 + (i % 2) * 22;
			int y = py + 22 + (i / 2) * 24;
			add(x, y, 20, 20, "", i % 4, false, () -> switchPage(p)).sprite(PAGE_ICONS[i]).tooltip(PAGE_NAMES[i])
					.highlighted(Session.page == p);
		}
	}

	private void switchPage(Session.Page page) {
		Session.page = page;
		Session.listPage = 0;
		Net.requestFor(page);
		reopen();
	}

	private void reopen() {
		this.minecraft.gui.setScreen(new AdminScreen());
	}

	/** Called by the network receiver when a list (log, bans, warps ...) arrived. */
	void dataArrived() {
		reopen();
	}

	private FancyButton add(int x, int y, int w, int h, String label, int theme, boolean confirm, Runnable r) {
		return this.addRenderableWidget(new FancyButton(x, y, w, h, label, theme, confirm, r));
	}

	private EditBox edit(int x, int y, int w, int h, String hint, String value, int max, Consumer<String> responder) {
		EditBox box = new EditBox(this.font, x, y, w, h, Component.literal(hint));
		box.setMaxLength(max);
		box.setValue(value);
		box.setResponder(responder);
		this.addRenderableWidget(box);
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
		int y = py + ph - 21;
		add(px + pw - 14 - 30, y, 30, 16, ">", 3, false, () -> {
			if (get.getAsInt() < pagerPages - 1) {
				set.accept(get.getAsInt() + 1);
				reopen();
			}
		});
		add(px + pw - 14 - 30 - 34, y, 30, 16, "<", 0, false, () -> {
			if (get.getAsInt() > 0) {
				set.accept(get.getAsInt() - 1);
				reopen();
			}
		});
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

	// ───────────────────────── SELF ─────────────────────────

	private void buildSelf() {
		String me = "@s";
		List<Act> acts = List.of(
				act("Survival", "gm_survival", me), act("Creative", "gm_creative", me), act("Adventure", "gm_adventure", me),
				act("Spectator", "gm_spectator", me), act("Heal", "heal", me),
				act("Feed", "feed", me), act("God On", "god_on", me), act("God Off", "god_off", me),
				act("Vanish On", "vanish_on", me), act("Vanish Off", "vanish_off", me),
				act("Speed", "effect", me, "speed 3"), act("Night Vis", "effect", me, "night_vision 0"), act("Jump", "effect", me, "jump_boost 4"),
				act("Haste", "effect", me, "haste 4"), act("Strength", "effect", me, "strength 4"),
				act("Fire Res", "effect", me, "fire_resistance 0"), act("Water Br", "effect", me, "water_breathing 0"),
				act("Clear FX", "clear_fx", me), act("+10 Lvls", "levels", me, "10"), act("Launch", "launch", me));
		grid(acts, 5, 4, 0);
	}

	// ───────────────────────── PLAYERS ─────────────────────────

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

	private void buildPlayers() {
		String sel = Session.selectedPlayer;
		if (sel == null) {
			List<String> all = onlineNames();
			String q = Session.playerSearch.toLowerCase(Locale.ROOT).trim();
			List<String> names = new ArrayList<>();
			for (String n : all) {
				if (q.isEmpty() || n.toLowerCase(Locale.ROOT).contains(q)) {
					names.add(n);
				}
			}
			int fieldH = 18;
			int findW = 44;
			int editW = cw - 2 * findW - 2 * GAP;
			searchBox = edit(cx, cy, editW, fieldH, "Search players", Session.playerSearch, 16, v -> Session.playerSearch = v);
			add(cx + editW + GAP, cy, findW, fieldH, "Find", 3, false, () -> {
				Session.playerSearch = searchBox.getValue();
				Session.playerPage = 0;
				reopen();
			}).tooltip("Filter the list by name");
			add(cx + editW + GAP + findW + GAP, cy, findW, fieldH, "All", 0, false, () -> {
				Session.playerSearch = "";
				Session.playerPage = 0;
				reopen();
			}).tooltip("Show every online player");

			int rowH = 20;
			int top = cy + fieldH + GAP + 2;
			int perPage = Math.max(3, (cy + ch - top + 2) / (rowH + 2));
			pagerPages = Math.max(1, (names.size() + perPage - 1) / perPage);
			Session.playerPage = Math.max(0, Math.min(Session.playerPage, pagerPages - 1));
			int start = Session.playerPage * perPage;
			for (int i = 0; i < perPage && start + i < names.size(); i++) {
				String n = names.get(start + i);
				add(cx, top + i * (rowH + 2), cw, rowH, n, 0, false, () -> {
					Session.selectedPlayer = n;
					reopen();
				}).look(FancyButton.Look.ROW).left(true).info("Manage >").tooltip("Open controls for " + n);
			}
			if (names.isEmpty()) {
				label("No players match.", cx + 6, top + 6, 0xFF8A8D96);
			}
			Session.status = names.size() + " of " + all.size() + " players shown";
			addPager(() -> Session.playerPage, v -> Session.playerPage = v);
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
				danger("Deop", "deop", p), act("Smite", "smite", p), act("+10 Lvls", "levels", p, "10"));
		grid(acts, 5, 5, 14);
		int bw = (cw - GAP * 4) / 5;
		int bh = Math.min(26, (ch - 14 - GAP * 4) / 5);
		int y = cy + 14 + 4 * (bh + GAP);
		add(cx + 3 * (bw + GAP), y, bw, bh, "Give Item", 2, false, () -> {
			Session.giveToSelected = true;
			switchPage(Session.Page.ITEMS);
		}).tooltip("Open the item giver targeting " + p);
		add(cx + 4 * (bw + GAP), y, bw, bh, "Back", 3, false, () -> {
			Session.selectedPlayer = null;
			Session.giveToSelected = false;
			Session.fxToSelected = false;
			reopen();
		}).tooltip("Back to the player list");
	}

	// ───────────────────────── ITEMS ─────────────────────────

	private void buildItems() {
		int fieldH = 18;
		int amountW = 28;
		int targetW = 84;
		int findW = 34;
		int editW = cw - (4 * amountW + 3 * 2) - targetW - findW - 3 * GAP;

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
		String target = Session.giveToSelected && Session.selectedPlayer != null ? shorten(Session.selectedPlayer, 9) : "Me";
		add(cx + cw - targetW, cy, targetW, fieldH, "To: " + target, 0, false, () -> {
			if (Session.selectedPlayer == null) {
				Session.status = "Pick a player on the Players page first to give items to them.";
			} else {
				Session.giveToSelected = !Session.giveToSelected;
			}
			reopen();
		}).tooltip("Click to switch between you and the selected player");

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
			add(left + (i % cols) * pitch, top + (i / cols) * pitch, cell, cell, "", 0, false, () -> {
				String who = Session.giveToSelected && Session.selectedPlayer != null ? Session.selectedPlayer : "@s";
				Net.act("give", who, e.id() + " " + Session.amount);
			}).look(FancyButton.Look.SLOT).icon(ItemList.stack(e)).tooltip(e.pretty() + "  (" + e.id() + ")");
		}
		if (items.isEmpty()) {
			label("No items match.", cx + 6, top + 6, 0xFF8A8D96);
			Session.status = "No items match that search.";
		} else {
			Session.status = items.size() + " items";
		}
		addPager(() -> Session.itemPage, v -> Session.itemPage = v);
	}

	// ───────────────────────── WORLD ─────────────────────────

	private void buildWorld() {
		List<Act> acts = List.of(
				act("Clear", "weather_clear", ""), act("Rain", "weather_rain", ""), act("Thunder", "weather_thunder", ""),
				act("Day", "time_day", ""), act("Noon", "time_noon", ""),
				act("Night", "time_night", ""), act("Midnight", "time_midnight", ""), act("Sunrise", "time_sunrise", ""),
				act("Peaceful", "diff_peaceful", ""), act("Easy", "diff_easy", ""),
				act("Normal", "diff_normal", ""), act("Hard", "diff_hard", ""), act("Set Spawn", "set_spawn", ""),
				act("Kill Items", "kill_items", ""), act("Kill XP", "kill_xp", ""),
				danger("Kill Mobs", "kill_hostiles", ""), act("Save", "save", ""), act("Tick Stop", "tick_freeze", ""),
				act("Tick Go", "tick_run", ""), act("Border 2k", "border", "", "2000"));
		grid(acts, 5, 4, 0);
	}

	// ───────────────────────── ANNOUNCE ─────────────────────────

	private void buildAnnounce() {
		label("Message to everyone (supports plain text)", cx + 2, cy + 1, 0xFF8A8D96);
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
		add(cx, cy + 152, ew, 20, "Clear Chat", 0, true, () -> Net.act("clear_chat", "", "")).tooltip("Click twice: scroll everyone's chat away");
		add(cx + ew + GAP, cy + 152, ew, 20, "Clear Title", 1, false, () -> Net.act("clear_title", "", "")).tooltip("Remove any title on screen");
		add(cx + 2 * (ew + GAP), cy + 152, ew, 20, "Ping Sound", 2, false, () -> Net.act("ping", "", "")).tooltip("Play a note to get everyone's attention");
	}

	// ───────────────────────── FUN ─────────────────────────

	private void buildFun() {
		List<Act> acts = List.of(
				act("Lightning", "smite", "@s"), act("Cookies All", "cookies", "@a"), act("Heal All", "heal", "@a"),
				act("Feed All", "feed", "@a"), act("Rocket", "rocket", "@s"),
				act("Random TP", "random_tp", "@s"), act("Glow All", "effect", "@a", "glowing 0 120"),
				act("No FX All", "clear_fx", "@a"), danger("Gather", "gather", "@s"), danger("All Creat", "gm_creative", "@a"),
				danger("All Surv", "gm_survival", "@a"), act("List On", "wl_on", ""), act("List Off", "wl_off", ""),
				act("TNT", "summon", "@s", "tnt"), danger("Stop", "stop", ""),
				act("Creeper", "summon", "@s", "creeper"), act("Golem", "summon", "@s", "iron_golem"), act("Horse", "summon", "@s", "horse"),
				act("Villager", "summon", "@s", "villager"), act("Pig", "summon", "@s", "pig"));
		grid(acts, 5, 4, 0);
	}

	// ───────────────────────── TELEPORT ─────────────────────────

	private void buildTeleport() {
		label("Coordinates (use ~ for 'here')", cx + 2, cy + 1, 0xFF8A8D96);
		int bw = 62;
		box1 = edit(cx, cy + 12, bw, 18, "X", Session.tpX, 10, v -> Session.tpX = v);
		box2 = edit(cx + bw + GAP, cy + 12, bw, 18, "Y", Session.tpY, 10, v -> Session.tpY = v);
		box3 = edit(cx + 2 * (bw + GAP), cy + 12, bw, 18, "Z", Session.tpZ, 10, v -> Session.tpZ = v);
		add(cx + 3 * (bw + GAP), cy + 12, cw - 3 * (bw + GAP), 18, "Go", 3, false, () -> {
			String x = box1.getValue().trim().isEmpty() ? "~" : box1.getValue().trim();
			String y = box2.getValue().trim().isEmpty() ? "~" : box2.getValue().trim();
			String z = box3.getValue().trim().isEmpty() ? "~" : box3.getValue().trim();
			Net.act("tp_xyz", "@s", x + " " + y + " " + z);
		}).tooltip("Teleport to these coordinates");

		int qw = (cw - GAP * 3) / 4;
		String[] ql = {"Surface", "Overworld", "Nether", "End"};
		String[] qa = {"tp_top", "tp_overworld", "tp_nether", "tp_end"};
		String[] qt = {"Up to the surface above you", "Jump to the overworld surface", "Jump to the Nether (careful, lava)", "Jump to the End platform"};
		for (int i = 0; i < 4; i++) {
			String action = qa[i];
			add(cx + i * (qw + GAP), cy + 36, qw, 20, ql[i], i, false, () -> Net.act(action, "@s", "")).tooltip(qt[i]);
		}

		label("Warps (saved places)", cx + 2, cy + 62, 0xFF8A8D96);
		int saveW = 70;
		box1 = edit(cx, cy + 72, cw - saveW - GAP, 18, "Warp name", Session.warpName, 20, v -> Session.warpName = v);
		add(cx + cw - saveW, cy + 72, saveW, 18, "Save Here", 2, false, () -> {
			String n = box1.getValue().trim();
			if (n.isEmpty()) {
				Session.status = "Type a warp name first.";
			} else {
				Net.act("warp_set", "", n);
			}
		}).tooltip("Save your current position under this name");

		int rowH = 18;
		int top = cy + 94;
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
		int perPage = 20;
		pagerPages = (FX_IDS.length + perPage - 1) / perPage;
		Session.fxPage = Math.max(0, Math.min(Session.fxPage, pagerPages - 1));
		int start = Session.fxPage * perPage;
		int cols = 5;
		int bw = (cw - GAP * (cols - 1)) / cols;
		int bh = 18;
		for (int i = 0; i < perPage && start + i < FX_IDS.length; i++) {
			String id = FX_IDS[start + i];
			add(cx + (i % cols) * (bw + GAP), cy + (i / cols) * (bh + 2), bw, bh, FX_NAMES[start + i], i % 4, false, () -> {
				Session.fxId = id;
				reopen();
			}).highlighted(Session.fxId.equals(id)).tooltip(id);
		}

		int cy2 = cy + 4 * (bh + 2) + 6;
		String durLabel = DUR_NAMES[Session.fxDur];
		int sw = 18;
		// level stepper
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
		// duration stepper
		int dx = cx + 34 + sw + 24 + sw + 16;
		label("Time", dx, cy2 + 5, 0xFF8A8D96);
		add(dx + 28, cy2, sw, sw, "", 0, false, () -> {
			Session.fxDur = Math.max(0, Session.fxDur - 1);
			reopen();
		}).base("step_minus");
		label(durLabel, dx + 28 + sw + 5, cy2 + 5, 0xFFFFFFFF);
		add(dx + 28 + sw + 5 + 44, cy2, sw, sw, "", 0, false, () -> {
			Session.fxDur = Math.min(DUR_NAMES.length - 1, Session.fxDur + 1);
			reopen();
		}).base("step_plus");
		// target
		String who = Session.fxToSelected && Session.selectedPlayer != null ? shorten(Session.selectedPlayer, 9) : "Me";
		int tw = 80;
		add(cx + cw - tw, cy2, tw, sw, "To: " + who, 0, false, () -> {
			if (Session.selectedPlayer == null) {
				Session.status = "Pick a player on the Players page first.";
			} else {
				Session.fxToSelected = !Session.fxToSelected;
			}
			reopen();
		}).tooltip("Switch between you and the selected player");

		int by = cy2 + sw + 6;
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
		addPager(() -> Session.fxPage, v -> Session.fxPage = v);
	}

	// ───────────────────────── RULES ─────────────────────────

	private void buildRules() {
		label("Game rules - press On or Off. (The current value isn't shown.)", cx + 2, cy + 1, 0xFF8A8D96);
		int colW = (cw - GAP) / 2;
		int btn = 34;
		for (int i = 0; i < RULE_IDS.length; i++) {
			String id = RULE_IDS[i];
			int x = cx + (i % 2) * (colW + GAP);
			int y = cy + 14 + (i / 2) * 24;
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

	// ───────────────────────── STATS / LOG (drawn directly) ─────────────────────────

	private void buildStats() {
		label("Updates every second while this page is open.", cx + 2, cy + 1, 0xFF8A8D96);
	}

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

	// ───────────────────────── rendering ─────────────────────────

	private void drawBar(GuiGraphicsExtractor g, int x, int y, int w, double fraction, int color) {
		g.fill(x, y, x + w, y + 8, 0xFF1A1A20);
		int fill = (int) Math.round(w * Math.max(0.0, Math.min(1.0, fraction)));
		if (fill > 0) {
			g.fill(x + 1, y + 1, x + Math.max(1, fill) - 1 + 1, y + 7, color);
		}
		g.fill(x, y, x + w, y + 1, 0xFF3A3B40);
		g.fill(x, y + 7, x + w, y + 8, 0xFF050507);
	}

	private void drawStats(GuiGraphicsExtractor g) {
		String[] s = Session.stats;
		int x = cx + 4;
		int y = cy + 16;
		if (s == null || s.length < 8) {
			g.text(this.font, Component.literal(Net.available() ? "Waiting for the server..." : "This server doesn't have the mod."), x, y, 0xFF8A8D96, false);
			return;
		}
		double tps = parse(s[0]);
		double mspt = parse(s[1]);
		double used = parse(s[2]);
		double max = Math.max(1, parse(s[3]));
		int barW = Math.min(240, cw - 8);

		g.text(this.font, Component.literal(String.format(Locale.ROOT, "TPS        %.1f / 20", tps)), x, y, 0xFFFFFFFF, true);
		drawBar(g, x, y + 11, barW, tps / 20.0, tps >= 18 ? 0xFFC8102E : tps >= 12 ? 0xFFFFAA00 : 0xFFFF5555);
		y += 28;
		g.text(this.font, Component.literal(String.format(Locale.ROOT, "Tick time  %.1f ms  (limit 50)", mspt)), x, y, 0xFFFFFFFF, true);
		drawBar(g, x, y + 11, barW, mspt / 50.0, mspt < 40 ? 0xFF8A8D96 : 0xFFFF5555);
		y += 28;
		g.text(this.font, Component.literal(String.format(Locale.ROOT, "Memory     %d / %d MB", (long) used, (long) max)), x, y, 0xFFFFFFFF, true);
		drawBar(g, x, y + 11, barW, used / max, used / max < 0.85 ? 0xFF8A8D96 : 0xFFFF5555);
		y += 28;
		g.text(this.font, Component.literal("Players    " + s[4] + " / " + s[5]), x, y, 0xFFFFFFFF, true);
		y += 14;
		g.text(this.font, Component.literal("Entities   " + s[6] + " total"), x, y, 0xFFFFFFFF, true);
		y += 12;
		String worlds = s.length > 7 ? s[7] : "";
		for (String w : worlds.split(",")) {
			if (!w.isBlank() && y < cy + ch - 2) {
				String[] kv = w.split("=", 2);
				g.text(this.font, Component.literal("   " + kv[0] + ": " + (kv.length > 1 ? kv[1] : "?")), x, y, 0xFF9A9CA6, false);
				y += 10;
			}
		}
	}

	private static double parse(String s) {
		try {
			return Double.parseDouble(s);
		} catch (NumberFormatException e) {
			return 0;
		}
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

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		// Screen already draws (and blurs) the background before calling us - calling it again crashes.
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, PANEL, px, py, pw, ph);

		graphics.text(this.font, Component.literal("AERO'S ADMIN PANEL"), px + 12, py + 4, 0xFFFF4D5E, true);
		String right = Session.selectedPlayer != null && Session.page == Session.Page.PLAYERS ? "Player: " + Session.selectedPlayer
				: PAGE_NAMES[Session.page.ordinal()];
		graphics.text(this.font, Component.literal(right), px + pw - 30 - this.font.width(right), py + 4, 0xFFC9CBD2, true);

		if (Session.page == Session.Page.STATS) {
			long now = System.currentTimeMillis();
			if (now - Session.statsAt > 1000) {
				Session.statsAt = now;
				Net.request("req_stats");
			}
		}

		super.extractRenderState(graphics, mouseX, mouseY, delta);

		for (Label l : labels) {
			graphics.text(this.font, Component.literal(l.text()), l.x(), l.y(), l.color(), false);
		}
		if (Session.page == Session.Page.STATS) {
			drawStats(graphics);
		} else if (Session.page == Session.Page.LOG) {
			drawLog(graphics);
		}

		boolean paged = switch (Session.page) {
			case ITEMS, LOG, BANS, TELEPORT, EFFECTS -> true;
			case PLAYERS -> Session.selectedPlayer == null;
			default -> false;
		};
		int statusMax = pw - 28 - SIDEBAR - (paged ? 100 : 0);
		graphics.text(this.font, Component.literal(fit(Session.status, statusMax)), cx + 2, py + ph - 17, 0xFF9A9CA6, false);
		if (paged) {
			int current = switch (Session.page) {
				case ITEMS -> Session.itemPage;
				case PLAYERS -> Session.playerPage;
				case TELEPORT -> Session.warpPage;
				case EFFECTS -> Session.fxPage;
				default -> Session.listPage;
			} + 1;
			String pageText = current + "/" + pagerPages;
			graphics.text(this.font, Component.literal(pageText), px + pw - 14 - 30 - 34 - 4 - this.font.width(pageText),
					py + ph - 17, 0xFFFFFFFF, true);
		}
	}
}
