package autism.serverbridge.trade;

import autism.serverbridge.AutismServerBridge;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.List;

public final class TradeHistory {
    private TradeHistory() {
    }

    public static void record(TradeSession session, List<ItemStack> givenByA, List<ItemStack> givenByB) {
        MinecraftServer server = AutismServerBridge.server();
        if (server == null) {
            return;
        }
        JsonObject row = new JsonObject();
        row.addProperty("time", Instant.now().toString());
        row.addProperty("id", session.id.toString());
        row.addProperty("a", session.nameA);
        row.addProperty("b", session.nameB);
        row.addProperty("yapA", session.yapA);
        row.addProperty("brainA", session.brainA);
        row.addProperty("yapB", session.yapB);
        row.addProperty("brainB", session.brainB);
        row.add("givenByA", items(givenByA));
        row.add("givenByB", items(givenByB));
        try {
            Files.writeString(server.getSavePath(WorldSavePath.ROOT).resolve("autismserverbridge-trades.jsonl"),
                    row + System.lineSeparator(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception error) {
            AutismServerBridge.LOGGER.info("Could not save trade history: " + error);
        }
    }

    private static JsonArray items(List<ItemStack> stacks) {
        JsonArray array = new JsonArray();
        for (ItemStack stack : stacks) {
            array.add(Registries.ITEM.getId(stack.getItem()) + " x" + stack.getCount());
        }
        return array;
    }
}
