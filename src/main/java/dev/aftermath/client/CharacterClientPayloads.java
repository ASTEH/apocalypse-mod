package dev.aftermath.client;

import dev.aftermath.network.CharacterScreenPayload;
import dev.aftermath.network.CharacterSelectionResultPayload;
import dev.aftermath.network.TraitStatePayload;
import dev.aftermath.network.ZombieKillCountPayload;
import dev.aftermath.network.WeaponProgressSyncPayload;
import dev.aftermath.network.SkillsSnapshotPayload;
import net.minecraft.client.Minecraft;

/**
 * Client-only handlers invoked directly by AftermathNetworking for clientbound payloads.
 * This class is deliberately not an EventBusSubscriber: it has no @SubscribeEvent methods.
 */
public final class CharacterClientPayloads {
    public static void open(CharacterScreenPayload payload) {
        Minecraft.getInstance().setScreen(new CharacterSelectionScreen(payload.definitionsJson()));
    }

    public static void handleTraitState(TraitStatePayload payload) {
        TraitClientState.setSelectedTraits(payload.selectedTraitIdsCsv());
    }

    public static void handleZombieKillCount(ZombieKillCountPayload payload) {
        ZombieKillHud.setCount(payload.count());
    }

    public static void handleWeaponProgress(WeaponProgressSyncPayload payload) {
        WeaponProgressClientState.apply(payload);
    }

    public static void handleSkillsSnapshot(SkillsSnapshotPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.setScreen(SkillsScreen.fromSnapshot(payload.snapshotJson()));
        }
    }

    public static void handleResult(CharacterSelectionResultPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (payload.success()) {
            if (minecraft.screen instanceof CharacterSelectionScreen) {
                minecraft.setScreen(null);
            }
        } else if (minecraft.screen instanceof CharacterSelectionScreen screen) {
            screen.setResult(payload.messageKey());
        }
    }

    private CharacterClientPayloads() {}
}
