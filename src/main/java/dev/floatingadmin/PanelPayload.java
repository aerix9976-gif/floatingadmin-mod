package dev.floatingadmin;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server -> client: "open" (show the panel) or "status \u001f text" (footer message). */
public record PanelPayload(String data) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<PanelPayload> TYPE =
			new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(FloatingAdmin.MOD_ID, "panel"));
	public static final StreamCodec<FriendlyByteBuf, PanelPayload> CODEC =
			ByteBufCodecs.STRING_UTF8.map(PanelPayload::new, PanelPayload::data).cast();

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
