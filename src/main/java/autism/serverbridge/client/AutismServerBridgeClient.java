package autism.serverbridge.client;

import autism.serverbridge.AutismNetwork;
import autism.serverbridge.client.mixin.HandledScreenAccessor;
import autism.serverbridge.client.mixin.ScreenInvoker;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public final class AutismServerBridgeClient implements ClientModInitializer {
    private static final int BUTTON_X = -48;
    private static final int BUTTON_Y = 4;

    @Override
    public void onInitializeClient() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!(screen instanceof InventoryScreen inventory)) {
                return;
            }
            HandledScreenAccessor position = (HandledScreenAccessor) inventory;
            ((ScreenInvoker) inventory).autism$addDrawableChild(ButtonWidget.builder(Text.literal("A"), button ->
                            ClientPlayNetworking.send(AutismNetwork.OPEN_MENU, PacketByteBufs.empty()))
                    .dimensions(position.autism$getX() + BUTTON_X, position.autism$getY() + BUTTON_Y, 20, 20)
                    .tooltip(Tooltip.of(Text.literal("Headquarters")))
                    .build());
        });
    }
}
