package com.caram.faelune.menu;

import com.caram.faelune.entity.FaeluneEntity;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** One of Faelune's armor slots (chestplate or leggings). */
public class FaeluneArmorSlot extends Slot {
    private final FaeluneEntity faelune;
    private final EquipmentSlot equipmentSlot;

    public FaeluneArmorSlot(FaeluneEntity faelune, int armorIndex, int x, int y) {
        super(faelune.getArmorInventory(), armorIndex, x, y);
        this.faelune = faelune;
        this.equipmentSlot = FaeluneEntity.ARMOR_SLOTS[armorIndex];
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return this.faelune.isEquippableInSlot(stack, this.equipmentSlot);
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public Identifier getNoItemIcon() {
        return this.equipmentSlot == EquipmentSlot.CHEST
                ? InventoryMenu.EMPTY_ARMOR_SLOT_CHESTPLATE
                : InventoryMenu.EMPTY_ARMOR_SLOT_LEGGINGS;
    }
}
