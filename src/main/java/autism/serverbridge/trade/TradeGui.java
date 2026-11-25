package autism.serverbridge.trade;

import autism.serverbridge.AutismServerBridge;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.Locale;
import java.util.UUID;

public final class TradeGui {
    public static final int[] LEFT = {10, 11, 12, 19, 20, 21};
    public static final int[] RIGHT = {14, 15, 16, 23, 24, 25};
    public static final int YAP_A = 28;
    public static final int BRAIN_A = 29;
    public static final int YAP_B = 33;
    public static final int BRAIN_B = 34;
    public static final int CONFIRM_A = 37;
    public static final int CONFIRM_B = 43;
    public static final int STATUS = 40;
    public static final int CANCEL = 49;

    private TradeGui() {
    }

    public static void open(TradeSession session) {
        session.inventory = new TradeInventory();
        draw(session);
        Text title = Text.literal("Bank of Autism | Trade");
        ServerPlayerEntity a = TradeManager.online(session.a);
        ServerPlayerEntity b = TradeManager.online(session.b);
        if (a == null || b == null) {
            TradeManager.cancel(session);
            return;
        }
        a.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                (syncId, inventory, player) -> new TradeListener(syncId, inventory, session), title));
        b.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                (syncId, inventory, player) -> new TradeListener(syncId, inventory, session), title));
    }

    public static void draw(TradeSession session) {
        for (int slot = 0; slot < 54; slot++) {
            if (isItemSlot(slot)) {
                continue;
            }
            session.inventory.setStack(slot, icon(session, slot));
        }
        session.inventory.markDirty();
    }

    public static boolean isItemSlot(int slot) {
        return contains(LEFT, slot) || contains(RIGHT, slot);
    }

    public static boolean owns(TradeSession session, UUID player, int slot) {
        boolean left = contains(LEFT, slot) || slot == YAP_A || slot == BRAIN_A || slot == CONFIRM_A;
        boolean right = contains(RIGHT, slot) || slot == YAP_B || slot == BRAIN_B || slot == CONFIRM_B;
        if (left) {
            return session.a.equals(player);
        }
        if (right) {
            return session.b.equals(player);
        }
        return false;
    }

    public static void adjust(TradeSession session, UUID player, int slot, int button, SlotActionType action) {
        if (!owns(session, player, slot)) {
            return;
        }
        double delta = action == SlotActionType.QUICK_MOVE ? (button == 1 ? -100.0 : 100.0) : (button == 1 ? -10.0 : 10.0);
        boolean yap = slot == YAP_A || slot == YAP_B;
        boolean sideA = session.a.equals(player);
        String currency = yap ? "usd" : "coin";
        double balance = AutismServerBridge.rawBalance(player, currency);
        double current = yap ? (sideA ? session.yapA : session.yapB) : (sideA ? session.brainA : session.brainB);
        double next = Math.max(0.0, current + delta);
        if (next > balance) {
            next = Math.floor(balance * 10.0) / 10.0;
        }
        if (yap) {
            if (sideA) {
                session.yapA = next;
            } else {
                session.yapB = next;
            }
        } else if (sideA) {
            session.brainA = next;
        } else {
            session.brainB = next;
        }
        session.resetConfirm();
        draw(session);
    }

    public static void toggleConfirm(TradeSession session, UUID player, int slot) {
        if (slot == CONFIRM_A && session.a.equals(player)) {
            session.confirmA = !session.confirmA;
        } else if (slot == CONFIRM_B && session.b.equals(player)) {
            session.confirmB = !session.confirmB;
        } else {
            return;
        }
        draw(session);
        if (session.confirmA && session.confirmB) {
            TradeTransaction.execute(session);
        }
    }

    private static ItemStack icon(TradeSession session, int slot) {
        if (slot == 1) {
            return named(Items.NAME_TAG, session.nameA + " offers");
        }
        if (slot == 7) {
            return named(Items.NAME_TAG, session.nameB + " offers");
        }
        if (slot == YAP_A) {
            return named(Items.EMERALD, "YapCoins " + money(session.yapA) + " YAP");
        }
        if (slot == BRAIN_A) {
            return named(Items.AMETHYST_SHARD, "Braincells " + money(session.brainA) + " BC");
        }
        if (slot == YAP_B) {
            return named(Items.EMERALD, "YapCoins " + money(session.yapB) + " YAP");
        }
        if (slot == BRAIN_B) {
            return named(Items.AMETHYST_SHARD, "Braincells " + money(session.brainB) + " BC");
        }
        if (slot == CONFIRM_A) {
            return named(session.confirmA ? Items.LIME_CONCRETE : Items.YELLOW_CONCRETE, session.nameA + (session.confirmA ? " confirmed" : " confirm"));
        }
        if (slot == CONFIRM_B) {
            return named(session.confirmB ? Items.LIME_CONCRETE : Items.YELLOW_CONCRETE, session.nameB + (session.confirmB ? " confirmed" : " confirm"));
        }
        if (slot == STATUS) {
            return named(Items.PAPER, session.confirmA && session.confirmB ? "Exchanging" : "Waiting for both");
        }
        if (slot == CANCEL) {
            return named(Items.RED_CONCRETE, "Cancel trade");
        }
        return named(Items.GRAY_STAINED_GLASS_PANE, " ");
    }

    private static ItemStack named(net.minecraft.item.Item item, String name) {
        ItemStack stack = new ItemStack(item);
        stack.setCustomName(Text.literal(name));
        return stack;
    }

    private static String money(double value) {
        return String.format(Locale.US, "%.1f", value);
    }

    private static boolean contains(int[] slots, int slot) {
        for (int value : slots) {
            if (value == slot) {
                return true;
            }
        }
        return false;
    }
}
