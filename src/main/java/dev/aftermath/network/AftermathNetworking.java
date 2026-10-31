package dev.aftermath.network;

import dev.aftermath.character.CharacterSelectionService;
import dev.aftermath.network.TraitStatePayload;
import dev.aftermath.network.ZombieKillCountPayload;
import dev.aftermath.network.MoodlesSyncPayload;
import dev.aftermath.network.WeaponProgressSyncPayload;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** Registers the versioned character protocol; all gameplay mutations are deferred onto the main thread. */
public final class AftermathNetworking {
    private static final String CLIENT_HANDLER = "dev.aftermath.client.CharacterClientPayloads";

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToServer(SelectCharacterPayload.TYPE, SelectCharacterPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        CharacterSelectionService.handleSelection(payload, context)));
        registrar.playToClient(CharacterScreenPayload.TYPE, CharacterScreenPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        invokeClient("open", CharacterScreenPayload.class, payload)));
        registrar.playToClient(CharacterSelectionResultPayload.TYPE, CharacterSelectionResultPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        invokeClient("handleResult", CharacterSelectionResultPayload.class, payload)));
        registrar.playToClient(TraitStatePayload.TYPE, TraitStatePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        invokeClient("handleTraitState", TraitStatePayload.class, payload)));
        registrar.playToClient(MoodlesSyncPayload.TYPE, MoodlesSyncPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        invokeClient("handleMoodles", MoodlesSyncPayload.class, payload)));
        registrar.playToClient(ZombieKillCountPayload.TYPE, ZombieKillCountPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        invokeClient("handleZombieKillCount", ZombieKillCountPayload.class, payload)));
        registrar.playToClient(WeaponProgressSyncPayload.TYPE, WeaponProgressSyncPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        invokeClient("handleWeaponProgress", WeaponProgressSyncPayload.class, payload)));
    }

    /*
     * Keep client-only Minecraft classes out of this common class so the dedicated server
     * can load the networking registry. These callbacks are invoked only for clientbound payloads.
     */
    /** Called only on the physical client; reflection keeps client-only classes out of the common class graph. */
    public static void registerClientHooks(IEventBus modEventBus) {
        try {
            Class<?> clientEvents = Class.forName("dev.aftermath.client.TraitClientEvents");
            clientEvents.getMethod("register").invoke(null);
            Class<?> moodlesHud = Class.forName("dev.aftermath.client.MoodlesHud");
            moodlesHud.getMethod("register", IEventBus.class).invoke(null, modEventBus);
            Class<?> zombieKillHud = Class.forName("dev.aftermath.client.ZombieKillHud");
            zombieKillHud.getMethod("register", IEventBus.class).invoke(null, modEventBus);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not register Aftermath client hooks", exception);
        }
    }

    private static void invokeClient(String methodName, Class<?> payloadType, Object payload) {
        try {
            Class<?> handler = Class.forName(CLIENT_HANDLER);
            Method method = handler.getMethod(methodName, payloadType);
            method.invoke(null, payload);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException exception) {
            throw new IllegalStateException("Could not dispatch Aftermath client payload " + methodName, exception);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) throw runtimeException;
            if (cause instanceof Error error) throw error;
            throw new IllegalStateException("Aftermath client payload failed: " + methodName, cause);
        }
    }

    private AftermathNetworking() {}
}
