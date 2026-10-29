package dev.aftermath.network;

import dev.aftermath.character.CharacterSelectionService;
import dev.aftermath.client.CharacterClientPayloads;
import net.neoforged.fml.Dist;
import net.neoforged.fml.DistExecutor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Registers the small, versioned protocol for character creation. */
public final class AftermathNetworking {
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToServer(SelectCharacterPayload.TYPE, SelectCharacterPayload.STREAM_CODEC,
                CharacterSelectionService::handleSelection);
        registrar.playToClient(CharacterScreenPayload.TYPE, CharacterScreenPayload.STREAM_CODEC,
                (payload, context) -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        () -> () -> CharacterClientPayloads.open(payload)));
        registrar.playToClient(CharacterSelectionResultPayload.TYPE, CharacterSelectionResultPayload.STREAM_CODEC,
                (payload, context) -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        () -> () -> CharacterClientPayloads.handleResult(payload)));
    }

    private AftermathNetworking() {}
}
