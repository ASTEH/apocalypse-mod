package dev.aftermath.content;

import dev.aftermath.AftermathMod;
import dev.aftermath.learning.TrainingManualItem;
import dev.aftermath.learning.TrainingVhsTapeItem;
import dev.aftermath.learning.TrainingTelevisionBlock;
import dev.aftermath.learning.TrainingTelevisionBlockEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public final class AftermathContent {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(AftermathMod.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(AftermathMod.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, AftermathMod.MOD_ID);

    public static final DeferredBlock<TrainingTelevisionBlock> TRAINING_TELEVISION = BLOCKS.registerBlock(
            "training_television",
            TrainingTelevisionBlock::new,
            BlockBehaviour.Properties.of().strength(1.5F).sound(SoundType.WOOD));

    public static final DeferredItem<BlockItem> TRAINING_TELEVISION_ITEM =
            ITEMS.registerSimpleBlockItem(TRAINING_TELEVISION, new Item.Properties());

    public static final Supplier<BlockEntityType<TrainingTelevisionBlockEntity>> TRAINING_TELEVISION_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("training_television",
                    () -> BlockEntityType.Builder.of(TrainingTelevisionBlockEntity::new, TRAINING_TELEVISION.get()).build(null));

    public static final DeferredItem<TrainingManualItem> MANUAL_FIRST_AID = ITEMS.register(
            "training_manual_first_aid",
            () -> new TrainingManualItem(ResourceLocation.fromNamespaceAndPath(AftermathMod.MOD_ID, "first_aid"),
                    new Item.Properties().stacksTo(1)));

    public static final DeferredItem<TrainingManualItem> MANUAL_FIREARMS_SAFETY = ITEMS.register(
            "training_manual_firearms_safety",
            () -> new TrainingManualItem(ResourceLocation.fromNamespaceAndPath(AftermathMod.MOD_ID, "firearms_safety"),
                    new Item.Properties().stacksTo(1)));

    public static final DeferredItem<TrainingManualItem> MANUAL_MECHANICS = ITEMS.register(
            "training_manual_mechanics",
            () -> new TrainingManualItem(ResourceLocation.fromNamespaceAndPath(AftermathMod.MOD_ID, "mechanics_basics"),
                    new Item.Properties().stacksTo(1)));

    public static final DeferredItem<TrainingManualItem> MANUAL_COOKING = ITEMS.register(
            "training_manual_cooking",
            () -> new TrainingManualItem(ResourceLocation.fromNamespaceAndPath(AftermathMod.MOD_ID, "cooking_techniques"),
                    new Item.Properties().stacksTo(1)));

    public static final DeferredItem<TrainingManualItem> MANUAL_FARMING = ITEMS.register(
            "training_manual_farming",
            () -> new TrainingManualItem(ResourceLocation.fromNamespaceAndPath(AftermathMod.MOD_ID, "farming_fieldguide"),
                    new Item.Properties().stacksTo(1)));

    public static final DeferredItem<TrainingManualItem> MANUAL_DRIVING = ITEMS.register(
            "training_manual_driving",
            () -> new TrainingManualItem(ResourceLocation.fromNamespaceAndPath(AftermathMod.MOD_ID, "driving_maintenance"),
                    new Item.Properties().stacksTo(1)));

    public static final DeferredItem<TrainingManualItem> MANUAL_SCAVENGING = ITEMS.register(
            "training_manual_scavenging",
            () -> new TrainingManualItem(ResourceLocation.fromNamespaceAndPath(AftermathMod.MOD_ID, "urban_scavenging"),
                    new Item.Properties().stacksTo(1)));

    public static final DeferredItem<TrainingManualItem> MANUAL_MELEE = ITEMS.register(
            "training_manual_melee",
            () -> new TrainingManualItem(ResourceLocation.fromNamespaceAndPath(AftermathMod.MOD_ID, "melee_survival"),
                    new Item.Properties().stacksTo(1)));

    public static final DeferredItem<TrainingVhsTapeItem> VHS_TAPE = ITEMS.register(
            "vhs_tape", () -> new TrainingVhsTapeItem(new Item.Properties().stacksTo(1)));

    /** A basic forage ingredient only; it does not apply medical effects or replace the medical mods. */
    public static final DeferredItem<Item> WILD_HERBS = ITEMS.register(
            "wild_herbs", () -> new Item(new Item.Properties().stacksTo(16)));

    /** A one-use garment reinforcement kit made from vanilla wool and string. */
    public static final DeferredItem<Item> CLOTHING_PATCH = ITEMS.register(
            "clothing_patch", () -> new Item(new Item.Properties().stacksTo(16)));

    public static final DeferredBlock<NoiseDecoyBlock> RADIO_DECOY = BLOCKS.registerBlock(
            "radio_decoy",
            properties -> new NoiseDecoyBlock(properties,
                    ResourceLocation.fromNamespaceAndPath(AftermathMod.MOD_ID, "radio_decoy"), 60),
            BlockBehaviour.Properties.of().strength(1.2F).sound(SoundType.METAL).noOcclusion());

    public static final DeferredBlock<NoiseDecoyBlock> ALARM_CLOCK_DECOY = BLOCKS.registerBlock(
            "alarm_clock_decoy",
            properties -> new NoiseDecoyBlock(properties,
                    ResourceLocation.fromNamespaceAndPath(AftermathMod.MOD_ID, "alarm_clock_decoy"), 40),
            BlockBehaviour.Properties.of().strength(0.8F).sound(SoundType.METAL).noOcclusion());

    public static final DeferredItem<BlockItem> RADIO_DECOY_ITEM =
            ITEMS.registerSimpleBlockItem(RADIO_DECOY, new Item.Properties());
    public static final DeferredItem<BlockItem> ALARM_CLOCK_DECOY_ITEM =
            ITEMS.registerSimpleBlockItem(ALARM_CLOCK_DECOY, new Item.Properties());

    public static final Supplier<BlockEntityType<NoiseDecoyBlockEntity>> NOISE_DECOY_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("noise_decoy",
                    () -> BlockEntityType.Builder.of(NoiseDecoyBlockEntity::new,
                            RADIO_DECOY.get(), ALARM_CLOCK_DECOY.get()).build(null));

    public static final DeferredItem<NoiseBatteryItem> NOISE_BATTERY = ITEMS.register(
            "noise_battery", () -> new NoiseBatteryItem(new Item.Properties().stacksTo(16)));

    public static final DeferredItem<ZombieDisguiseItem> ZOMBIE_DISGUISE = ITEMS.register(
            "zombie_disguise", () -> new ZombieDisguiseItem(new Item.Properties().stacksTo(16)));

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
    }

    private AftermathContent() {}
}
