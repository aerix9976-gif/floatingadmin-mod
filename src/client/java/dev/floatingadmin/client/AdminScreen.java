package dev.floatingadmin.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** The whole panel: one screen, five pages, drawn with the custom neon sprites. */
final class AdminScreen extends Screen {
	private static final Identifier PANEL = Identifier.fromNamespaceAndPath(FloatingAdminClient.MOD_ID, "panel");
	private static final int GAP = 4;

	private record Act(String label, int theme, boolean confirm, String tip, String action, String target, String arg) {}

	private int px, py, pw, ph;
	private int cx, cy, cw, ch;
	private int pagerPages = 1;
	private EditBox searchBox;
	private EditBox broadcastBox;

	AdminScreen() {
		super(Component.literal("Floating Admin"));
	}

	// ───────────────────────── layout ─────────────────────────

	@Override
	protected void init() {
		pw = Math.min(340, this.width - 8);
		ph = Math.min(216, this.height - 8);
		px = (this.width - pw) / 2;
		py = (this.height - ph) / 2;
		cx = px + 14;
		cw = pw - 28;
		cy = py + 58;
		ch = ph - 58 - 22;

		buildTabs();
		switch (Session.page) {
			case HOME -> buildHome();
			case PLAYERS -> buildPlayers();
			case ITEMS -> buildItems();
			case WORLD -> buildWorld();
			case FUN -> buildFun();
		}
	}

	private void buildTabs() {
		Session.Page[] pages = Session.Page.values();
		String[] names = {"Self", "Players", "Items", "World", "Fun"};
		String[] sprites = {"tab_self", "tab_players", "tab_items", "tab_world", "tab_fun"};
		int w = (cw - GAP * (pages.length - 1)) / pages.length;
		for (int i = 0; i < pages.length; i++) {
			Session.Page p = pages[i];
			this.addRenderableWidget(new FancyButton(cx + i * (w + GAP), py + 32, w, 20, names[i], i % 4, false,
					() -> switchPage(p)).sprite(sprites[i]).tooltip(names[i]).highlighted(Session.page == p));
		}
	}

	private void switchPage(Session.Page page) {
		Session.page = page;
		reopen();
	}

	private void reopen() {
		this.minecraft.gui.setScreen(new AdminScreen());
	}

	private FancyButton add(int x, int y, int w, int h, String label, int theme, boolean confirm, Runnable r) {
		return this.addRenderableWidget(new FancyButton(x, y, w, h, label, theme, confirm, r));
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

	// ───────────────────────── pages ─────────────────────────

	private void buildHome() {
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
			List<String> names = onlineNames();
			int perPage = 12;
			pagerPages = Math.max(1, (names.size() + perPage - 1) / perPage);
			Session.playerPage = Math.max(0, Math.min(Session.playerPage, pagerPages - 1));
			int start = Session.playerPage * perPage;
			int cols = 3;
			int rows = 4;
			int bw = (cw - GAP * (cols - 1)) / cols;
			int bh = Math.min(26, (ch - GAP * (rows - 1)) / rows);
			for (int i = 0; i < perPage && start + i < names.size(); i++) {
				String n = names.get(start + i);
				add(cx + (i % cols) * (bw + GAP), cy + (i / cols) * (bh + GAP), bw, bh, shorten(n, 13), (i % cols) + 1, false, () -> {
					Session.selectedPlayer = n;
					reopen();
				}).tooltip("Open controls for " + n);
			}
			addPager(() -> Session.playerPage, v -> Session.playerPage = v);
			return;
		}
		String p = sel;
		List<Act> acts = List.of(
				act("Heal", "heal", p), act("Feed", "feed", p), danger("Kill", "kill", p), act("TP To", "tp_to", p), act("Bring", "bring", p),
				act("Survival", "gm_survival", p), act("Creative", "gm_creative", p), act("Adventure", "gm_adventure", p),
				act("Spectator", "gm_spectator", p), act("Spy", "spy", p),
				act("Freeze", "freeze", p), act("Unfreeze", "unfreeze", p), act("Mute", "mute", p), act("Unmute", "unmute", p),
				danger("Clear Inv", "clear_inv", p),
				danger("Kick", "kick", p), danger("Ban", "ban", p), act("Pardon", "pardon", p), danger("Op", "op", p), danger("Deop", "deop", p),
				act("Smite", "smite", p), act("Clear FX", "clear_fx", p), act("+10 Lvls", "levels", p, "10"));
		grid(acts, 5, 5, 0);
		int bw = (cw - GAP * 4) / 5;
		int bh = Math.min(26, (ch - GAP * 4) / 5);
		int y = cy + 4 * (bh + GAP);
		add(cx + 3 * (bw + GAP), y, bw, bh, "Give Item", 2, false, () -> {
			Session.giveToSelected = true;
			switchPage(Session.Page.ITEMS);
		}).tooltip("Open the item giver targeting " + p);
		add(cx + 4 * (bw + GAP), y, bw, bh, "Back", 3, false, () -> {
			Session.selectedPlayer = null;
			Session.giveToSelected = false;
			reopen();
		});
	}

	private void buildItems() {
		int fieldH = 18;
		int amountW = 28;
		int targetW = 84;
		int findW = 34;
		int editW = cw - (4 * amountW + 3 * 2) - targetW - findW - 3 * GAP;

		searchBox = new EditBox(this.font, cx, cy, editW, fieldH, Component.literal("Search items"));
		searchBox.setMaxLength(40);
		searchBox.setValue(Session.search);
		searchBox.setResponder(v -> Session.search = v);
		this.addRenderableWidget(searchBox);
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
			}).highlighted(Session.amount == a);
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

		// grid of items
		List<ItemList.Entry> items = ItemList.filter(Session.search);
		int cols = 8;
		int rows = 3;
		int perPage = cols * rows;
		pagerPages = Math.max(1, (items.size() + perPage - 1) / perPage);
		Session.itemPage = Math.max(0, Math.min(Session.itemPage, pagerPages - 1));
		int top = cy + fieldH + GAP + 2;
		int bw = (cw - GAP * (cols - 1)) / cols;
		int bh = Math.min(34, (cy + ch - top - GAP * (rows - 1)) / rows);
		int start = Session.itemPage * perPage;
		for (int i = 0; i < perPage && start + i < items.size(); i++) {
			ItemList.Entry e = items.get(start + i);
			FancyButton b = add(cx + (i % cols) * (bw + GAP), top + (i / cols) * (bh + GAP), bw, bh, "", i % 4, false, () -> {
				String who = Session.giveToSelected && Session.selectedPlayer != null ? Session.selectedPlayer : "@s";
				Net.act("give", who, e.id() + " " + Session.amount);
			});
			b.icon(ItemList.stack(e)).tooltip(e.pretty() + "  (" + e.id() + ")");
		}
		addPager(() -> Session.itemPage, v -> Session.itemPage = v);
	}

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

	private void buildFun() {
		int fieldH = 18;
		int btnW = 52;
		int editW = cw - 3 * btnW - 3 * GAP;
		broadcastBox = new EditBox(this.font, cx, cy, editW, fieldH, Component.literal("Announcement"));
		broadcastBox.setMaxLength(100);
		broadcastBox.setValue(Session.broadcast);
		broadcastBox.setResponder(v -> Session.broadcast = v);
		this.addRenderableWidget(broadcastBox);
		String[] labels = {"Chat", "Title", "Bar"};
		String[] actions = {"announce", "title", "actionbar"};
		String[] tips = {"Send to everyone's chat", "Big title on everyone's screen", "Message above everyone's hotbar"};
		for (int i = 0; i < 3; i++) {
			String action = actions[i];
			add(cx + editW + GAP + i * (btnW + GAP), cy, btnW, fieldH, labels[i], i + 1, false, () -> {
				String m = broadcastBox.getValue().trim();
				if (!m.isEmpty()) {
					Net.act(action, "", m);
				}
			}).tooltip(tips[i]);
		}

		List<Act> acts = List.of(
				act("Lightning", "smite", "@s"), act("Cookies", "cookies", "@a"), act("Heal All", "heal", "@a"),
				act("Feed All", "feed", "@a"), act("Rocket", "rocket", "@s"),
				act("Random TP", "random_tp", "@s"), act("Glow All", "effect", "@a", "glowing 0 120"),
				act("No FX All", "clear_fx", "@a"), danger("Gather", "gather", "@s"), danger("All Creat", "gm_creative", "@a"),
				danger("All Surv", "gm_survival", "@a"), act("List On", "wl_on", ""), act("List Off", "wl_off", ""),
				act("Summon Pig", "summon", "@s", "pig"), danger("Stop", "stop", ""));
		grid(acts, 5, 3, fieldH + GAP + 2);
	}

	// ───────────────────────── helpers ─────────────────────────

	private void addPager(java.util.function.IntSupplier get, java.util.function.IntConsumer set) {
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

	// ───────────────────────── rendering ─────────────────────────

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		this.extractBackground(graphics, mouseX, mouseY, delta);
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, PANEL, px, py, pw, ph);

		graphics.text(this.font, Component.literal("FLOATING ADMIN"), px + 16, py + 12, 0xFFB9A6FF, true);
		String right = Session.selectedPlayer != null && Session.page == Session.Page.PLAYERS
				? "Player: " + Session.selectedPlayer
				: Session.page.name().substring(0, 1) + Session.page.name().substring(1).toLowerCase(Locale.ROOT);
		graphics.text(this.font, Component.literal(right), px + pw - 16 - this.font.width(right), py + 12, 0xFF7FE9FF, true);

		super.extractRenderState(graphics, mouseX, mouseY, delta);

		boolean paged = (Session.page == Session.Page.ITEMS) || (Session.page == Session.Page.PLAYERS && Session.selectedPlayer == null);
		int statusMax = pw - 28 - (paged ? 100 : 0);
		graphics.text(this.font, Component.literal(fit(Session.status, statusMax)), px + 16, py + ph - 17, 0xFF9AA0C8, false);
		if (paged) {
			int current = (Session.page == Session.Page.ITEMS ? Session.itemPage : Session.playerPage) + 1;
			String pageText = current + "/" + pagerPages;
			graphics.text(this.font, Component.literal(pageText), px + pw - 14 - 30 - 34 - 4 - this.font.width(pageText),
					py + ph - 17, 0xFFFFFFFF, true);
		}
	}
}
