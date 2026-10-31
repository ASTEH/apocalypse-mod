package dev.aftermath.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server-authoritative settings. Gameplay options stay in the server config.
 * Firearm handling defaults are intentionally noticeable at level 0 and become fully trained at level 10.
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
    public static final ModConfigSpec.BooleanValue ENABLE_HOSTILE_SPAWN_FILTER;
    public static final ModConfigSpec.BooleanValue ENABLE_SPAWNER_REMOVAL;
    public static final ModConfigSpec.BooleanValue ENABLE_ZOMBIE_VARIANTS;
    public static final ModConfigSpec.BooleanValue ENABLE_ZOMBIE_NIGHT_ONLY_SPAWNING;
    public static final ModConfigSpec.BooleanValue ENABLE_MILITARY_SPAWN_CONTROL;
    public static final ModConfigSpec.DoubleValue MILITARY_EVENT_CHANCE_MULTIPLIER;

    public static final ModConfigSpec.IntValue WEAPON_XP_PER_KILL;
    public static final ModConfigSpec.IntValue MAX_XP_PER_VICTIM_PER_MINUTE;
    public static final ModConfigSpec.DoubleValue UNTRAINED_FIREARM_SPREAD;
    public static final ModConfigSpec.DoubleValue UNTRAINED_FIREARM_RELOAD_DURATION;
    public static final ModConfigSpec.DoubleValue UNTRAINED_FIREARM_AIM_TIME;
    public static final ModConfigSpec.DoubleValue UNTRAINED_FIREARM_RECOIL_PITCH;
    public static final ModConfigSpec.DoubleValue UNTRAINED_FIREARM_RECOIL_YAW;
    public static final ModConfigSpec.DoubleValue UNTRAINED_SHOTGUN_HANDLING_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue UNTRAINED_HEAVY_HANDLING_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue UNTRAINED_SNIPER_HANDLING_MULTIPLIER;
    public static final ModConfigSpec.IntValue DEFAULT_NOISE_RADIUS;
    public static final ModConfigSpec.IntValue MAX_HORDE_SIZE;
    public static final ModConfigSpec.DoubleValue HORDE_SURROUND_RADIUS;
    public static final ModConfigSpec.IntValue AI_RECALC_INTERVAL_TICKS;
    public static final ModConfigSpec.IntValue MAX_NOISE_TARGETS;
    public static final ModConfigSpec.IntValue MAX_PATH_REQUESTS_PER_TICK;
    public static final ModConfigSpec.BooleanValue ENABLE_FORAGING;
    public static final ModConfigSpec.DoubleValue FORAGING_BASE_RADIUS;
    public static final ModConfigSpec.DoubleValue FORAGING_RADIUS_PER_SKILL_LEVEL;
    public static final ModConfigSpec.DoubleValue FORAGING_MAX_BASE_RADIUS;
    public static final ModConfigSpec.DoubleValue FORAGING_PROFESSION_RADIUS_BONUS;
    public static final ModConfigSpec.DoubleValue FORAGING_TRAIT_RADIUS_BONUS;
    public static final ModConfigSpec.DoubleValue FORAGING_SPOT_CHANCE;
    public static final ModConfigSpec.DoubleValue FORAGING_FOCUS_WEIGHT_MULTIPLIER;
    public static final ModConfigSpec.IntValue FORAGING_SCAN_INTERVAL_TICKS;


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
        ENABLE_HOSTILE_SPAWN_FILTER = builder.comment("Block unlisted hostile mob spawns using the world_rules/spawn_control.json datapack policy.")
                .define("enableHostileSpawnFilter", true);
        ENABLE_SPAWNER_REMOVAL = builder.comment("Remove configured vanilla spawner blocks when their chunks finish loading.")
                .define("enableSpawnerRemoval", true);
        ENABLE_ZOMBIE_VARIANTS = builder.comment("Enable weighted, datapack-driven zombie stat and sense archetypes.")
                .define("enableZombieVariants", true);
        ENABLE_ZOMBIE_NIGHT_ONLY_SPAWNING = builder.comment("Allow new zombie-like hostile mobs to spawn in the Overworld only at night. Manual commands, spawn eggs and transformations are unaffected.")
                .define("enableZombieNightOnlySpawning", true);
        ENABLE_MILITARY_SPAWN_CONTROL = builder.comment("Reduce the frequency of TaCZ: Simple Enemy dynamic military events when that optional mod is installed.")
                .define("enableMilitarySpawnControl", true);
        MILITARY_EVENT_CHANCE_MULTIPLIER = builder.comment("Fraction of TaCZ: Simple Enemy base chances retained by military_patrol, far_combat, and cave_extraction. 0.15 means 15% of their normal configured chance.")
                .defineInRange("militaryEventChanceMultiplier", 0.15D, 0.0D, 1.0D);
        ENABLE_FORAGING = builder.comment("Enable the Project Zomboid-inspired scavenging search mode.")
                .define("enableForaging", true);
        builder.pop();

        builder.push("weapon_progression");
        WEAPON_XP_PER_KILL = builder.comment("Weapon XP per eligible kill. With 10 XP per zombie kill, the 6500 XP level-10 threshold requires about 650 kills per weapon class.")
                .defineInRange("weaponXpPerKill", 10, 0, 10000);
        MAX_XP_PER_VICTIM_PER_MINUTE = builder.comment("Safety cap on XP awarded for one victim. Keep this at or above weaponXpPerKill for the normal 650-kill progression.")
                .defineInRange("maxXpPerVictimPerMinute", 10, 0, 10000);
        UNTRAINED_FIREARM_SPREAD = builder.comment("Additional TaCZ projectile spread at firearm level 0, before weapon-class scaling. Reaches zero at level 10.")
                .defineInRange("untrainedFirearmSpread", 0.65D, 0.0D, 5.0D);
        UNTRAINED_FIREARM_RELOAD_DURATION = builder.comment("Reload duration multiplier at firearm level 0, before weapon-class scaling. Reaches 1.0 at level 10; Quick Reload is applied afterward.")
                .defineInRange("untrainedFirearmReloadDuration", 1.75D, 1.0D, 3.0D);
        UNTRAINED_FIREARM_AIM_TIME = builder.comment("Aim transition time multiplier at firearm level 0, before weapon-class scaling. Reaches 1.0 at level 10.")
                .defineInRange("untrainedFirearmAimTime", 1.65D, 1.0D, 3.0D);
        UNTRAINED_FIREARM_RECOIL_PITCH = builder.comment("Additional client camera pitch kick in degrees per successful TaCZ trigger at firearm level 0.")
                .defineInRange("untrainedFirearmRecoilPitch", 2.0D, 0.0D, 10.0D);
        UNTRAINED_FIREARM_RECOIL_YAW = builder.comment("Maximum additional random horizontal camera kick in degrees per successful TaCZ trigger at firearm level 0.")
                .defineInRange("untrainedFirearmRecoilYaw", 0.8D, 0.0D, 10.0D);
        UNTRAINED_SHOTGUN_HANDLING_MULTIPLIER = builder.comment("Extra untrained handling penalty for shotguns. Affects spread, reload time, aiming and camera recoil; fades out by level 10.")
                .defineInRange("untrainedShotgunHandlingMultiplier", 1.25D, 1.0D, 3.0D);
        UNTRAINED_HEAVY_HANDLING_MULTIPLIER = builder.comment("Extra untrained handling penalty for heavy weapons. Affects spread, reload time, aiming and camera recoil; fades out by level 10.")
                .defineInRange("untrainedHeavyHandlingMultiplier", 1.40D, 1.0D, 3.0D);
        UNTRAINED_SNIPER_HANDLING_MULTIPLIER = builder.comment("Extra untrained handling penalty for sniper rifles. Affects spread, reload time, aiming and camera recoil; fades out by level 10.")
                .defineInRange("untrainedSniperHandlingMultiplier", 1.20D, 1.0D, 3.0D);
        builder.pop();

        builder.push("noise_and_hordes");
        DEFAULT_NOISE_RADIUS = builder.comment("Fallback radius for a noise event when its data definition has no radius.")
                .defineInRange("defaultNoiseRadius", 64, 0, 512);
        MAX_HORDE_SIZE = builder.comment("Hard safety cap for Aftermath-managed horde members.")
                .defineInRange("maxHordeSize", 24, 1, 128);
        HORDE_SURROUND_RADIUS = builder.comment("Base radius, in blocks, used to position grouped zombies around the player who produced a noise event.")
                .defineInRange("hordeSurroundRadius", 4.0D, 2.0D, 12.0D);
        AI_RECALC_INTERVAL_TICKS = builder.comment("Minimum interval between Aftermath path/target recalculations for an entity.")
                .defineInRange("aiRecalcIntervalTicks", 40, 5, 1200);
        MAX_NOISE_TARGETS = builder.comment("Maximum already-loaded eligible mobs assigned to one noise event.")
                .defineInRange("maxNoiseTargetsPerEvent", 32, 1, 128);
        MAX_PATH_REQUESTS_PER_TICK = builder.comment("Global Aftermath path requests allowed per tick in each dimension.")
                .defineInRange("maxPathRequestsPerTick", 16, 1, 256);
        builder.pop();

        builder.push("foraging");
        FORAGING_BASE_RADIUS = builder.comment("Minimum scavenging search radius before skill progression.")
                .defineInRange("baseRadius", 3.0D, 3.0D, 10.0D);
        FORAGING_RADIUS_PER_SKILL_LEVEL = builder.comment("Additional search radius per scavenging skill level.")
                .defineInRange("radiusPerSkillLevel", 0.7D, 0.0D, 1.5D);
        FORAGING_MAX_BASE_RADIUS = builder.comment("Maximum skill-derived radius before profession and trait bonuses.")
                .defineInRange("maxBaseRadius", 10.0D, 3.0D, 10.0D);
        FORAGING_PROFESSION_RADIUS_BONUS = builder.comment("Extra radius for the Scavenger profession.")
                .defineInRange("professionRadiusBonus", 2.5D, 0.0D, 5.0D);
        FORAGING_TRAIT_RADIUS_BONUS = builder.comment("Extra radius for the Keen Eye scavenger trait.")
                .defineInRange("traitRadiusBonus", 2.5D, 0.0D, 5.0D);
        FORAGING_SPOT_CHANCE = builder.comment("Chance that a five-block search cell contains one natural forage point.")
                .defineInRange("spotChance", 0.32D, 0.01D, 1.0D);
        FORAGING_FOCUS_WEIGHT_MULTIPLIER = builder.comment("Loot weight multiplier for the selected search category; other categories remain possible.")
                .defineInRange("focusWeightMultiplier", 1.8D, 1.0D, 5.0D);
        FORAGING_SCAN_INTERVAL_TICKS = builder.comment("Minimum player tick interval between search area refreshes.")
                .defineInRange("scanIntervalTicks", 10, 5, 100);
        builder.pop();

        SPEC = builder.build();
    }

    private AftermathServerConfig() {}
}
