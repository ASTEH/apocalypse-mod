package dev.aftermath.content;

import dev.aftermath.AftermathMod;
import dev.aftermath.learning.TrainingManualItem;
import dev.aftermath.learning.TrainingTelevisionBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class AftermathContent {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(AftermathMod.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(AftermathMod.MOD_ID);

    public static final DeferredBlock<TrainingTelevisionBlock> TRAINING_TELEVISION = BLOCKS.registerBlock(
            "training_television",
            TrainingTelevisionBlock::new,
            BlockBehaviour.Properties.of().strength(1.5F).sound(SoundType.WOOD).noOcclusion());

    public static final DeferredItem<BlockItem> TRAINING_TELEVISION_ITEM =
            ITEMS.registerSimpleBlockItem(TRAINING_TELEVISION, new Item.Properties());

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

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
    }

    private AftermathContent() {}
}
