package autism.serverbridge.trade;

import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class TradeManager {
    private static final Map<UUID, TradeSession> sessions = new HashMap<>();
    private static final Map<UUID, TradeRequest> requests = new HashMap<>();

    private TradeManager() {
    }

    public static void register() {
        TradeRequest.register();
    }

    public static int request(ServerPlayerEntity from, ServerPlayerEntity to) {
        if (from.getUuid().equals(to.getUuid())) {
            from.sendMessage(Text.literal("You cannot trade with yourself."), false);
            return 0;
        }
        if (sessions.containsKey(from.getUuid()) || sessions.containsKey(to.getUuid())) {
            from.sendMessage(Text.literal("One of you is already in a trade."), false);
            return 0;
        }
        requests.put(to.getUuid(), new TradeRequest(from.getUuid(), to.getUuid()));
        from.sendMessage(Text.literal("Trade request sent to " + to.getEntityName() + "."), false);
        to.sendMessage(Text.literal(from.getEntityName() + " wants to trade."), false);
        TradePromptGui.open(to, from);
        return 1;
    }

    public static int accept(ServerPlayerEntity player) {
        TradeRequest request = requests.get(player.getUuid());
        if (request == null || request.expired()) {
            requests.remove(player.getUuid());
            player.sendMessage(Text.literal("No trade request to accept."), false);
            return 0;
        }
        ServerPlayerEntity from = online(request.from);
        if (from == null) {
            requests.remove(player.getUuid());
            player.sendMessage(Text.literal("That player is offline."), false);
            return 0;
        }
        if (sessions.containsKey(from.getUuid()) || sessions.containsKey(player.getUuid())) {
            player.sendMessage(Text.literal("One of you is already in a trade."), false);
            return 0;
        }
        requests.remove(player.getUuid());
        TradeSession session = new TradeSession(from.getUuid(), from.getEntityName(), player.getUuid(), player.getEntityName());
        sessions.put(session.a, session);
        sessions.put(session.b, session);
        TradeGui.open(session);
        return 1;
    }

    public static int deny(ServerPlayerEntity player) {
        TradeRequest request = requests.remove(player.getUuid());
        if (request == null) {
            player.sendMessage(Text.literal("No trade request to deny."), false);
            return 0;
        }
        ServerPlayerEntity from = online(request.from);
        if (from != null) {
            from.sendMessage(Text.literal(player.getEntityName() + " denied the trade."), false);
        }
        player.sendMessage(Text.literal("Trade denied."), false);
        return 1;
    }

    public static void disconnect(ServerPlayerEntity player) {
        requests.remove(player.getUuid());
        TradeSession session = sessions.get(player.getUuid());
        if (session != null) {
            cancel(session, player);
        }
    }

    public static void cancel(TradeSession session) {
        cancel(session, null);
    }

    public static void cancel(TradeSession session, ServerPlayerEntity fallback) {
        synchronized (session) {
            if (session.completed || session.closing) {
                return;
            }
            session.closing = true;
            sessions.remove(session.a);
            sessions.remove(session.b);
        }
        giveSide(session, session.a, TradeGui.LEFT, fallback);
        giveSide(session, session.b, TradeGui.RIGHT, fallback);
        tell(session.a, "Trade cancelled. Your items were returned.");
        tell(session.b, "Trade cancelled. Your items were returned.");
        close(session.a);
        close(session.b);
    }

    public static void finish(TradeSession session) {
        synchronized (session) {
            session.completed = true;
            session.closing = true;
            sessions.remove(session.a);
            sessions.remove(session.b);
        }
        close(session.a);
        close(session.b);
    }

    public static ServerPlayerEntity online(UUID id) {
        if (autism.serverbridge.AutismServerBridge.server() == null) {
            return null;
        }
        return autism.serverbridge.AutismServerBridge.server().getPlayerManager().getPlayer(id);
    }

    public static void give(ServerPlayerEntity player, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        if (!player.getInventory().insertStack(stack)) {
            player.dropItem(stack, false);
        }
    }

    private static void giveSide(TradeSession session, UUID id, int[] slots, ServerPlayerEntity fallback) {
        ServerPlayerEntity player = online(id);
        if (player == null && fallback != null && fallback.getUuid().equals(id)) {
            player = fallback;
        }
        for (int slot : slots) {
            ItemStack stack = session.inventory.getStack(slot);
            session.inventory.setStack(slot, ItemStack.EMPTY);
            if (stack.isEmpty()) {
                continue;
            }
            if (player != null) {
                give(player, stack);
            } else {
                autism.serverbridge.AutismServerBridge.LOGGER.info("Could not return " + stack);
            }
        }
    }

    private static void tell(UUID id, String message) {
        ServerPlayerEntity player = online(id);
        if (player != null) {
            player.sendMessage(Text.literal(message), false);
        }
    }

    private static void close(UUID id) {
        ServerPlayerEntity player = online(id);
        if (player != null && player.currentScreenHandler instanceof TradeListener) {
            player.closeHandledScreen();
        }
    }
}
