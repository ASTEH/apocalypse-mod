package dev.aftermath.content;

import dev.aftermath.AftermathMod;
import dev.aftermath.learning.TrainingManualItem;
import dev.aftermath.learning.TrainingVhsTapeItem;
import dev.aftermath.medical.BandageItem;
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
            BlockBehaviour.Properties.of().strength(1.5F).sound(SoundType.WOOD).noOcclusion());

    public static final DeferredItem<BlockItem> TRAINING_TELEVISION_ITEM =
            ITEMS.registerSimpleBlockItem(TRAINING_TELEVISION, new Item.Properties());

    public static final Supplier<BlockEntityType<TrainingTelevisionBlockEntity>> TRAINING_TELEVISION_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("training_television",
                    () -> BlockEntityType.Builder.of(TrainingTelevisionBlockEntity::new, TRAINING_TELEVISION.get()).build(null));

    public static final DeferredItem<BandageItem> BANDAGE = ITEMS.register(
            "bandage",
            () -> new BandageItem(new Item.Properties().stacksTo(16)));

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

    public static final DeferredItem<TrainingVhsTapeItem> VHS_TAPE = ITEMS.register(
            "vhs_tape", () -> new TrainingVhsTapeItem(new Item.Properties().stacksTo(1)));

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
    }

    private AftermathContent() {}
}
