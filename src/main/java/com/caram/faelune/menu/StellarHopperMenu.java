package com.caram.faelune.menu;

import com.caram.faelune.block.entity.StellarHopperBlockEntity;
import com.caram.faelune.registry.ModMenus;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class StellarHopperMenu extends AbstractContainerMenu {
    public static final int FILTER_X = 62;
    public static final int FILTER_Y = 18;
    public static final int STORAGE_X = 44;
    public static final int STORAGE_Y = 42;
    public static final int INVENTORY_Y = 76;

    private static final int STORAGE_END = StellarHopperBlockEntity.CONTAINER_SIZE;
    private static final int FILTER_END = STORAGE_END + StellarHopperBlockEntity.FILTER_SIZE;

    private final Container hopper;
    private final Container filter;

    public StellarHopperMenu(int containerId, Inventory playerInventory) {
        this(
                containerId,
                playerInventory,
                new SimpleContainer(StellarHopperBlockEntity.CONTAINER_SIZE),
                new SimpleContainer(StellarHopperBlockEntity.FILTER_SIZE)
        );
    }

    public StellarHopperMenu(int containerId, Inventory playerInventory, Container hopper, Container filter) {
        super(ModMenus.STELLAR_HOPPER_MENU.get(), containerId);
        checkContainerSize(hopper, StellarHopperBlockEntity.CONTAINER_SIZE);
        checkContainerSize(filter, StellarHopperBlockEntity.FILTER_SIZE);
        this.hopper = hopper;
        this.filter = filter;
        hopper.startOpen(playerInventory.player);

        for (int i = 0; i < StellarHopperBlockEntity.CONTAINER_SIZE; i++) {
            this.addSlot(new Slot(hopper, i, STORAGE_X + i * 18, STORAGE_Y));
        }
        for (int i = 0; i < StellarHopperBlockEntity.FILTER_SIZE; i++) {
            this.addSlot(new FilterSlot(filter, i, FILTER_X + i * 18, FILTER_Y));
        }
        this.addStandardInventorySlots(playerInventory, 8, INVENTORY_Y);
    }

    public boolean isFilterSlot(Slot slot) {
        return slot.container == this.filter;
    }

    @Override
    public boolean stillValid(Player player) {
        return this.hopper.stillValid(player);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.hopper.stopOpen(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return result;
        }

        ItemStack stack = slot.getItem();
        result = stack.copy();
        if (index < FILTER_END) {
            if (!this.moveItemStackTo(stack, FILTER_END, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!this.moveItemStackTo(stack, 0, STORAGE_END, false)) {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return result;
    }

    private static class FilterSlot extends Slot {
        FilterSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }
}
