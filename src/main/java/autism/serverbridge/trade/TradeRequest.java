package autism.serverbridge.trade;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.UUID;

public final class TradeRequest {
    public final UUID from;
    public final UUID to;
    public final long expiresAt;

    public TradeRequest(UUID from, UUID to) {
        this.from = from;
        this.to = to;
        this.expiresAt = System.currentTimeMillis() + 60000L;
    }

    public boolean expired() {
        return System.currentTimeMillis() > expiresAt;
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("autismtrade")
                    .requires(source -> source.getEntity() instanceof ServerPlayerEntity)
                    .executes(ctx -> {
                        PlayerListGui.open(ctx.getSource().getPlayer());
                        return 1;
                    }));
            dispatcher.register(CommandManager.literal("autismshop")
                    .requires(source -> source.getEntity() instanceof ServerPlayerEntity)
                    .executes(ctx -> {
                        ShopGui.open(ctx.getSource().getPlayer());
                        return 1;
                    }));
        });
    }
}
