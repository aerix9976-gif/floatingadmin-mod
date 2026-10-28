package dev.floatingadmin.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** UI state that survives page changes (the screen is rebuilt on every page switch). */
final class Session {
	enum Page { DASH, SELF, PLAYERS, ITEMS, FORGE, INVENTORY, KITS, WORLD, ANNOUNCE, FUN, TELEPORT, EFFECTS, RULES, BANS, LOG }

	/** A choice waiting for a recipient. kind: give (a = item id, b = "amount ench"), inv, kit (a = kit name). */
	record Pending(String kind, String a, String b) {}

	static Page page = Page.DASH;
	static String status = "Drag the title bar to move the panel. Esc hides it, K brings it back.";
	static Pending pending = null;

	// window position (offset from the centre) - remembered between openings
	static int offX = 0;
	static int offY = 0;

	// players
	static String selectedPlayer = null;
	static int playerPage = 0;
	static String playerSearch = "";

	// items  (targets: 0 ask, 1 me, 2 selected player)
	static String search = "";
	static int itemPage = 0;
	static int itemCat = 0;
	static int amount = 1;
	static int itemTarget = 1;
	static int itemEnch = 0; // 0 off, 1 best, 2 overpowered

	// forge
	static int forgeTier = 0;
	static int forgeTarget = 0;
	static int forgeMode = 1;

	// inventory viewer
	static String invName = null;
	static String invMode = "inv";
	static String invOp = "copy";
	static Map<Integer, String[]> invSlots = new HashMap<>();

	// kits
	static String kitName = "";
	static int kitTarget = 0;
	static int kitPage = 0;
	static List<String> kits = new ArrayList<>();

	// announce
	static String announceText = "";
	static String announceColor = "white";

	// teleport
	static String tpX = "";
	static String tpY = "";
	static String tpZ = "";
	static String warpName = "";
	static int warpPage = 0;

	// effects
	static String fxId = "speed";
	static int fxLevel = 2;
	static int fxDur = 2;
	static boolean fxToSelected = false;

	// bans / log (shared list paging)
	static int bansTab = 0;
	static int listPage = 0;

	// data pushed by the server
	static String[] stats = null;
	static long statsAt = 0;
	static long logAt = 0;
	static List<String> log = new ArrayList<>();
	static List<String> bans = new ArrayList<>();
	static List<String> mutes = new ArrayList<>();
	static List<String> warps = new ArrayList<>();

	private Session() {}

	static List<String> lines(String body) {
		List<String> out = new ArrayList<>();
		if (body == null || body.isEmpty()) {
			return out;
		}
		for (String l : body.split("\n")) {
			if (!l.isBlank()) {
				out.add(l);
			}
		}
		return out;
	}

	static String targetLabel(int t) {
		return t == 0 ? "Ask" : t == 1 ? "Me" : "Selected";
	}
}
