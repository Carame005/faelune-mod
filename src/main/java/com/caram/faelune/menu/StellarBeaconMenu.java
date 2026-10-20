package com.caram.faelune.menu;

import com.caram.faelune.block.entity.StellarBeaconBlockEntity;
import com.caram.faelune.registry.ModMenus;
import com.caram.faelune.work.WorkTask;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class StellarBeaconMenu extends AbstractContainerMenu {
    /** Buttons 0..N-1 select a {@link WorkTask} by id. */
    public static final int BUTTON_RADIUS_DOWN = 10;
    public static final int BUTTON_RADIUS_UP = 11;
    public static final int BUTTON_UNLINK_ALL = 12;

    public static final int STORAGE_TOP = 64;
    public static final int INVENTORY_TOP = 134;

    private final Container container;
    private final ContainerData data;
    private final BlockPos pos;

    public StellarBeaconMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buffer) {
        this(
                containerId,
                playerInventory,
                new SimpleContainer(StellarBeaconBlockEntity.CONTAINER_SIZE),
                new SimpleContainerData(StellarBeaconBlockEntity.DATA_COUNT),
                buffer.readBlockPos()
        );
    }

    public StellarBeaconMenu(int containerId, Inventory playerInventory, Container container, ContainerData data, BlockPos pos) {
        super(ModMenus.STELLAR_BEACON_MENU.get(), containerId);
        checkContainerSize(container, StellarBeaconBlockEntity.CONTAINER_SIZE);
        checkContainerDataCount(data, StellarBeaconBlockEntity.DATA_COUNT);
        this.container = container;
        this.data = data;
        this.pos = pos;
        container.startOpen(playerInventory.player);

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(container, col + row * 9, 8 + col * 18, STORAGE_TOP + row * 18));
            }
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, INVENTORY_TOP + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, INVENTORY_TOP + 58));
        }

        this.addDataSlots(data);
    }

    public WorkTask getTask() {
        return WorkTask.byId(this.data.get(StellarBeaconBlockEntity.DATA_TASK));
    }

    public int getRadius() {
        return this.data.get(StellarBeaconBlockEntity.DATA_RADIUS);
    }

    public int getLinkedCount() {
        return this.data.get(StellarBeaconBlockEntity.DATA_LINKED);
    }

    public int getMissingToolCount() {
        return this.data.get(StellarBeaconBlockEntity.DATA_MISSING_TOOL);
    }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        if (!(player.level().getBlockEntity(this.pos) instanceof StellarBeaconBlockEntity beacon)) {
            return false;
        }

        if (buttonId >= 0 && buttonId < WorkTask.values().length) {
            beacon.setTask(WorkTask.byId(buttonId));
        } else if (buttonId == BUTTON_RADIUS_DOWN) {
            beacon.setRadius(beacon.getRadius() - 1);
        } else if (buttonId == BUTTON_RADIUS_UP) {
            beacon.setRadius(beacon.getRadius() + 1);
        } else if (buttonId == BUTTON_UNLINK_ALL) {
            beacon.unlinkAll(player.level());
        } else {
            return false;
        }

        player.level().playSound(null, this.pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.8f, 1.4f);
        return true;
    }

    @Override
    public boolean stillValid(Player player) {
        return this.container.stillValid(player);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.container.stopOpen(player);
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
        int storageSlots = StellarBeaconBlockEntity.CONTAINER_SIZE;
        if (index < storageSlots) {
            if (!this.moveItemStackTo(stack, storageSlots, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!this.moveItemStackTo(stack, 0, storageSlots, false)) {
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
