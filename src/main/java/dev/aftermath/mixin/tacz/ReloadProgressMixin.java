package dev.aftermath.mixin.tacz;

import dev.aftermath.character.WeaponProgressionService;
import dev.aftermath.weapon.TaczCompatibility;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Scales the elapsed reload time exposed to TaCZ Lua gun scripts.
 * Scripts using ModernKineticGunScriptAPI.getReloadTime() inherit the skill penalty without
 * changing each weapon's authored reload durations or firing custom reload events.
 */
@Mixin(targets = "com.tacz.guns.item.ModernKineticGunScriptAPI", remap = false)
public abstract class ReloadProgressMixin {
    @Shadow
    public abstract LivingEntity getShooter();

    @Shadow
    public abstract ItemStack getItemStack();

    @Inject(method = "getReloadTime", at = @At("RETURN"), cancellable = true, remap = false)
    private void aftermath$scaleReloadProgress(CallbackInfoReturnable<Long> callback) {
        if (!(getShooter() instanceof ServerPlayer player)) return;
        long elapsed = callback.getReturnValue();
        if (elapsed <= 0L) return;

        String weaponClass = TaczCompatibility.weaponClassForItem(getItemStack());
        double durationMultiplier = WeaponProgressionService.reloadDurationMultiplier(player, weaponClass);
        if (durationMultiplier <= 1.0001D) return;

        // Lower effective elapsed time means the weapon's native reload state machine completes later.
        callback.setReturnValue(Math.max(0L, Math.round(elapsed / durationMultiplier)));
    }
}
