package dev.aftermath.learning;

import dev.aftermath.content.AftermathContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class TrainingTelevisionBlockEntity extends BlockEntity {
    private static final String CASSETTE_TAG = "Cassette";
    private ItemStack cassette = ItemStack.EMPTY;

    public TrainingTelevisionBlockEntity(BlockPos pos, BlockState state) {
        super(AftermathContent.TRAINING_TELEVISION_BLOCK_ENTITY.get(), pos, state);
    }

    public boolean hasCassette() {
        return !cassette.isEmpty();
    }

    public ItemStack getCassette() {
        return cassette.copy();
    }

    public boolean insertCassette(ItemStack stack) {
        if (!cassette.isEmpty() || stack.isEmpty() || !stack.is(AftermathContent.VHS_TAPE.get())) {
            return false;
        }
        cassette = stack.copy();
        cassette.setCount(1);
        markUpdated();
        return true;
    }

    public ItemStack removeCassette() {
        if (cassette.isEmpty()) return ItemStack.EMPTY;
        ItemStack removed = cassette;
        cassette = ItemStack.EMPTY;
        markUpdated();
        return removed;
    }

    public boolean matchesCassette(ResourceLocation itemId, ResourceLocation tapeId) {
        if (cassette.isEmpty() || itemId == null || tapeId == null) return false;
        ResourceLocation storedItemId = BuiltInRegistries.ITEM.getKey(cassette.getItem());
        return itemId.equals(storedItemId) && tapeId.equals(TrainingVhsTapeItem.readTapeId(cassette));
    }

    public boolean consumeCassette(ResourceLocation itemId, ResourceLocation tapeId) {
        if (!matchesCassette(itemId, tapeId)) return false;
        cassette.shrink(1);
        if (cassette.isEmpty()) cassette = ItemStack.EMPTY;
        markUpdated();
        return true;
    }

    private void markUpdated() {
        setChanged();
        if (level != null) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!cassette.isEmpty()) tag.put(CASSETTE_TAG, cassette.save(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        cassette = tag.contains(CASSETTE_TAG)
                ? ItemStack.parseOptional(registries, tag.getCompound(CASSETTE_TAG))
                : ItemStack.EMPTY;
        if (!cassette.is(AftermathContent.VHS_TAPE.get())) cassette = ItemStack.EMPTY;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
