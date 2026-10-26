package dev.floatingadmin.client;

import dev.floatingadmin.ActionPayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** Sends actions to the server. The server checks operator status and decides what to run. */
final class Net {
	private static final String SEP = "\u001f";

	private Net() {}

	static void act(String action, String target, String arg) {
		if (!ClientPlayNetworking.canSend(ActionPayload.TYPE)) {
			Session.status = "This server doesn't have Floating Admin installed.";
			return;
		}
		ClientPlayNetworking.send(new ActionPayload(action + SEP + target + SEP + arg));
		Session.status = "Sent: " + action.replace('_', ' ');
	}
}
