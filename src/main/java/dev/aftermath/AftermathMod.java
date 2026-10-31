package dev.aftermath;

import com.mojang.logging.LogUtils;
import dev.aftermath.character.AftermathAttachments;
import dev.aftermath.character.AftermathServerEvents;
import dev.aftermath.character.CharacterDefinitions;
import dev.aftermath.character.TraitRuntimeService;
import dev.aftermath.character.WeaponProgressionService;
import dev.aftermath.content.AftermathContent;
import dev.aftermath.day.DayProgressionDefinitions;
import dev.aftermath.learning.LearningDefinitions;
import dev.aftermath.learning.LearningService;
import dev.aftermath.learning.compat.LrTacticalCompatibility;
import dev.aftermath.medical.MedicalCompatibilityService;
import dev.aftermath.weapon.FirearmDefinitions;
import dev.aftermath.weapon.TaczCompatibility;
import dev.aftermath.noise.HordeDirector;
import dev.aftermath.noise.NoiseDefinitions;
import dev.aftermath.noise.NoiseService;
import dev.aftermath.command.AftermathCommands;
import dev.aftermath.config.AftermathServerConfig;
import dev.aftermath.network.AftermathNetworking;
import dev.aftermath.zombie.ZombieKillService;
import dev.aftermath.zombie.ZombieVariantDefinitions;
import dev.aftermath.zombie.ZombieVariantService;
import dev.aftermath.zombie.ZombieNightSpawnService;
import dev.aftermath.world.MilitarySpawnControlService;
import dev.aftermath.world.WorldSpawnControlService;
import dev.aftermath.world.WorldSpawnDefinitions;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

/** Common entry point. Keep this class safe to load on a dedicated server. */
@Mod(AftermathMod.MOD_ID)
public final class AftermathMod {
    public static final String MOD_ID = "aftermath";
    public static final Logger LOGGER = LogUtils.getLogger();

    public AftermathMod(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.SERVER, AftermathServerConfig.SPEC);
        MedicalCompatibilityService.logDetectedMedicalMods();
        AftermathAttachments.register(modEventBus);
        AftermathContent.register(modEventBus);
        modEventBus.addListener(AftermathNetworking::registerPayloads);
        NeoForge.EVENT_BUS.addListener(AftermathCommands::registerCommands);
        NeoForge.EVENT_BUS.addListener(WorldSpawnDefinitions::register);
        NeoForge.EVENT_BUS.addListener(MilitarySpawnControlService::onLevelTick);
        NeoForge.EVENT_BUS.addListener(MilitarySpawnControlService::onServerStopping);
        NeoForge.EVENT_BUS.addListener(WorldSpawnControlService::onMobPositionCheck);
        NeoForge.EVENT_BUS.addListener(WorldSpawnControlService::onChunkLoad);
        NeoForge.EVENT_BUS.addListener(ZombieVariantDefinitions::register);
        NeoForge.EVENT_BUS.addListener(ZombieNightSpawnService::onFinalizeSpawn);
        NeoForge.EVENT_BUS.addListener(ZombieVariantService::onFinalizeSpawn);
        NeoForge.EVENT_BUS.addListener(CharacterDefinitions::register);
        NeoForge.EVENT_BUS.addListener(DayProgressionDefinitions::register);
        NeoForge.EVENT_BUS.addListener(AftermathServerEvents::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(AftermathServerEvents::onPlayerRespawn);
        NeoForge.EVENT_BUS.addListener(WeaponProgressionService::onLivingDeath);
        NeoForge.EVENT_BUS.addListener(ZombieKillService::onLivingDeath);
        NeoForge.EVENT_BUS.addListener(WeaponProgressionService::onLivingDamage);
        NeoForge.EVENT_BUS.addListener(TraitRuntimeService::onLivingDamage);
        NeoForge.EVENT_BUS.addListener(TraitRuntimeService::onLivingHeal);
        NeoForge.EVENT_BUS.addListener(TraitRuntimeService::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(TraitRuntimeService::onPlayerLogout);
        NeoForge.EVENT_BUS.addListener(LearningDefinitions::register);
        NeoForge.EVENT_BUS.addListener(FirearmDefinitions::register);
        NeoForge.EVENT_BUS.addListener(LearningService::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(LearningService::onItemCrafted);
        NeoForge.EVENT_BUS.addListener(LearningService::onItemSmelted);
        NeoForge.EVENT_BUS.addListener(LearningService::onCropBreak);
        NeoForge.EVENT_BUS.addListener(LearningService::onAnvilRepair);
        NeoForge.EVENT_BUS.addListener(LearningService::onLootContainerInteract);
        NeoForge.EVENT_BUS.addListener(LearningService::onContainerOpen);
        NeoForge.EVENT_BUS.addListener(LearningService::onContainerClose);
        NeoForge.EVENT_BUS.addListener(LearningService::onLivingDamage);
        LrTacticalCompatibility.register();
        NeoForge.EVENT_BUS.addListener(LearningService::onPlayerLogout);
        NeoForge.EVENT_BUS.addListener(NoiseDefinitions::register);
        NeoForge.EVENT_BUS.addListener(NoiseService::onLevelTick);
        NeoForge.EVENT_BUS.addListener(NoiseService::onServerStopping);
        NeoForge.EVENT_BUS.addListener(HordeDirector::onEntityJoinLevel);
        NeoForge.EVENT_BUS.addListener(TaczCompatibility::onEntityJoinLevel);
        NeoForge.EVENT_BUS.addListener(HordeDirector::onEntityLeaveLevel);
        NeoForge.EVENT_BUS.addListener(HordeDirector::onServerStopping);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            AftermathNetworking.registerClientHooks(modEventBus);
        }

        LOGGER.info("Aftermath Survival Framework {} initialised for Minecraft 1.21.1.",
                modContainer.getModInfo().getVersion());
    }
}
