package dev.aftermath.client;

import dev.aftermath.weapon.TaczCompatibility;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.material.FogType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.resources.ResourceLocation;
import java.lang.reflect.Method;
import net.minecraft.world.entity.player.Player;

import java.util.Locale;

/**
 * Client-only trait visuals and audio filtering. This class is registered reflectively from the
 * common entry point so dedicated servers never load client classes. It avoids mixins and shaders.
 */
public final class TraitClientEvents {
    private static final float HARD_OF_HEARING_VOLUME = 0.35F;
    private static final float KEEN_HEARING_VOLUME = 1.4F;
    private static Player lastFirearmTraitPlayer;

    private TraitClientEvents() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(TraitClientEvents::onPlaySound);
        NeoForge.EVENT_BUS.addListener(TraitClientEvents::onRenderFog);
        NeoForge.EVENT_BUS.addListener(TraitClientEvents::onClientTick);
        NeoForge.EVENT_BUS.addListener(TraitClientEvents::onComputeCameraAngles);
    }

    /**
     * TaCZ computes inaccuracy from its active gun cache on both logical sides. The server sends
     * the exact effective trait multipliers; the local cache is modified only for the local player.
     */
    public static void onClientTick(ClientTickEvent.Post event) {
        Player current = Minecraft.getInstance().player;
        if (lastFirearmTraitPlayer != null && lastFirearmTraitPlayer != current) {
            TaczCompatibility.applyFirearmTraitEffects(lastFirearmTraitPlayer, 1.0D, 1.0D, 1.0D);
        }
        if (current != null) {
            TaczCompatibility.applyFirearmTraitEffects(current,
                    TraitClientState.firearmSpreadMultiplier(),
                    TraitClientState.firearmRecoilMultiplier(),
                    TraitClientState.quickReloadDurationMultiplier());
        }
        lastFirearmTraitPlayer = current;
    }

    /**
     * Adds a visible but bounded low-frequency tremor to the camera while aiming a TaCZ gun.
     * The amplitude is calculated by the server from this weapon class's mastery XP.
     */
    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || event.getCamera().getEntity() != player) return;

        ItemStack held = player.getMainHandItem();
        ResourceLocation gunId = TaczCompatibility.gunIdFromHeldItem(held);
        if (gunId == null || "empty".equals(gunId.getPath())) return;

        float aim = clientAimProgress(player, (float) event.getPartialTick());
        if (aim < 0.18F) return;

        double sway = TraitClientState.aimingSwayDegrees() * aim;
        if (player.isSprinting()) sway *= 1.45D;
        else if (player.getDeltaMovement().horizontalDistanceSqr() > 0.0025D) sway *= 1.20D;
        if (player.isCrouching()) sway *= 0.52D;
        if (sway < 0.001D) return;

        double seconds = System.nanoTime() / 1_000_000_000.0D;
        double pitchOscillation = Math.sin(seconds * 7.3D)
                + Math.sin(seconds * 11.7D + 0.7D) * 0.36D
                + Math.sin(seconds * 3.1D + 1.4D) * 0.42D;
        double yawOscillation = Math.sin(seconds * 6.1D + 0.9D) * 0.72D
                + Math.sin(seconds * 10.2D + 2.1D) * 0.28D;
        event.setPitch(event.getPitch() + (float) (pitchOscillation * sway * 0.62D));
        event.setYaw(event.getYaw() + (float) (yawOscillation * sway * 0.48D));
    }

    private static float clientAimProgress(LocalPlayer player, float partialTick) {
        try {
            Class<?> operatorType = Class.forName("com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator");
            Method factory = operatorType.getMethod("fromLocalPlayer", LocalPlayer.class);
            Object operator = factory.invoke(null, player);
            Object result = operatorType.getMethod("getClientAimingProgress", float.class)
                    .invoke(operator, partialTick);
            if (result instanceof Number number) {
                return Math.max(0.0F, Math.min(1.0F, number.floatValue()));
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // No TaCZ installed or its client API changed: aiming sway safely stays disabled.
        }
        return 0.0F;
    }

    public static void onPlaySound(PlaySoundEvent event) {
        if (Minecraft.getInstance().player == null) return;
        SoundInstance original = event.getSound();
        if (original == null) return;

        // Deaf means genuinely deaf, not only deaf to known footsteps/zombie/gunfire IDs.
        // Keep only the MUSIC category so custom ambience, UI, blocks, mobs and weapon addons
        // are filtered too, without depending on their sound event names.
        if (TraitClientState.has("deaf")) {
            if (original.getSource() != SoundSource.MUSIC) {
                event.setSound(null);
            }
            return;
        }

        String name = event.getName() == null ? "" : event.getName().toLowerCase(Locale.ROOT);
        if (!isRelevantSound(name)) return;

        if (TraitClientState.has("hard_of_hearing")) {
            event.setSound(new ScaledSoundInstance(original, HARD_OF_HEARING_VOLUME));
        } else if (TraitClientState.has("keen_hearing")) {
            event.setSound(new ScaledSoundInstance(original, KEEN_HEARING_VOLUME));
        }
    }

    private static boolean isRelevantSound(String name) {
        return name.contains("step")
                || name.contains("footstep")
                || name.contains("zombie")
                || name.contains("gun")
                || name.contains("shot")
                || name.contains("shoot")
                || name.contains("firearm")
                || name.contains("weapon")
                || name.contains("tacz")
                || name.contains("gunshot");
    }

    public static void onRenderFog(ViewportEvent.RenderFog event) {
        if (!TraitClientState.has("short_sighted") || event.getType() != FogType.NONE) return;

        // Use the normal viewport fog event rather than a custom post-process shader (Iris-safe fallback).
        float originalNear = event.getNearPlaneDistance();
        float originalFar = event.getFarPlaneDistance();
        event.setNearPlaneDistance(Math.max(0.0F, originalNear * 0.55F));
        event.setFarPlaneDistance(Math.max(originalNear + 1.0F, originalFar * 0.55F));
        event.setCanceled(true);
    }

    /**
     * Delegates the original sound identity, resolved sound and coordinates so sound-engine addons
     * can still identify the source. Only the effective volume is scaled for the selected trait.
     */
    private record ScaledSoundInstance(SoundInstance delegate, float volumeScale) implements SoundInstance {
        @Override public ResourceLocation getLocation() { return delegate.getLocation(); }
        @Override public WeighedSoundEvents resolve(SoundManager manager) { return delegate.resolve(manager); }
        @Override public Sound getSound() { return delegate.getSound(); }
        @Override public SoundSource getSource() { return delegate.getSource(); }
        @Override public boolean isLooping() { return delegate.isLooping(); }
        @Override public int getDelay() { return delegate.getDelay(); }
        @Override public float getVolume() { return delegate.getVolume() * volumeScale; }
        @Override public float getPitch() { return delegate.getPitch(); }
        @Override public double getX() { return delegate.getX(); }
        @Override public double getY() { return delegate.getY(); }
        @Override public double getZ() { return delegate.getZ(); }
        @Override public Attenuation getAttenuation() { return delegate.getAttenuation(); }
        @Override public boolean isRelative() { return delegate.isRelative(); }
        @Override public boolean canStartSilent() { return delegate.canStartSilent(); }
        @Override public boolean canPlaySound() { return delegate.canPlaySound(); }
    }
}
