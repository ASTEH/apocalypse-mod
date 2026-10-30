package dev.aftermath.learning;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public final class TrainingVhsTapeItem extends Item {
    public static final String DATA_KEY = "VhsTapeId";

    public TrainingVhsTapeItem(Properties properties) {
        super(properties);
    }

    public static ResourceLocation readTapeId(ItemStack stack) {
        CustomData data = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        if (data == null) return null;
        CompoundTag tag = data.copyTag();
        return tag.contains(DATA_KEY) ? ResourceLocation.tryParse(tag.getString(DATA_KEY)) : null;
    }
}
