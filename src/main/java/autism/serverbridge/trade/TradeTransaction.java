package autism.serverbridge.trade;

import autism.serverbridge.AutismServerBridge;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class TradeTransaction {
    private TradeTransaction() {
    }

    public static void execute(TradeSession session) {
        synchronized (session) {
            if (session.completed || session.closing || !session.confirmA || !session.confirmB) {
                return;
            }
            ServerPlayerEntity a = TradeManager.online(session.a);
            ServerPlayerEntity b = TradeManager.online(session.b);
            if (a == null || b == null) {
                fail(session, "One of you left.");
                return;
            }
            List<ItemStack> toB = copies(session, TradeGui.LEFT);
            List<ItemStack> toA = copies(session, TradeGui.RIGHT);
            if (!fits(b, toB) || !fits(a, toA)) {
                fail(session, "Someone's inventory is full.");
                return;
            }
            if (shortOf(session.a, "usd", session.yapA) || shortOf(session.a, "coin", session.brainA)
                    || shortOf(session.b, "usd", session.yapB) || shortOf(session.b, "coin", session.brainB)) {
                fail(session, "Someone no longer has enough money.");
                return;
            }
            List<Change> done = new ArrayList<>();
            boolean paid = pay(done, session.a, "usd", -session.yapA)
                    && pay(done, session.a, "coin", -session.brainA)
                    && pay(done, session.b, "usd", -session.yapB)
                    && pay(done, session.b, "coin", -session.brainB)
                    && pay(done, session.b, "usd", session.yapA)
                    && pay(done, session.b, "coin", session.brainA)
                    && pay(done, session.a, "usd", session.yapB)
                    && pay(done, session.a, "coin", session.brainB);
            if (!paid) {
                for (int i = done.size() - 1; i >= 0; i--) {
                    Change change = done.get(i);
                    AutismServerBridge.changeBalance(change.id, change.currency, -change.delta);
                }
                fail(session, "The economy mod refused the payment. Nothing was taken.");
                return;
            }
            clear(session, TradeGui.LEFT);
            clear(session, TradeGui.RIGHT);
            for (ItemStack stack : toB) {
                TradeManager.give(b, stack);
            }
            for (ItemStack stack : toA) {
                TradeManager.give(a, stack);
            }
            TradeHistory.record(session, toB, toA);
            a.sendMessage(Text.literal("Trade complete."), false);
            b.sendMessage(Text.literal("Trade complete."), false);
            TradeManager.finish(session);
        }
    }

    private static boolean shortOf(UUID player, String currency, double amount) {
        return amount > 0.0 && AutismServerBridge.rawBalance(player, currency) + 0.001 < amount;
    }

    private static boolean pay(List<Change> done, UUID player, String currency, double delta) {
        if (Math.abs(delta) < 0.0000001) {
            return true;
        }
        if (!AutismServerBridge.changeBalance(player, currency, delta)) {
            return false;
        }
        done.add(new Change(player, currency, delta));
        return true;
    }

    private static void fail(TradeSession session, String message) {
        session.resetConfirm();
        TradeGui.draw(session);
        ServerPlayerEntity a = TradeManager.online(session.a);
        ServerPlayerEntity b = TradeManager.online(session.b);
        if (a != null) {
            a.sendMessage(Text.literal(message), false);
        }
        if (b != null) {
            b.sendMessage(Text.literal(message), false);
        }
    }

    private static List<ItemStack> copies(TradeSession session, int[] slots) {
        List<ItemStack> stacks = new ArrayList<>();
        for (int slot : slots) {
            ItemStack stack = session.inventory.getStack(slot);
            if (!stack.isEmpty()) {
                stacks.add(stack.copy());
            }
        }
        return stacks;
    }

    private static void clear(TradeSession session, int[] slots) {
        for (int slot : slots) {
            session.inventory.setStack(slot, ItemStack.EMPTY);
        }
    }

    private static boolean fits(ServerPlayerEntity player, List<ItemStack> stacks) {
        int empty = 0;
        for (int slot = 0; slot < 36; slot++) {
            if (player.getInventory().getStack(slot).isEmpty()) {
                empty++;
            }
        }
        int needed = 0;
        for (ItemStack offer : stacks) {
            int remain = offer.getCount();
            for (int slot = 0; slot < 36 && remain > 0; slot++) {
                ItemStack have = player.getInventory().getStack(slot);
                if (!have.isEmpty() && ItemStack.canCombine(have, offer)) {
                    remain -= Math.min(remain, have.getMaxCount() - have.getCount());
                }
            }
            if (remain > 0) {
                needed += (remain + offer.getMaxCount() - 1) / offer.getMaxCount();
            }
        }
        return needed <= empty;
    }

    private record Change(UUID id, String currency, double delta) {
    }
}
