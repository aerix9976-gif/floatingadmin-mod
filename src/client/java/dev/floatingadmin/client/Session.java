package dev.floatingadmin.client;

/** UI state that survives page changes (the screen is rebuilt on every page switch). */
final class Session {
	enum Page { HOME, PLAYERS, ITEMS, WORLD, FUN }

	static Page page = Page.HOME;
	static String selectedPlayer = null;
	static int playerPage = 0;
	static String search = "";
	static int itemPage = 0;
	static int itemCat = 0;
	static String playerSearch = "";
	static int amount = 1;
	static boolean giveToSelected = false;
	static String broadcast = "";
	static String status = "Press K anytime to open. Replies from the server show in chat.";

	private Session() {}
}
