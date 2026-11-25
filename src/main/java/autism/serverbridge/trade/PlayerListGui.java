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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class PlayerListGui {
    private PlayerListGui() {
    }

    public static void open(ServerPlayerEntity viewer) {
        List<ServerPlayerEntity> players = new ArrayList<>(viewer.getServer().getPlayerManager().getPlayerList());
        players.removeIf(player -> player.getUuid().equals(viewer.getUuid()));
        players.sort(Comparator.comparing(player -> player.getEntityName().toLowerCase(Locale.ROOT)));
        SimpleInventory inventory = new SimpleInventory(54);
        UUID[] ids = new UUID[45];
        for (int i = 0; i < players.size() && i < 45; i++) {
            ServerPlayerEntity other = players.get(i);
            ids[i] = other.getUuid();
            ItemStack head = new ItemStack(Items.PLAYER_HEAD);
            head.getOrCreateNbt().putString("SkullOwner", other.getGameProfile().getName());
            head.setCustomName(Text.literal("Trade with " + other.getEntityName()));
            inventory.setStack(i, head);
        }
        if (players.isEmpty()) {
            ItemStack paper = new ItemStack(Items.PAPER);
            paper.setCustomName(Text.literal("Nobody else is online"));
            inventory.setStack(22, paper);
        }
        for (int slot = 45; slot < 54; slot++) {
            inventory.setStack(slot, pane());
        }
        viewer.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                (syncId, playerInventory, player) -> new ListListener(syncId, playerInventory, inventory, ids),
                Text.literal("Who are you trading?")));
    }

    private static ItemStack pane() {
        ItemStack stack = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
        stack.setCustomName(Text.literal(" "));
        return stack;
    }

    private static final class ListListener extends GenericContainerScreenHandler {
        private final UUID[] ids;

        private ListListener(int syncId, PlayerInventory playerInventory, SimpleInventory inventory, UUID[] ids) {
            super(ScreenHandlerType.GENERIC_9X6, syncId, playerInventory, inventory, 6);
            this.ids = ids;
        }

        @Override
        public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
            if (!(player instanceof ServerPlayerEntity viewer) || slotIndex < 0 || slotIndex >= ids.length || ids[slotIndex] == null) {
                return;
            }
            ServerPlayerEntity target = TradeManager.online(ids[slotIndex]);
            if (target != null) {
                TradeManager.request(viewer, target);
            }
            viewer.closeHandledScreen();
        }
    }
}
