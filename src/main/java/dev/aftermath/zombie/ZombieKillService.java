package dev.aftermath.zombie;

import dev.aftermath.character.AftermathAttachments;
import dev.aftermath.noise.ZombieEntityClassifier;
import dev.aftermath.network.ZombieKillCountPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Persistent, per-player lifetime kill totals for vanilla and recognized modded zombie-like mobs. */
public final class ZombieKillService {
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Mob victim)
                || !ZombieEntityClassifier.isZombieLikeKillTarget(victim)
                || !(event.getSource().getEntity() instanceof ServerPlayer killer)) {
            return;
        }

        int current = getCount(killer);
        int next = current == Integer.MAX_VALUE ? current : current + 1;
        killer.setData(AftermathAttachments.ZOMBIE_KILLS, next);
        sync(killer);
    }

    public static int getCount(ServerPlayer player) {
        return Math.max(0, player.getData(AftermathAttachments.ZOMBIE_KILLS));
    }

    public static void sync(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new ZombieKillCountPayload(getCount(player)));
    }

    private ZombieKillService() {}
}
