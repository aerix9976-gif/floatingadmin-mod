package dev.floatingadmin.client;

import dev.floatingadmin.ActionPayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** Sends actions to the server. The server checks operator status and decides what to run. */
final class Net {
	private static final String SEP = "\u001f";

	private Net() {}

	static boolean available() {
		return ClientPlayNetworking.canSend(ActionPayload.TYPE);
	}

	/** An action that does something. Shows "Sent" in the footer. */
	static void act(String action, String target, String arg) {
		if (!available()) {
			Session.status = "This server doesn't have Aero's Admin Panel installed.";
			return;
		}
		ClientPlayNetworking.send(new ActionPayload(action + SEP + target + SEP + arg));
		Session.status = "Sent: " + action.replace('_', ' ');
	}

	/** A data request. Silent, does not touch the footer. */
	static void request(String what) {
		if (available()) {
			ClientPlayNetworking.send(new ActionPayload(what + SEP + SEP));
		}
	}

	/** A data request with a target and argument, e.g. a player's inventory. Silent. */
	static void request(String what, String target, String arg) {
		if (available()) {
			ClientPlayNetworking.send(new ActionPayload(what + SEP + target + SEP + arg));
		}
	}

	/** Asks the server for whatever the given page displays. */
	static void requestFor(Session.Page page) {
		switch (page) {
			case DASH -> {
				request("req_stats");
				request("req_log");
			}
			case LOG -> request("req_log");
			case INVENTORY -> {
				if (Session.invName != null) {
					request("req_inv", Session.invName, Session.invMode);
				}
			}
			case KITS -> request("req_kits");
			case BANS -> {
				request("req_mutes");
				request("req_bans");
			}
			case TELEPORT -> request("req_warps");
			default -> { }
		}
	}
}
