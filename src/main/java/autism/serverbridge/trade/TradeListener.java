package autism.serverbridge.trade;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;

public final class TradeListener extends GenericContainerScreenHandler {
    private final TradeSession session;

    public TradeListener(int syncId, PlayerInventory playerInventory, TradeSession session) {
        super(ScreenHandlerType.GENERIC_9X6, syncId, playerInventory, session.inventory, 6);
        this.session = session;
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return !session.closing && session.involves(player.getUuid());
    }

    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
        if (!(player instanceof ServerPlayerEntity serverPlayer) || session.completed || session.closing) {
            return;
        }
        if (actionType == SlotActionType.THROW || actionType == SlotActionType.CLONE || actionType == SlotActionType.SWAP || actionType == SlotActionType.QUICK_CRAFT) {
            return;
        }
        if (slotIndex < 0 || slotIndex >= 54) {
            if (actionType != SlotActionType.QUICK_MOVE) {
                super.onSlotClick(slotIndex, button, actionType, player);
            }
            return;
        }
        if (TradeGui.isItemSlot(slotIndex)) {
            if (TradeGui.owns(session, serverPlayer.getUuid(), slotIndex) && actionType == SlotActionType.PICKUP) {
                moveCursor(slotIndex);
                session.resetConfirm();
                TradeGui.draw(session);
            }
            return;
        }
        if (slotIndex == TradeGui.YAP_A || slotIndex == TradeGui.BRAIN_A || slotIndex == TradeGui.YAP_B || slotIndex == TradeGui.BRAIN_B) {
            TradeGui.adjust(session, serverPlayer.getUuid(), slotIndex, button, actionType);
            return;
        }
        if (slotIndex == TradeGui.CONFIRM_A || slotIndex == TradeGui.CONFIRM_B) {
            TradeGui.toggleConfirm(session, serverPlayer.getUuid(), slotIndex);
            return;
        }
        if (slotIndex == TradeGui.CANCEL) {
            TradeManager.cancel(session);
        }
    }

    @Override
    public void onClosed(PlayerEntity player) {
        super.onClosed(player);
        if (!session.completed && !session.closing) {
            TradeManager.cancel(session, player instanceof ServerPlayerEntity serverPlayer ? serverPlayer : null);
        }
    }

    private void moveCursor(int slotIndex) {
        Slot slot = this.slots.get(slotIndex);
        ItemStack cursor = this.getCursorStack();
        ItemStack current = slot.getStack();
        if (cursor.isEmpty() && current.isEmpty()) {
            return;
        }
        if (cursor.isEmpty()) {
            this.setCursorStack(current.copy());
            slot.setStack(ItemStack.EMPTY);
        } else if (current.isEmpty()) {
            slot.setStack(cursor.copy());
            this.setCursorStack(ItemStack.EMPTY);
        } else if (ItemStack.canCombine(cursor, current)) {
            int move = Math.min(cursor.getCount(), current.getMaxCount() - current.getCount());
            if (move > 0) {
                current.increment(move);
                cursor.decrement(move);
                slot.setStack(current);
                this.setCursorStack(cursor.isEmpty() ? ItemStack.EMPTY : cursor);
            }
        } else {
            slot.setStack(cursor.copy());
            this.setCursorStack(current.copy());
        }
        slot.markDirty();
    }
}
