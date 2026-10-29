package dev.aftermath.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.material.FogType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;

/**
 * Client-only trait visuals and audio filtering. This class is registered reflectively from the
 * common entry point so dedicated servers never load client classes. It avoids mixins and shaders.
 */
public final class TraitClientEvents {
    private static final float HARD_OF_HEARING_VOLUME = 0.35F;
    private static final float KEEN_HEARING_VOLUME = 1.4F;

    private TraitClientEvents() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(TraitClientEvents::onPlaySound);
        NeoForge.EVENT_BUS.addListener(TraitClientEvents::onRenderFog);
    }

    public static void onPlaySound(PlaySoundEvent event) {
        if (Minecraft.getInstance().player == null) return;
        String name = event.getName() == null ? "" : event.getName().toLowerCase(Locale.ROOT);
        if (!isRelevantSound(name)) return;

        if (TraitClientState.has("deaf")) {
            event.setSound(null);
            return;
        }

        SoundInstance original = event.getSound();
        if (original == null) return;

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
