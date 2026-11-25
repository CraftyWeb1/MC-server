package autism.serverbridge;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

public final class AutismNetwork {
    public static final Identifier OPEN_MENU = new Identifier("autismserverbridge", "open_menu");

    private AutismNetwork() {
    }

    public static void registerServer() {
        ServerPlayNetworking.registerGlobalReceiver(OPEN_MENU, (server, player, handler, buf, responseSender) ->
                server.execute(() -> openMenu(player)));
    }

    private static void openMenu(ServerPlayerEntity player) {
        if (player.getServer() == null) {
            return;
        }
        player.getServer().getCommandManager().executeWithPrefix(
                player.getCommandSource().withLevel(2),
                "/gui open autism_menu");
    }
}
