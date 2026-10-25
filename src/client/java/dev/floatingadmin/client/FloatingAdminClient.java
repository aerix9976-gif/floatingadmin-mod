package dev.floatingadmin.client;

import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;

public final class FloatingAdminClient implements ClientModInitializer {
	public static final String MOD_ID = "floatingadmin";

	private static final KeyMapping.Category CATEGORY =
			KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));

	public static final KeyMapping OPEN = KeyMappingHelper.registerKeyMapping(
			new KeyMapping("key.floatingadmin.open", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, CATEGORY));

	@Override
	public void onInitializeClient() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.player != null && OPEN.consumeClick()) {
				client.gui.setScreen(new AdminScreen());
			}
		});
	}
}
