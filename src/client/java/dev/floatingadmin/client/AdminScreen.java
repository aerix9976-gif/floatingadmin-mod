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

	private record Act(String label, int theme, boolean confirm, String tip, String... commands) {}

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
					a.theme() >= 0 ? a.theme() : (col % 4), a.confirm(), () -> {
						for (String c : a.commands()) {
							Cmd.run(c);
						}
					});
			if (a.tip() != null) {
				b.tooltip(a.tip());
			}
		}
	}

	private static Act act(String label, String... commands) {
		return new Act(label, -1, false, null, commands);
	}

	private static Act danger(String label, String... commands) {
		return new Act(label, 0, true, "Click twice to confirm", commands);
	}

	// ───────────────────────── pages ─────────────────────────

	private void buildHome() {
		List<Act> acts = List.of(
				act("Survival", "gamemode survival @s"),
				act("Creative", "gamemode creative @s"),
				act("Adventure", "gamemode adventure @s"),
				act("Spectator", "gamemode spectator @s"),
				act("Heal", "effect give @s instant_health 1 10 true"),
				act("Feed", "effect give @s saturation 1 20 true"),
				act("God On", "effect give @s resistance infinite 4 true", "effect give @s regeneration infinite 4 true"),
				act("God Off", "effect clear @s resistance", "effect clear @s regeneration"),
				act("Vanish On", "effect give @s invisibility infinite 0 true"),
				act("Vanish Off", "effect clear @s invisibility"),
				act("Speed", "effect give @s speed infinite 3 true"),
				act("Night Vis", "effect give @s night_vision infinite 0 true"),
				act("Jump", "effect give @s jump_boost infinite 4 true"),
				act("Haste", "effect give @s haste infinite 4 true"),
				act("Clear FX", "effect clear @s"),
				act("Set Day", "time set day"));
		grid(acts, 4, 4, 0);
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
				act("Heal", "effect give " + p + " instant_health 1 10 true"),
				act("Feed", "effect give " + p + " saturation 1 20 true"),
				danger("Kill", "kill " + p),
				act("TP To", "tp @s " + p),
				act("Bring", "tp " + p + " @s"),
				act("Survival", "gamemode survival " + p),
				act("Creative", "gamemode creative " + p),
				act("Spectator", "gamemode spectator " + p),
				act("Freeze", "effect give " + p + " slowness infinite 255 true", "effect give " + p + " jump_boost infinite 128 true"),
				act("Unfreeze", "effect clear " + p + " slowness", "effect clear " + p + " jump_boost"),
				danger("Clear Inv", "clear " + p),
				danger("Kick", "kick " + p + " Kicked by staff"),
				danger("Ban", "ban " + p + " Banned by staff"),
				act("Pardon", "pardon " + p),
				danger("Op", "op " + p),
				danger("Deop", "deop " + p),
				act("Glow", "effect give " + p + " glowing 60 0 true"),
				act("Clear FX", "effect clear " + p));
		// "Give Item" and "Back" need screen actions rather than commands, added below
		grid(acts, 4, 5, 0);
		int bw = (cw - GAP * 3) / 4;
		int bh = Math.min(26, (ch - GAP * 4) / 5);
		int y = cy + 4 * (bh + GAP);
		add(cx + 2 * (bw + GAP), y, bw, bh, "Give Item", 2, false, () -> {
			Session.giveToSelected = true;
			switchPage(Session.Page.ITEMS);
		}).tooltip("Open the item giver targeting " + p);
		add(cx + 3 * (bw + GAP), y, bw, bh, "Back", 3, false, () -> {
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
				Cmd.run("give " + who + " " + e.id() + " " + Session.amount);
			});
			b.icon(ItemList.stack(e)).tooltip(e.pretty() + "  (" + e.id() + ")");
		}
		addPager(() -> Session.itemPage, v -> Session.itemPage = v);
	}

	private void buildWorld() {
		List<Act> acts = List.of(
				act("Clear", "weather clear"),
				act("Rain", "weather rain"),
				act("Thunder", "weather thunder"),
				act("Day", "time set day"),
				act("Noon", "time set noon"),
				act("Night", "time set night"),
				act("Midnight", "time set midnight"),
				act("Sunrise", "time set 23000"),
				act("Peaceful", "difficulty peaceful"),
				act("Easy", "difficulty easy"),
				act("Normal", "difficulty normal"),
				act("Hard", "difficulty hard"),
				act("Set Spawn", "setworldspawn"),
				act("Kill Items", "kill @e[type=item]"),
				act("Kill XP", "kill @e[type=experience_orb]"),
				act("Save", "save-all"));
		grid(acts, 4, 4, 0);
	}

	private void buildFun() {
		int fieldH = 18;
		int btnW = 56;
		int editW = cw - 2 * btnW - 2 * GAP;
		broadcastBox = new EditBox(this.font, cx, cy, editW, fieldH, Component.literal("Announcement"));
		broadcastBox.setMaxLength(100);
		broadcastBox.setValue(Session.broadcast);
		broadcastBox.setResponder(v -> Session.broadcast = v);
		this.addRenderableWidget(broadcastBox);
		add(cx + editW + GAP, cy, btnW, fieldH, "Chat", 2, false, () -> {
			String m = broadcastBox.getValue().trim();
			if (!m.isEmpty()) {
				Cmd.run("tellraw @a [{\"text\":\"[Announcement] \",\"color\":\"aqua\"},{\"text\":\"" + Cmd.json(m) + "\",\"color\":\"white\"}]");
			}
		}).tooltip("Send to everyone's chat");
		add(cx + editW + GAP + btnW + GAP, cy, btnW, fieldH, "Title", 3, false, () -> {
			String m = broadcastBox.getValue().trim();
			if (!m.isEmpty()) {
				Cmd.run("title @a title {\"text\":\"" + Cmd.json(m) + "\",\"color\":\"aqua\"}");
			}
		}).tooltip("Show as a big title on everyone's screen");

		List<Act> acts = List.of(
				act("Lightning", "summon lightning_bolt ~ ~ ~"),
				act("Cookies All", "give @a cookie 8"),
				act("Heal All", "effect give @a instant_health 1 10 true"),
				act("Feed All", "effect give @a saturation 1 20 true"),
				act("Launch Me", "effect give @s levitation 2 40 true", "effect give @s slow_falling 25 0 true"),
				act("Rocket", "summon firework_rocket ~ ~1 ~"),
				act("Random TP", "spreadplayers ~ ~ 10 1000 false @s"),
				act("Glow All", "effect give @a glowing 120 0 true"),
				danger("All Creative", "gamemode creative @a"),
				danger("All Survival", "gamemode survival @a"),
				danger("Gather All", "tp @a @s"),
				act("Clear FX All", "effect clear @a"));
		grid(acts, 4, 3, fieldH + GAP + 2);
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
