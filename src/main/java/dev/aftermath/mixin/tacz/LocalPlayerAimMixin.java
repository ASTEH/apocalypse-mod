package dev.aftermath.mixin.tacz;

import dev.aftermath.character.WeaponProgressionService;
import dev.aftermath.client.TraitClientState;
import dev.aftermath.client.WeaponProgressClientState;
import dev.aftermath.weapon.TaczCompatibility;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keeps the local ADS animation in step with the server-authoritative aim penalty. */
@Mixin(targets = "com.tacz.guns.client.gameplay.LocalPlayerAim", remap = false)
public abstract class LocalPlayerAimMixin {
    @Shadow
    @Final
    private LocalPlayer player;

    @Inject(method = "getAlphaProgress", at = @At("RETURN"), cancellable = true, remap = false)
    private void aftermath$slowClientAim(@Coerce Object gunData, CallbackInfoReturnable<Float> callback) {
        String weaponClass = TaczCompatibility.weaponClassForItem(player.getMainHandItem());
        if (weaponClass == null) return;

        int level = WeaponProgressClientState.levelFor(weaponClass);
        boolean eagleEye = TraitClientState.has("eagle_eye");
        double multiplier = WeaponProgressionService.aimTimeMultiplier(
                level, eagleEye, WeaponProgressClientState.untrainedAimTimeMultiplier());
        callback.setReturnValue((float) (callback.getReturnValue() / multiplier));
    }
}
