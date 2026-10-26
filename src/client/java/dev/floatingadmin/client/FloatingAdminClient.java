package dev.floatingadmin.client;

import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

import dev.floatingadmin.ActionPayload;
import dev.floatingadmin.PanelPayload;

import net.minecraft.client.KeyMapping;
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
		// The server tells us when to open the panel (after /adminpanel) and what to show in the footer.
		ClientPlayNetworking.registerGlobalReceiver(PanelPayload.TYPE, (payload, context) -> {
			String[] parts = payload.data().split(SEP, 2);
			if (parts[0].equals("open")) {
				context.client().gui.setScreen(new AdminScreen());
			} else if (parts[0].equals("status") && parts.length > 1) {
				Session.status = parts[1];
			}
		});

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.player == null || !OPEN.consumeClick()) {
				return;
			}
			if (ClientPlayNetworking.canSend(ActionPayload.TYPE)) {
				// the server verifies operator status and answers with "open"
				ClientPlayNetworking.send(new ActionPayload("open" + SEP + SEP));
			} else {
				Session.status = "This server doesn't have Floating Admin installed.";
				client.gui.setScreen(new AdminScreen());
			}
		});
	}
}
