package dev.aftermath.character;

import dev.aftermath.config.AftermathServerConfig;
import dev.aftermath.network.CharacterNetwork;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

public final class CharacterLoginHandler {
    private CharacterLoginHandler() {}

    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !AftermathServerConfig.ENABLE_CHARACTER_CREATION.get()) {
            return;
        }

        if (!player.getData(AftermathAttachments.CHARACTER_PROFILE).complete()
                && !CharacterNetwork.sendCatalog(player, true)) {
            player.sendSystemMessage(Component.translatable("message.aftermath.character.missing"));
            player.sendSystemMessage(Component.translatable("message.aftermath.character.list_hint"));
        }
    }
}
