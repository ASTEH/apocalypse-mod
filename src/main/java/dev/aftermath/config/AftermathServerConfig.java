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
    public static final ModConfigSpec.BooleanValue ENABLE_FIREARM_TRAIT_EFFECTS;
    public static final ModConfigSpec.BooleanValue ENABLE_LEARNING;
    public static final ModConfigSpec.BooleanValue ENABLE_NOISE_EVENTS;
    public static final ModConfigSpec.BooleanValue ENABLE_HORDE_BEHAVIOR;
    public static final ModConfigSpec.BooleanValue ENABLE_BLOCK_BREAKING;
    public static final ModConfigSpec.BooleanValue ENABLE_DAY_PROGRESSION;
    public static final ModConfigSpec.BooleanValue ENABLE_BLEEDING;
    public static final ModConfigSpec.BooleanValue ENABLE_DAYTIME_ZOMBIE_SUPPRESSION;

    public static final ModConfigSpec.IntValue WEAPON_XP_PER_KILL;
    public static final ModConfigSpec.IntValue MAX_XP_PER_VICTIM_PER_MINUTE;
    public static final ModConfigSpec.IntValue DEFAULT_NOISE_RADIUS;
    public static final ModConfigSpec.IntValue MAX_HORDE_SIZE;
    public static final ModConfigSpec.IntValue AI_RECALC_INTERVAL_TICKS;
    public static final ModConfigSpec.IntValue MAX_NOISE_TARGETS;
    public static final ModConfigSpec.IntValue MAX_PATH_REQUESTS_PER_TICK;
    public static final ModConfigSpec.IntValue MAX_SOUND_NOISE_SCANS_PER_TICK;
    public static final ModConfigSpec.IntValue DAYTIME_ZOMBIE_SPAWN_CHANCE_PERCENT;

    public static final ModConfigSpec.IntValue BLEEDING_CHANCE_PERCENT;
    public static final ModConfigSpec.IntValue MAX_BLEEDING_SEVERITY;
    public static final ModConfigSpec.IntValue BLEEDING_TICK_INTERVAL_TICKS;
    public static final ModConfigSpec.DoubleValue BLEEDING_DAMAGE_PER_SEVERITY;
    public static final ModConfigSpec.DoubleValue EAGLE_EYE_STRENGTH_SCALE;
    public static final ModConfigSpec.DoubleValue QUICK_RELOAD_STRENGTH_SCALE;
    public static final ModConfigSpec.DoubleValue UNTRAINED_SPREAD_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue MASTERED_SPREAD_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue UNTRAINED_RECOIL_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue MASTERED_RECOIL_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue UNTRAINED_AIM_SWAY_DEGREES;
    public static final ModConfigSpec.DoubleValue MASTERED_AIM_SWAY_DEGREES;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("features");
        ENABLE_CHARACTER_CREATION = builder.comment("Enable the initial character selection flow.")
                .define("enableCharacterCreation", true);
        ALLOW_CHARACTER_RESELECTION = builder.comment("Allow authorised players/admins to reopen character selection.")
                .define("allowCharacterReselection", true);
        ENABLE_WEAPON_PROGRESSION = builder.comment("Enable weapon-class XP and levels.")
                .define("enableWeaponProgression", true);
        ENABLE_FIREARM_TRAIT_EFFECTS = builder.comment("Enable the Eagle Eye and Quick Reload character-trait effects for supported TaCZ firearms.")
                .define("enableFirearmTraitEffects", true);
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
        ENABLE_DAYTIME_ZOMBIE_SUPPRESSION = builder.comment("Suppress natural zombie-like mob spawns in the Overworld during daytime.")
                .define("enableDaytimeZombieSuppression", true);
        builder.pop();

        builder.push("zombie_population");
        DAYTIME_ZOMBIE_SPAWN_CHANCE_PERCENT = builder.comment("Chance (0-100) for a zombie-like mob to be allowed to spawn naturally in the Overworld during daytime. 0 means none; 5 means very rare.")
                .defineInRange("daytimeZombieSpawnChancePercent", 0, 0, 100);
        builder.pop();

        builder.push("firearm_traits");
        EAGLE_EYE_STRENGTH_SCALE = builder.comment("Scale applied to the Eagle Eye trait value from datapacks. 1.0 uses the datapack value; lower values make the spread tighter.")
                .defineInRange("eagleEyeStrengthScale", 1.0D, 0.1D, 2.0D);
        QUICK_RELOAD_STRENGTH_SCALE = builder.comment("Scale applied to the Quick Reload trait value from datapacks. 1.0 uses the datapack value; lower values make reloads faster.")
                .defineInRange("quickReloadStrengthScale", 1.0D, 0.1D, 2.0D);
        UNTRAINED_SPREAD_MULTIPLIER = builder.comment("Firearm spread multiplier for a character with no experience in that weapon class.")
                .defineInRange("untrainedSpreadMultiplier", 2.20D, 1.0D, 4.0D);
        MASTERED_SPREAD_MULTIPLIER = builder.comment("Firearm spread multiplier at weapon proficiency level 10.")
                .defineInRange("masteredSpreadMultiplier", 1.0D, 0.5D, 2.0D);
        UNTRAINED_RECOIL_MULTIPLIER = builder.comment("Camera recoil multiplier for a character with no experience in that weapon class.")
                .defineInRange("untrainedRecoilMultiplier", 2.35D, 1.0D, 4.0D);
        MASTERED_RECOIL_MULTIPLIER = builder.comment("Camera recoil multiplier at weapon proficiency level 10.")
                .defineInRange("masteredRecoilMultiplier", 1.0D, 0.5D, 2.0D);
        UNTRAINED_AIM_SWAY_DEGREES = builder.comment("Maximum low-frequency aiming sway in degrees for a character with no weapon experience.")
                .defineInRange("untrainedAimSwayDegrees", 1.15D, 0.0D, 3.0D);
        MASTERED_AIM_SWAY_DEGREES = builder.comment("Aiming sway in degrees at weapon proficiency level 10.")
                .defineInRange("masteredAimSwayDegrees", 0.08D, 0.0D, 1.0D);
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
        MAX_SOUND_NOISE_SCANS_PER_TICK = builder.comment("Maximum server sound events converted to zombie noise per tick per dimension; chat, movement, and weapon adapters have their own throttles.")
                .defineInRange("maxSoundNoiseScansPerTick", 24, 1, 128);
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
