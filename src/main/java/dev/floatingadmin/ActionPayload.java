package dev.floatingadmin;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client -> server: "action \u001f target \u001f argument". The server decides what that means. */
public record ActionPayload(String data) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<ActionPayload> TYPE =
			new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(FloatingAdmin.MOD_ID, "action"));
	public static final StreamCodec<FriendlyByteBuf, ActionPayload> CODEC =
			ByteBufCodecs.STRING_UTF8.map(ActionPayload::new, ActionPayload::data).cast();

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
