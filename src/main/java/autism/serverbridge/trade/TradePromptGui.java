package autism.serverbridge.trade;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public final class TradePromptGui {
    private TradePromptGui() {
    }

    public static void open(ServerPlayerEntity target, ServerPlayerEntity from) {
        SimpleInventory inventory = new SimpleInventory(9);
        inventory.setStack(2, named(Items.LIME_CONCRETE, "Accept"));
        inventory.setStack(4, named(Items.PAPER, from.getEntityName() + " wants to trade"));
        inventory.setStack(6, named(Items.RED_CONCRETE, "Deny"));
        target.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                (syncId, playerInventory, player) -> new PromptListener(syncId, playerInventory, inventory),
                Text.literal("Trade request")));
    }

    private static ItemStack named(net.minecraft.item.Item item, String name) {
        ItemStack stack = new ItemStack(item);
        stack.setCustomName(Text.literal(name));
        return stack;
    }

    private static final class PromptListener extends GenericContainerScreenHandler {
        private PromptListener(int syncId, PlayerInventory playerInventory, SimpleInventory inventory) {
            super(ScreenHandlerType.GENERIC_9X1, syncId, playerInventory, inventory, 1);
        }

        @Override
        public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
            if (!(player instanceof ServerPlayerEntity serverPlayer)) {
                return;
            }
            if (slotIndex == 2) {
                TradeManager.accept(serverPlayer);
            } else if (slotIndex == 6) {
                TradeManager.deny(serverPlayer);
                serverPlayer.closeHandledScreen();
            }
        }
    }
}
