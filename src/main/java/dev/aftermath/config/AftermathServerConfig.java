package dev.aftermath.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server-authoritative settings. Gameplay options stay in the server config.
 * Defaults are conservative until each associated feature is implemented and tested.
 */
public final class AftermathServerConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue ENABLE_CHARACTER_CREATION;
    public static final ModConfigSpec.BooleanValue ALLOW_CHARACTER_RESELECTION;
    public static final ModConfigSpec.BooleanValue ENABLE_WEAPON_PROGRESSION;
    public static final ModConfigSpec.BooleanValue ENABLE_LEARNING;
    public static final ModConfigSpec.BooleanValue ENABLE_NOISE_EVENTS;
    public static final ModConfigSpec.BooleanValue ENABLE_HORDE_BEHAVIOR;
    public static final ModConfigSpec.BooleanValue ENABLE_BLOCK_BREAKING;
    public static final ModConfigSpec.BooleanValue ENABLE_DAY_PROGRESSION;

    public static final ModConfigSpec.IntValue WEAPON_XP_PER_KILL;
    public static final ModConfigSpec.IntValue MAX_XP_PER_VICTIM_PER_MINUTE;
    public static final ModConfigSpec.IntValue DEFAULT_NOISE_RADIUS;
    public static final ModConfigSpec.IntValue MAX_HORDE_SIZE;
    public static final ModConfigSpec.IntValue AI_RECALC_INTERVAL_TICKS;
    public static final ModConfigSpec.IntValue MAX_NOISE_TARGETS;
    public static final ModConfigSpec.IntValue MAX_PATH_REQUESTS_PER_TICK;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("features");
        ENABLE_CHARACTER_CREATION = builder.comment("Enable the initial character selection flow.")
                .define("enableCharacterCreation", true);
        ALLOW_CHARACTER_RESELECTION = builder.comment("Allow authorised players/admins to reopen character selection.")
                .define("allowCharacterReselection", true);
        ENABLE_WEAPON_PROGRESSION = builder.comment("Enable weapon-class XP and levels.")
                .define("enableWeaponProgression", true);
        ENABLE_LEARNING = builder.comment("Enable manual and television learning systems.")
                .define("enableLearning", true);
        ENABLE_NOISE_EVENTS = builder.comment("Enable server-side noise events. Noise never spawns mobs near players.")
                .define("enableNoiseEvents", true);
        ENABLE_HORDE_BEHAVIOR = builder.comment("Enable Aftermath-managed horde groups. Disabled until the AI MVP is tested.")
                .define("enableHordeBehavior", false);
        ENABLE_BLOCK_BREAKING = builder.comment("Allow configured zombies to break explicitly allow-listed blocks. Disabled by default.")
                .define("enableBlockBreaking", false);
        ENABLE_DAY_PROGRESSION = builder.comment("Scale noise detection and group sizes using the saved apocalypse day.")
                .define("enableDayProgression", true);
        builder.pop();

        builder.push("weapon_progression");
        WEAPON_XP_PER_KILL = builder.comment("Base weapon XP for an eligible kill.")
                .defineInRange("weaponXpPerKill", 10, 0, 10000);
        MAX_XP_PER_VICTIM_PER_MINUTE = builder.comment("Anti-farm limit: maximum XP from repeated kills of the same victim identity in a rolling minute.")
                .defineInRange("maxXpPerVictimPerMinute", 10, 0, 10000);
        builder.pop();

        builder.push("noise_and_hordes");
        DEFAULT_NOISE_RADIUS = builder.comment("Fallback radius for a noise event when its data definition has no radius.")
                .defineInRange("defaultNoiseRadius", 64, 0, 512);
        MAX_HORDE_SIZE = builder.comment("Hard safety cap for Aftermath-managed horde members.")
                .defineInRange("maxHordeSize", 24, 1, 128);
        AI_RECALC_INTERVAL_TICKS = builder.comment("Minimum interval between Aftermath path/target recalculations for an entity.")
                .defineInRange("aiRecalcIntervalTicks", 40, 5, 1200);
        MAX_NOISE_TARGETS = builder.comment("Maximum already-loaded eligible mobs assigned to one noise event.")
                .defineInRange("maxNoiseTargetsPerEvent", 32, 1, 128);
        MAX_PATH_REQUESTS_PER_TICK = builder.comment("Global Aftermath path requests allowed per tick in each dimension.")
                .defineInRange("maxPathRequestsPerTick", 16, 1, 256);
        builder.pop();

        SPEC = builder.build();
    }

    private AftermathServerConfig() {}
}
