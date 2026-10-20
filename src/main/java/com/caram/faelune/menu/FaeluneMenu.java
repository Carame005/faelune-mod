package com.caram.faelune.menu;

import com.caram.faelune.entity.FaeluneEntity;
import com.caram.faelune.registry.ModMenus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class FaeluneMenu extends AbstractContainerMenu {
    public static final int BUTTON_TOGGLE_PICKUP = 0;
    public static final int BUTTON_TOGGLE_AUTO_HEAL = 1;

    private final FaeluneEntity faelune;

    public FaeluneMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buffer) {
        this(containerId, playerInventory, getFaeluneFromBuffer(playerInventory, buffer));
    }

    public FaeluneMenu(int containerId, Inventory playerInventory, FaeluneEntity faelune) {
        super(ModMenus.FAELUNE_MENU.get(), containerId);
        this.faelune = faelune;

        // Weapon/tool slot (same position as the donkey saddle slot)
        this.addSlot(new Slot(faelune.getInventory(), FaeluneEntity.WEAPON_SLOT, 8, 18) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return FaeluneEntity.isToolSlotItem(stack);
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });

        // Storage 5x3 (chested donkey layout), slots 1..15
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < FaeluneEntity.STORAGE_COLUMNS; col++) {
                int index = 1 + col + row * FaeluneEntity.STORAGE_COLUMNS;
                this.addSlot(new Slot(
                        faelune.getInventory(),
                        index,
                        80 + col * 18,
                        18 + row * 18
                ));
            }
        }

        // Chestplate and leggings, under the weapon slot
        for (int i = 0; i < FaeluneEntity.ARMOR_SLOTS.length; i++) {
            this.addSlot(new FaeluneArmorSlot(faelune, i, 8, 36 + i * 18));
        }

        // Player inventory
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }

        // Hotbar
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
        }
    }

    private static FaeluneEntity getFaeluneFromBuffer(Inventory playerInventory, FriendlyByteBuf buffer) {
        if (playerInventory.player.level().getEntity(buffer.readVarInt()) instanceof FaeluneEntity entity) {
            return entity;
        }
        throw new IllegalStateException("Faelune entity not found for menu");
    }

    public FaeluneEntity getFaelune() {
        return this.faelune;
    }

    @Override
    public boolean stillValid(Player player) {
        return this.faelune.isAlive()
                && this.faelune.isOwnedBy(player)
                && this.faelune.distanceToSqr(player) < 64.0;
    }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        if (!this.faelune.isOwnedBy(player)) {
            return false;
        }
        switch (buttonId) {
            case BUTTON_TOGGLE_PICKUP -> this.faelune.toggleCanPickupItems();
            case BUTTON_TOGGLE_AUTO_HEAL -> this.faelune.toggleAutoHeal();
            default -> {
                return false;
            }
        }
        return true;
    }

    /** Menu slot index of the armor slot that fits this item, or -1. */
    private int armorSlotFor(ItemStack stack) {
        for (int i = 0; i < FaeluneEntity.ARMOR_SLOTS.length; i++) {
            if (this.faelune.isEquippableInSlot(stack, FaeluneEntity.ARMOR_SLOTS[i])) {
                return FaeluneEntity.INVENTORY_SIZE + i;
            }
        }
        return -1;
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

        int faeluneSlots = FaeluneEntity.INVENTORY_SIZE + FaeluneEntity.ARMOR_SLOTS.length;
        int armorSlot = this.armorSlotFor(stack);
        if (index < faeluneSlots) {
            if (!this.moveItemStackTo(stack, faeluneSlots, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (armorSlot >= 0 && !this.slots.get(armorSlot).hasItem()) {
            if (!this.moveItemStackTo(stack, armorSlot, armorSlot + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (FaeluneEntity.isToolSlotItem(stack)) {
            if (!this.moveItemStackTo(stack, 0, 1, false)) {
                if (!this.moveItemStackTo(stack, 1, FaeluneEntity.INVENTORY_SIZE, false)) {
                    return ItemStack.EMPTY;
                }
            }
        } else if (!this.moveItemStackTo(stack, 1, FaeluneEntity.INVENTORY_SIZE, false)) {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }

        return result;
    }
}
