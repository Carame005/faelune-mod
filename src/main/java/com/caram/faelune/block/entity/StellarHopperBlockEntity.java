package com.caram.faelune.block.entity;

import com.caram.faelune.menu.StellarHopperMenu;
import com.caram.faelune.registry.ModBlockEntities;
import java.util.function.BooleanSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.Hopper;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.VanillaInventoryCodeHooks;

/**
 * Hopper logic mirrors {@link HopperBlockEntity}. The filter lives in a separate container so that other
 * hoppers and pipes only ever see the 5 storage slots; it is enforced through {@link #canPlaceItem}, which
 * every insertion path (sucking from above, item entities, being pushed into) checks.
 */
public class StellarHopperBlockEntity extends BaseContainerBlockEntity implements Hopper {
    public static final int CONTAINER_SIZE = HopperBlockEntity.HOPPER_CONTAINER_SIZE;
    public static final int FILTER_SIZE = 3;
    /** Ticks between transfers; the vanilla hopper uses 8. */
    public static final int TRANSFER_COOLDOWN = 2;
    private static final int NO_COOLDOWN_TIME = -1;

    private static final Component DEFAULT_NAME = Component.translatable("container.faelune.stellar_hopper");

    private NonNullList<ItemStack> items = NonNullList.withSize(CONTAINER_SIZE, ItemStack.EMPTY);
    private final SimpleContainer filter = new SimpleContainer(FILTER_SIZE) {
        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public void setChanged() {
            super.setChanged();
            StellarHopperBlockEntity.this.setChanged();
        }
    };
    private int cooldownTime = NO_COOLDOWN_TIME;

    public StellarHopperBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.STELLAR_HOPPER.get(), pos, state);
    }

    public static void pushItemsTick(Level level, BlockPos pos, BlockState state, StellarHopperBlockEntity hopper) {
        hopper.cooldownTime--;
        if (!hopper.isOnCooldown()) {
            hopper.cooldownTime = 0;
            hopper.tryMoveItems(level, pos, state, () -> HopperBlockEntity.suckInItems(level, hopper));
        }
    }

    public static void entityInside(Level level, BlockPos pos, BlockState state, Entity entity, StellarHopperBlockEntity hopper) {
        if (entity instanceof ItemEntity itemEntity
                && !itemEntity.getItem().isEmpty()
                && entity.getBoundingBox().move(-pos.getX(), -pos.getY(), -pos.getZ()).intersects(hopper.getSuckAabb())) {
            hopper.tryMoveItems(level, pos, state, () -> HopperBlockEntity.addItem(hopper, itemEntity));
        }
    }

    private boolean tryMoveItems(Level level, BlockPos pos, BlockState state, BooleanSupplier pullAction) {
        if (level.isClientSide() || this.isOnCooldown() || !state.getValue(HopperBlock.ENABLED)) {
            return false;
        }

        boolean changed = false;
        if (!this.isEmpty()) {
            changed = this.ejectItems(level, pos, state);
        }
        if (this.hasFilter() && !this.inventoryFull()) {
            changed |= pullAction.getAsBoolean();
        }

        if (changed) {
            this.cooldownTime = TRANSFER_COOLDOWN;
            setChanged(level, pos, state);
            return true;
        }
        return false;
    }

    private boolean ejectItems(Level level, BlockPos pos, BlockState state) {
        Direction facing = state.getValue(HopperBlock.FACING);
        BlockPos targetPos = pos.relative(facing);
        Direction side = facing.getOpposite();

        IItemHandler handler = VanillaInventoryCodeHooks.getItemHandler(level, targetPos, side).orElse(null);
        if (handler != null) {
            return this.ejectInto(handler);
        }
        Container container = HopperBlockEntity.getContainerAt(level, targetPos);
        return container != null && this.ejectInto(container, side);
    }

    private boolean ejectInto(IItemHandler handler) {
        for (int slot = 0; slot < CONTAINER_SIZE; slot++) {
            ItemStack stack = this.items.get(slot);
            if (stack.isEmpty()) {
                continue;
            }
            ItemStack single = stack.copyWithCount(1);
            if (ItemHandlerHelper.insertItem(handler, single, true).isEmpty()) {
                ItemHandlerHelper.insertItem(handler, single, false);
                this.removeItem(slot, 1);
                return true;
            }
        }
        return false;
    }

    private boolean ejectInto(Container container, Direction side) {
        for (int slot = 0; slot < CONTAINER_SIZE; slot++) {
            ItemStack stack = this.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            int originalCount = stack.getCount();
            ItemStack result = HopperBlockEntity.addItem(this, container, this.removeItem(slot, 1), side);
            if (result.isEmpty()) {
                container.setChanged();
                return true;
            }
            stack.setCount(originalCount);
            if (originalCount == 1) {
                this.setItem(slot, stack);
            }
        }
        return false;
    }

    private boolean inventoryFull() {
        for (ItemStack stack : this.items) {
            if (stack.isEmpty() || stack.getCount() != stack.getMaxStackSize()) {
                return false;
            }
        }
        return true;
    }

    private boolean isOnCooldown() {
        return this.cooldownTime > 0;
    }

    public boolean hasFilter() {
        return !this.filter.isEmpty();
    }

    /** An empty filter accepts nothing, so an unconfigured hopper never swallows items by accident. */
    public boolean matchesFilter(ItemStack stack) {
        for (int i = 0; i < FILTER_SIZE; i++) {
            ItemStack filterStack = this.filter.getItem(i);
            if (!filterStack.isEmpty() && ItemStack.isSameItem(filterStack, stack)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return this.matchesFilter(stack);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (this.level != null) {
            Containers.dropContents(this.level, pos, this.filter);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, this.items);
        ContainerHelper.saveAllItems(output.child("Filter"), this.filter.getItems());
        output.putInt("TransferCooldown", this.cooldownTime);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.items = NonNullList.withSize(CONTAINER_SIZE, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, this.items);
        this.filter.getItems().clear();
        ContainerHelper.loadAllItems(input.childOrEmpty("Filter"), this.filter.getItems());
        this.cooldownTime = input.getIntOr("TransferCooldown", NO_COOLDOWN_TIME);
    }

    @Override
    public double getLevelX() {
        return this.worldPosition.getX() + 0.5;
    }

    @Override
    public double getLevelY() {
        return this.worldPosition.getY() + 0.5;
    }

    @Override
    public double getLevelZ() {
        return this.worldPosition.getZ() + 0.5;
    }

    @Override
    public boolean isGridAligned() {
        return true;
    }

    @Override
    public int getContainerSize() {
        return CONTAINER_SIZE;
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return this.items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        this.items = items;
    }

    @Override
    protected Component getDefaultName() {
        return DEFAULT_NAME;
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return new StellarHopperMenu(containerId, inventory, this, this.filter);
    }
}
