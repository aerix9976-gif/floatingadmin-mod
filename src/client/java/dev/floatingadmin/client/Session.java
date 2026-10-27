package dev.floatingadmin.client;

import java.util.ArrayList;
import java.util.List;

/** UI state that survives page changes (the screen is rebuilt on every page switch). */
final class Session {
	enum Page { SELF, PLAYERS, ITEMS, WORLD, ANNOUNCE, FUN, TELEPORT, EFFECTS, RULES, BANS, STATS, LOG }

	static Page page = Page.SELF;
	static String status = "Press K anytime to open. Replies from the server show in chat.";

	// players
	static String selectedPlayer = null;
	static int playerPage = 0;
	static String playerSearch = "";

	// items
	static String search = "";
	static int itemPage = 0;
	static int itemCat = 0;
	static int amount = 1;
	static boolean giveToSelected = false;

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
	static int fxPage = 0;
	static boolean fxToSelected = false;

	// bans / log (shared list paging)
	static int bansTab = 0;
	static int listPage = 0;

	// data pushed by the server
	static String[] stats = null;
	static long statsAt = 0;
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
}
