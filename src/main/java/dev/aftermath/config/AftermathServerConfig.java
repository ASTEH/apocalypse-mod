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
    public static final ModConfigSpec.BooleanValue ENABLE_BLEEDING;
    public static final ModConfigSpec.BooleanValue ENABLE_MOODLES;
    public static final ModConfigSpec.BooleanValue ENABLE_HOSTILE_SPAWN_FILTER;
    public static final ModConfigSpec.BooleanValue ENABLE_SPAWNER_REMOVAL;
    public static final ModConfigSpec.BooleanValue ENABLE_ZOMBIE_VARIANTS;

    public static final ModConfigSpec.IntValue WEAPON_XP_PER_KILL;
    public static final ModConfigSpec.IntValue MAX_XP_PER_VICTIM_PER_MINUTE;
    public static final ModConfigSpec.IntValue DEFAULT_NOISE_RADIUS;
    public static final ModConfigSpec.IntValue MAX_HORDE_SIZE;
    public static final ModConfigSpec.IntValue AI_RECALC_INTERVAL_TICKS;
    public static final ModConfigSpec.IntValue MAX_NOISE_TARGETS;
    public static final ModConfigSpec.IntValue MAX_PATH_REQUESTS_PER_TICK;

    public static final ModConfigSpec.IntValue BLEEDING_CHANCE_PERCENT;
    public static final ModConfigSpec.IntValue MAX_BLEEDING_SEVERITY;
    public static final ModConfigSpec.IntValue BLEEDING_TICK_INTERVAL_TICKS;
    public static final ModConfigSpec.DoubleValue BLEEDING_DAMAGE_PER_SEVERITY;

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
        ENABLE_BLEEDING = builder.comment("Enable bleeding caused by hostile attacks and treatment with bandages.")
                .define("enableBleeding", true);
        ENABLE_MOODLES = builder.comment("Enable server-authoritative tiredness, exertion and panic Moodles.")
                .define("enableMoodles", true);
        ENABLE_HOSTILE_SPAWN_FILTER = builder.comment("Block unlisted hostile mob spawns using the world_rules/spawn_control.json datapack policy.")
                .define("enableHostileSpawnFilter", true);
        ENABLE_SPAWNER_REMOVAL = builder.comment("Remove configured vanilla spawner blocks when their chunks finish loading.")
                .define("enableSpawnerRemoval", true);
        ENABLE_ZOMBIE_VARIANTS = builder.comment("Enable weighted, datapack-driven zombie stat and sense archetypes.")
                .define("enableZombieVariants", true);
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

        builder.push("injuries_and_first_aid");
        BLEEDING_CHANCE_PERCENT = builder.comment("Chance in percent for a hostile hit that deals damage to cause or worsen bleeding.")
                .defineInRange("bleedingChancePercent", 25, 0, 100);
        MAX_BLEEDING_SEVERITY = builder.comment("Maximum stacking severity of bleeding.")
                .defineInRange("maxBleedingSeverity", 3, 1, 10);
        BLEEDING_TICK_INTERVAL_TICKS = builder.comment("Ticks between bleeding damage ticks (20 ticks = 1 second).")
                .defineInRange("bleedingDamageIntervalTicks", 40, 10, 1200);
        BLEEDING_DAMAGE_PER_SEVERITY = builder.comment("Damage dealt per bleeding severity level at each damage tick.")
                .defineInRange("bleedingDamagePerSeverity", 0.5D, 0.0D, 10.0D);
        builder.pop();

        SPEC = builder.build();
    }

    private AftermathServerConfig() {}
}
