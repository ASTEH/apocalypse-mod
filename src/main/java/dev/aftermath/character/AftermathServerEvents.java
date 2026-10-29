package dev.aftermath.character;

import dev.aftermath.AftermathMod;
import dev.aftermath.config.AftermathServerConfig;
import dev.aftermath.network.CharacterScreenPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Server-side lifecycle hooks for first-join character creation and respawns. */
public final class AftermathServerEvents {
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        CharacterProfile profile = player.getData(AftermathAttachments.CHARACTER_PROFILE);
        PacketDistributor.sendToPlayer(player, TraitRuntimeService.createClientStatePayload(player));
        if (profile.created() || !AftermathServerConfig.ENABLE_CHARACTER_CREATION.get()) return;

        CharacterDefinitions.Snapshot definitions = CharacterDefinitions.snapshot();
        if (definitions.professions().isEmpty() || definitions.traits().isEmpty()) {
            AftermathMod.LOGGER.error("Cannot open character creation for {}: no usable definitions were loaded.",
                    player.getGameProfile().getName());
            return;
        }

        PacketDistributor.sendToPlayer(player, new CharacterScreenPayload(CharacterDefinitions.toClientJson()));
    }

    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            CharacterSelectionService.reapplyProfile(player);
            CharacterProfile profile = player.getData(AftermathAttachments.CHARACTER_PROFILE);
            PacketDistributor.sendToPlayer(player, TraitRuntimeService.createClientStatePayload(player));
        }
    }

    private AftermathServerEvents() {}
}
