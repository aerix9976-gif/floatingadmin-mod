package dev.floatingadmin;

import com.mojang.brigadier.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public final class FloatingAdmin implements ModInitializer {
	public static final String MOD_ID = "floatingadmin";
	public static final Logger LOGGER = LoggerFactory.getLogger("FloatingAdmin");

	@Override
	public void onInitialize() {
		Store.load();
		Stats.register();
		PayloadTypeRegistry.serverboundPlay().register(ActionPayload.TYPE, ActionPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(PanelPayload.TYPE, PanelPayload.CODEC);

		// Every click in the GUI arrives here. The server re-checks operator status each time.
		ServerPlayNetworking.registerGlobalReceiver(ActionPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			var server = context.server();
			server.execute(() -> PanelActions.handle(server, player, payload.data()));
		});

		// /adminpanel, /ap, /admin, /fap - only visible and usable by operators
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			for (String name : new String[] {"adminpanel", "ap", "admin", "fap"}) {
				dispatcher.register(Commands.literal(name)
						.requires(src -> PanelActions.isOperator(dispatcher, src))
						.executes(ctx -> {
							ServerPlayer player = ctx.getSource().getPlayer();
							if (player == null) {
								ctx.getSource().sendSystemMessage(Component.literal("The admin panel can only be opened by a player."));
								return 0;
							}
							ServerPlayNetworking.send(player, new PanelPayload("open"));
							return Command.SINGLE_SUCCESS;
						}));
			}
		});

		ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> {
			long left = Store.muteLeft(sender.getUUID());
			if (left > 0) {
				sender.sendSystemMessage(Component.literal(left == Long.MAX_VALUE ? "You are muted."
						: "You are muted for " + Util.duration(left) + "."));
				return false;
			}
			return true;
		});

		LOGGER.info("Aero's Admin Panel loaded - operators can use /adminpanel");
	}
}
