package com.caram.faelune.item;

import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.TooltipDisplay;
import org.jspecify.annotations.Nullable;

/**
 * Right-click your Faelune to tune it, then right-click a Stellar Beacon to link it.
 * Sneak + right-click a linked Faelune to unlink it.
 */
public class StellarTunerItem extends Item {
    private static final String TAG_ID = "FaeluneId";
    private static final String TAG_NAME = "FaeluneName";

    public StellarTunerItem(Item.Properties properties) {
        super(properties);
    }

    public static void storeFaelune(ItemStack stack, UUID id, Component name) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.putString(TAG_ID, id.toString());
            tag.putString(TAG_NAME, name.getString());
        });
    }

    public static void clearStoredFaelune(ItemStack stack) {
        stack.remove(DataComponents.CUSTOM_DATA);
    }

    public static @Nullable UUID getStoredFaelune(ItemStack stack) {
        String raw = getTag(stack).getStringOr(TAG_ID, "");
        if (raw.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static CompoundTag getTag(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? new CompoundTag() : data.copyTag();
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return getStoredFaelune(stack) != null;
    }

    @Override
    public void appendHoverText(
            ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag
    ) {
        String name = getTag(stack).getStringOr(TAG_NAME, "");
        if (getStoredFaelune(stack) != null) {
            builder.accept(Component.translatable("tooltip.faelune.tuner.stored", name).withStyle(ChatFormatting.LIGHT_PURPLE));
            builder.accept(Component.translatable("tooltip.faelune.tuner.use_beacon").withStyle(ChatFormatting.GRAY));
        } else {
            builder.accept(Component.translatable("tooltip.faelune.tuner.use_faelune").withStyle(ChatFormatting.GRAY));
            builder.accept(Component.translatable("tooltip.faelune.tuner.unlink").withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
