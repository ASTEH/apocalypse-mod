package dev.aftermath;

import com.mojang.logging.LogUtils;
import dev.aftermath.character.AftermathAttachments;
import dev.aftermath.character.AftermathServerEvents;
import dev.aftermath.character.CharacterDefinitions;
import dev.aftermath.character.WeaponProgressionService;
import dev.aftermath.command.AftermathCommands;
import dev.aftermath.config.AftermathServerConfig;
import dev.aftermath.network.AftermathNetworking;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
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
        AftermathAttachments.register(modEventBus);
        modEventBus.addListener(AftermathNetworking::registerPayloads);
        NeoForge.EVENT_BUS.addListener(AftermathCommands::registerCommands);
        NeoForge.EVENT_BUS.addListener(CharacterDefinitions::register);
        NeoForge.EVENT_BUS.addListener(AftermathServerEvents::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(AftermathServerEvents::onPlayerRespawn);
        NeoForge.EVENT_BUS.addListener(WeaponProgressionService::onLivingDeath);
        NeoForge.EVENT_BUS.addListener(WeaponProgressionService::onLivingDamage);

        LOGGER.info("Aftermath Survival Framework {} initialised for Minecraft 1.21.1.",
                modContainer.getModInfo().getVersion());
    }
}
