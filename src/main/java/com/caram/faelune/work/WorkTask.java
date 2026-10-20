package com.caram.faelune.work;

import com.caram.faelune.entity.FaeluneEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;

public enum WorkTask {
    NONE("none"),
    LUMBERJACK("lumberjack"),
    FARMER("farmer"),
    GUARD("guard"),
    COLLECTOR("collector");

    private static final WorkTask[] VALUES = values();

    private final String name;

    WorkTask(String name) {
        this.name = name;
    }

    public static WorkTask byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : NONE;
    }

    public int id() {
        return this.ordinal();
    }

    public String getSerializedName() {
        return this.name;
    }

    public Component displayName() {
        return Component.translatable("work.faelune.task." + this.name);
    }

    public Component description() {
        return Component.translatable("work.faelune.task." + this.name + ".desc");
    }

    public boolean hasRequiredTool(ItemStack tool) {
        return switch (this) {
            case NONE, COLLECTOR -> true;
            case LUMBERJACK -> tool.is(ItemTags.AXES);
            case FARMER -> tool.is(ItemTags.HOES);
            case GUARD -> FaeluneEntity.isWeapon(tool);
        };
    }

    /** Items Faelune keeps (one stack) instead of depositing, so she can replant. */
    public boolean isSupply(ItemStack stack) {
        return switch (this) {
            case LUMBERJACK -> isSapling(stack);
            case FARMER -> isSeed(stack);
            default -> false;
        };
    }

    public static boolean isSapling(ItemStack stack) {
        return stack.is(ItemTags.SAPLINGS) && Block.byItem(stack.getItem()) != Blocks.AIR;
    }

    public static boolean isSeed(ItemStack stack) {
        return !stack.isEmpty() && Block.byItem(stack.getItem()) instanceof CropBlock;
    }

    /** Icon shown on the station buttons. */
    public ItemStack icon() {
        return switch (this) {
            case NONE -> new ItemStack(Items.COOKIE);
            case LUMBERJACK -> new ItemStack(Items.IRON_AXE);
            case FARMER -> new ItemStack(Items.IRON_HOE);
            case GUARD -> new ItemStack(Items.IRON_SWORD);
            case COLLECTOR -> new ItemStack(Items.HOPPER);
        };
    }
}
