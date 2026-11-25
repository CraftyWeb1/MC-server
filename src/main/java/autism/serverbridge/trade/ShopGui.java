package autism.serverbridge.trade;

import autism.serverbridge.AutismServerBridge;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.Locale;

public final class ShopGui {
    private static final Offer[] OFFERS = {
            new Offer(Items.BREAD, 4, 5.0),
            new Offer(Items.COOKED_BEEF, 8, 15.0),
            new Offer(Items.ENDER_PEARL, 1, 50.0),
            new Offer(Items.GOLDEN_APPLE, 1, 80.0),
            new Offer(Items.ARROW, 16, 10.0),
            new Offer(Items.TORCH, 16, 4.0),
            new Offer(Items.OAK_PLANKS, 32, 8.0),
            new Offer(Items.WHITE_BED, 1, 20.0)
    };
    private static final int[] ITEM_SLOTS = {10, 11, 12, 13, 14, 15, 16};
    private static final int BACK = 18;
    private static final int PAGE_LABEL = 22;
    private static final int NEXT = 26;

    private ShopGui() {
    }

    public static void open(ServerPlayerEntity player) {
        open(player, 0);
    }

    public static void open(ServerPlayerEntity player, int page) {
        int pages = pageCount();
        int shown = Math.max(0, Math.min(page, pages - 1));
        SimpleInventory inventory = new SimpleInventory(27);
        for (int slot = 0; slot < 27; slot++) {
            inventory.setStack(slot, named(Items.GRAY_STAINED_GLASS_PANE, " "));
        }
        for (int i = 0; i < ITEM_SLOTS.length; i++) {
            int offerIndex = shown * ITEM_SLOTS.length + i;
            if (offerIndex >= OFFERS.length) {
                break;
            }
            Offer offer = OFFERS[offerIndex];
            ItemStack stack = new ItemStack(offer.item, offer.count);
            stack.setCustomName(Text.literal(offer.count + " for " + money(offer.price) + " YAP"));
            inventory.setStack(ITEM_SLOTS[i], stack);
        }
        inventory.setStack(BACK, named(Items.ARROW, shown == 0 ? "Back" : "Previous page"));
        inventory.setStack(PAGE_LABEL, named(Items.PAPER, "Page " + (shown + 1) + " / " + pages));
        if (shown < pages - 1) {
            inventory.setStack(NEXT, named(Items.ARROW, "Next page"));
        }
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                (syncId, playerInventory, viewer) -> new ShopListener(syncId, playerInventory, inventory, shown),
                Text.literal("Go Broke")));
    }

    private static int pageCount() {
        return Math.max(1, (OFFERS.length + ITEM_SLOTS.length - 1) / ITEM_SLOTS.length);
    }

    private static void headquarters(ServerPlayerEntity player) {
        if (player.getServer() == null) {
            return;
        }
        player.getServer().getCommandManager().executeWithPrefix(
                player.getCommandSource().withLevel(2),
                "/gui open autism_menu");
    }

    private static ItemStack named(Item item, String name) {
        ItemStack stack = new ItemStack(item);
        stack.setCustomName(Text.literal(name));
        return stack;
    }

    private static String money(double value) {
        return String.format(Locale.US, "%.1f", value);
    }

    private static int offerIndex(int page, int slot) {
        for (int i = 0; i < ITEM_SLOTS.length; i++) {
            if (ITEM_SLOTS[i] == slot) {
                return page * ITEM_SLOTS.length + i;
            }
        }
        return -1;
    }

    private record Offer(Item item, int count, double price) {
    }

    private static final class ShopListener extends GenericContainerScreenHandler {
        private final int page;

        private ShopListener(int syncId, PlayerInventory playerInventory, SimpleInventory inventory, int page) {
            super(ScreenHandlerType.GENERIC_9X3, syncId, playerInventory, inventory, 3);
            this.page = page;
        }

        @Override
        public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
            if (!(player instanceof ServerPlayerEntity buyer)) {
                return;
            }
            if (slotIndex == BACK) {
                if (page == 0) {
                    headquarters(buyer);
                } else {
                    open(buyer, page - 1);
                }
                return;
            }
            if (slotIndex == NEXT && page < pageCount() - 1) {
                open(buyer, page + 1);
                return;
            }
            int offerIndex = offerIndex(page, slotIndex);
            if (offerIndex < 0 || offerIndex >= OFFERS.length) {
                return;
            }
            Offer offer = OFFERS[offerIndex];
            if (AutismServerBridge.rawBalance(buyer.getUuid(), "usd") + 0.001 < offer.price) {
                buyer.sendMessage(Text.literal("You do not have enough YapCoins."), false);
                return;
            }
            if (!AutismServerBridge.changeBalance(buyer.getUuid(), "usd", -offer.price)) {
                buyer.sendMessage(Text.literal("The shop could not take the YapCoins."), false);
                return;
            }
            TradeManager.give(buyer, new ItemStack(offer.item, offer.count));
            buyer.sendMessage(Text.literal("Bought for " + money(offer.price) + " YAP."), false);
        }
    }
}
