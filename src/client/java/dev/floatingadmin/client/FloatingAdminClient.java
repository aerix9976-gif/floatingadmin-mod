package dev.floatingadmin.client;

import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

import dev.floatingadmin.PanelPayload;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class FloatingAdminClient implements ClientModInitializer {
	public static final String MOD_ID = "floatingadmin";

	private static final KeyMapping.Category CATEGORY =
			KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));

	public static final KeyMapping OPEN = KeyMappingHelper.registerKeyMapping(
			new KeyMapping("key.floatingadmin.open", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, CATEGORY));

	private static final String SEP = "\u001f";

	@Override
	public void onInitializeClient() {
		// The server tells us when to open the panel (after /adminpanel) and sends the data the pages show.
		ClientPlayNetworking.registerGlobalReceiver(PanelPayload.TYPE, (payload, context) -> {
			String[] parts = payload.data().split(SEP, 2);
			String body = parts.length > 1 ? parts[1] : "";
			switch (parts[0]) {
				case "open" -> {
					Net.requestFor(Session.page);
					context.client().gui.setScreen(new AdminScreen());
				}
				case "status" -> Session.status = body;
				case "stats" -> {
					Session.stats = body.split("\\|", -1);
					Session.statsAt = System.currentTimeMillis();
				}
				case "log" -> Session.log = Session.lines(body);
				case "bans" -> Session.bans = Session.lines(body);
				case "mutes" -> Session.mutes = Session.lines(body);
				case "warps" -> Session.warps = Session.lines(body);
				default -> { }
			}
			if (!parts[0].equals("open") && !parts[0].equals("stats") && !parts[0].equals("status")
					&& Minecraft.getInstance().gui.screen() instanceof AdminScreen screen) {
				screen.dataArrived();
			}
		});

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.player == null || !OPEN.consumeClick()) {
				return;
			}
			if (Net.available()) {
				// the server verifies operator status and answers with "open"
				Net.request("open");
			} else {
				Session.status = "This server doesn't have Aero's Admin Panel installed.";
				client.gui.setScreen(new AdminScreen());
			}
		});
	}
}
