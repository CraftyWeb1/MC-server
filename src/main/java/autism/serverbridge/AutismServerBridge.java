package autism.serverbridge;

import autism.serverbridge.trade.TradeManager;
import io.github.miniplaceholders.api.Expansion;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.identity.Identity;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public final class AutismServerBridge implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("AutismServerBridge");
    private static volatile MinecraftServer minecraftServer;
    private static Method changeMethod;
    private static Method setMethod;
    private static Method addMethod;
    private static boolean scannedMutators;

    public static MinecraftServer server() {
        return minecraftServer;
    }

    public static double rawBalance(UUID playerId, String currencyId) {
        Double value = EconomyLookup.read(playerId, currencyId);
        return value == null ? 0.0 : value;
    }

    public static boolean changeBalance(UUID playerId, String currencyId, double delta) {
        return EconomyLookup.change(playerId, currencyId, delta);
    }

    @Override
    public void onInitialize() {
        if (FabricLoader.getInstance().getEnvironmentType() != EnvType.SERVER) {
            return;
        }
        AutismNetwork.registerServer();
        ServerLifecycleEvents.SERVER_STARTED.register(server -> minecraftServer = server);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> TradeManager.disconnect(handler.player));
        TradeManager.register();
        Expansion.builder("autism")
                .audiencePlaceholder("yapcoins", (audience, queue, ctx) -> balanceTag(audience, "usd"))
                .audiencePlaceholder("braincells", (audience, queue, ctx) -> balanceTag(audience, "coin"))
                .build()
                .register();
        LOGGER.info("Registered <autism_yapcoins>, <autism_braincells>, and /trade.");
    }

    private static Tag balanceTag(Audience audience, String currencyId) {
        String shown = "0.0";
        Optional<UUID> playerId = audience.get(Identity.UUID);
        if (playerId.isPresent()) {
            shown = EconomyLookup.balance(playerId.get(), currencyId);
        }
        return Tag.selfClosingInserting(Component.text(shown));
    }

    private static final class EconomyLookup {
        private static volatile boolean reported;

        private EconomyLookup() {
        }

        static String balance(UUID playerId, String currencyId) {
            Double value = read(playerId, currencyId);
            if (value == null) {
                return "0.0";
            }
            return String.format(Locale.US, "%.1f", value);
        }

        static Double read(UUID playerId, String currencyId) {
            try {
                Object state = serverState();
                if (state == null) {
                    return null;
                }
                Method method = findBalanceMethod(state.getClass());
                if (method == null) {
                    report("No getBalance(player, currency) method on " + state.getClass().getName());
                    return null;
                }
                method.setAccessible(true);
                Object target = Modifier.isStatic(method.getModifiers()) ? null : state;
                Object raw = method.invoke(target, arguments(method, playerId, currencyId));
                report("Using " + method);
                return parse(raw);
            } catch (Throwable error) {
                report("Balance lookup failed: " + error);
                return null;
            }
        }

        static boolean change(UUID playerId, String currencyId, double delta) {
            if (Math.abs(delta) < 0.0000001) {
                return true;
            }
            Double current = read(playerId, currencyId);
            if (current == null || current + delta < -0.0001) {
                return false;
            }
            try {
                Object state = serverState();
                if (state == null) {
                    return false;
                }
                scanMutators(state.getClass());
                if (changeMethod != null) {
                    invokeNumber(changeMethod, state, playerId, currencyId, delta);
                    return true;
                }
                if (setMethod != null) {
                    invokeNumber(setMethod, state, playerId, currencyId, current + delta);
                    return true;
                }
                if (addMethod != null) {
                    invokeNumber(addMethod, state, playerId, currencyId, delta);
                    return true;
                }
                report("ServerEcoMod has getBalance, but no changeBalance, setBalance, or addBalance.");
                return false;
            } catch (Throwable error) {
                report("Currency change failed: " + error);
                return false;
            }
        }

        private static void scanMutators(Class<?> type) {
            if (scannedMutators) {
                return;
            }
            scannedMutators = true;
            StringBuilder names = new StringBuilder();
            for (Method method : type.getDeclaredMethods()) {
                names.append(method.getName()).append(' ');
                if (method.getParameterCount() != 3) {
                    continue;
                }
                if (method.getName().equals("changeBalance") && changeMethod == null) {
                    changeMethod = method;
                }
                if (method.getName().equals("setBalance") && setMethod == null) {
                    setMethod = method;
                }
                if (method.getName().equals("addBalance") && addMethod == null) {
                    addMethod = method;
                }
            }
            LOGGER.info("ServerEcoMod methods: " + names);
        }

        private static void invokeNumber(Method method, Object state, UUID playerId, String currencyId, double value) throws ReflectiveOperationException {
            method.setAccessible(true);
            Object target = Modifier.isStatic(method.getModifiers()) ? null : state;
            Class<?>[] types = method.getParameterTypes();
            Object[] args = new Object[3];
            int numberSlot = -1;
            for (int i = 0; i < 3; i++) {
                if (isNumber(types[i])) {
                    numberSlot = i;
                }
            }
            if (numberSlot < 0) {
                throw new IllegalStateException(method.toString());
            }
            args[numberSlot] = coerce(types[numberSlot], value);
            boolean playerFilled = false;
            for (int i = 0; i < 3; i++) {
                if (i == numberSlot) {
                    continue;
                }
                if (!playerFilled) {
                    args[i] = playerArgument(types[i], playerId);
                    playerFilled = args[i] != null;
                    if (args[i] == null) {
                        args[i] = currencyArgument(types[i], currencyId);
                    }
                } else {
                    args[i] = currencyArgument(types[i], currencyId);
                    if (args[i] == null) {
                        args[i] = playerArgument(types[i], playerId);
                    }
                }
            }
            method.invoke(target, args);
        }

        private static boolean isNumber(Class<?> type) {
            return type == double.class || type == Double.class || type == float.class || type == Float.class
                    || type == int.class || type == Integer.class || type == long.class || type == Long.class;
        }

        private static Object coerce(Class<?> type, double value) {
            if (type == float.class || type == Float.class) {
                return (float) value;
            }
            if (type == long.class || type == Long.class) {
                return Math.round(value);
            }
            if (type == int.class || type == Integer.class) {
                return (int) Math.round(value);
            }
            return value;
        }

        private static Object serverState() throws ReflectiveOperationException {
            Class<?> type = Class.forName("server_economy.EconomyState");
            for (Method method : type.getDeclaredMethods()) {
                if (!method.getName().equals("getServerState") || !Modifier.isStatic(method.getModifiers())) {
                    continue;
                }
                method.setAccessible(true);
                if (method.getParameterCount() == 0) {
                    return method.invoke(null);
                }
                MinecraftServer running = minecraftServer;
                if (method.getParameterCount() == 1 && running != null && method.getParameterTypes()[0].isInstance(running)) {
                    return method.invoke(null, running);
                }
            }
            report("server_economy.EconomyState has no usable getServerState method");
            return null;
        }

        private static Method findBalanceMethod(Class<?> type) {
            for (Method method : type.getMethods()) {
                if (method.getName().equals("getBalance") && method.getParameterCount() == 2 && arguments(method, new UUID(0L, 0L), "usd") != null) {
                    return method;
                }
            }
            return null;
        }

        private static Object[] arguments(Method method, UUID playerId, String currencyId) {
            Class<?> first = method.getParameterTypes()[0];
            Class<?> second = method.getParameterTypes()[1];
            Object player = playerArgument(first, playerId);
            Object currency = currencyArgument(second, currencyId);
            if (player != null && currency != null) {
                return new Object[]{player, currency};
            }
            player = playerArgument(second, playerId);
            currency = currencyArgument(first, currencyId);
            if (player != null && currency != null) {
                return new Object[]{currency, player};
            }
            return null;
        }

        private static Object playerArgument(Class<?> type, UUID playerId) {
            if (type == UUID.class) {
                return playerId;
            }
            if (type == String.class) {
                return playerId.toString();
            }
            return null;
        }

        private static Object currencyArgument(Class<?> type, String currencyId) {
            if (type == String.class) {
                return currencyId;
            }
            return null;
        }

        private static Double parse(Object raw) {
            if (raw instanceof Optional<?> optional) {
                return parse(optional.orElse(null));
            }
            if (raw == null) {
                return 0.0;
            }
            if (raw instanceof Number number) {
                return number.doubleValue();
            }
            try {
                return Double.parseDouble(raw.toString().trim());
            } catch (NumberFormatException error) {
                report("Balance value was " + raw.getClass().getName() + ": " + raw);
                return null;
            }
        }

        private static void report(String message) {
            if (!reported) {
                reported = true;
                LOGGER.info(message);
            }
        }
    }
}
