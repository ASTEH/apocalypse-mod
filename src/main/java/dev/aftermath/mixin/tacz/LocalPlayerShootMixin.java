package dev.aftermath.mixin.tacz;

import dev.aftermath.character.WeaponProgressionService;
import dev.aftermath.client.WeaponProgressClientState;
import dev.aftermath.weapon.TaczCompatibility;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Adds a visible control penalty once per successful trigger action.
 * TaCZ retains each weapon's authored recoil animation; this is an additional novice-control penalty.
 */
@Mixin(targets = "com.tacz.guns.client.gameplay.LocalPlayerShoot", remap = false)
public abstract class LocalPlayerShootMixin {
    @Shadow
    @Final
    private LocalPlayer player;

    @Inject(method = "shoot", at = @At("RETURN"), remap = false)
    private void aftermath$applyNoviceRecoil(CallbackInfoReturnable<?> callback) {
        if (!"SUCCESS".equals(String.valueOf(callback.getReturnValue()))) return;

        String weaponClass = TaczCompatibility.weaponClassForItem(player.getMainHandItem());
        if (weaponClass == null) return;

        double classMultiplier = WeaponProgressClientState.handlingClassMultiplier(weaponClass);
        double penalty = WeaponProgressionService.handlingPenaltyForLevel(
                WeaponProgressClientState.levelFor(weaponClass)) * classMultiplier;
        if (penalty <= 0.0D) return;

        float pitchKick = (float) (WeaponProgressClientState.untrainedRecoilPitch() * penalty);
        float yawKick = (float) ((player.getRandom().nextFloat() * 2.0F - 1.0F)
                * WeaponProgressClientState.untrainedRecoilYaw() * penalty);
        if (pitchKick <= 0.0F && yawKick == 0.0F) return;

        player.setXRot(Mth.clamp(player.getXRot() - pitchKick, -90.0F, 90.0F));
        player.setYRot(player.getYRot() + yawKick);
    }
}
