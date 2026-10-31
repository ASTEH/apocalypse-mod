package dev.aftermath.mixin.tacz;

import dev.aftermath.character.WeaponProgressionService;
import dev.aftermath.weapon.TaczCompatibility;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Server-side aim transition penalty; the client counterpart uses the synchronized skill snapshot. */
@Mixin(targets = "com.tacz.guns.entity.shooter.LivingEntityAim", remap = false)
public abstract class LivingEntityAimMixin {
    @Shadow
    @Final
    private LivingEntity shooter;

    @ModifyVariable(method = "tickAimingProgress", at = @At("STORE"), ordinal = 1, remap = false)
    private float aftermath$slowAimTransition(float alphaProgress) {
        if (!(shooter instanceof ServerPlayer player)) return alphaProgress;

        String weaponClass = TaczCompatibility.weaponClassForItem(player.getMainHandItem());
        double multiplier = WeaponProgressionService.aimTimeMultiplier(player, weaponClass);
        return (float) (alphaProgress / multiplier);
    }
}
