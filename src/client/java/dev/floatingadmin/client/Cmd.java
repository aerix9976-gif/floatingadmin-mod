package dev.floatingadmin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;

/** Everything the panel does is a normal server command, so it works on any server where you are operator. */
final class Cmd {
	private Cmd() {}

	static void run(String command) {
		ClientPacketListener connection = Minecraft.getInstance().getConnection();
		if (connection == null) {
			return;
		}
		connection.sendCommand(command);
		Session.status = "Sent: /" + (command.length() > 60 ? command.substring(0, 57) + "..." : command);
	}

	/** Escapes text for use inside a JSON string. */
	static String json(String s) {
		return s.replace("\\", "\\\\").replace("\"", "\\\"");
	}
}
