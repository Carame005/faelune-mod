package com.caram.faelune.block.entity;

import com.caram.faelune.entity.FaeluneEntity;
import com.caram.faelune.menu.StellarBeaconMenu;
import com.caram.faelune.registry.ModBlockEntities;
import com.caram.faelune.work.WorkTask;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;

public class StellarBeaconBlockEntity extends BaseContainerBlockEntity {
    public static final int CONTAINER_SIZE = 27;
    public static final int MIN_RADIUS = 4;
    public static final int MAX_RADIUS = 16;
    public static final int DEFAULT_RADIUS = 8;
    /** How far away a linked Faelune is still counted/managed by this station. */
    public static final double LINK_SCAN_RANGE = 64.0;

    public static final int DATA_TASK = 0;
    public static final int DATA_RADIUS = 1;
    public static final int DATA_LINKED = 2;
    public static final int DATA_MISSING_TOOL = 3;
    public static final int DATA_COUNT = 4;

    private static final Component DEFAULT_NAME = Component.translatable("container.faelune.stellar_beacon");

    private NonNullList<ItemStack> items = NonNullList.withSize(CONTAINER_SIZE, ItemStack.EMPTY);
    private WorkTask task = WorkTask.NONE;
    private int radius = DEFAULT_RADIUS;
    private int linkedCount;
    private int missingToolCount;

    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_TASK -> StellarBeaconBlockEntity.this.task.id();
                case DATA_RADIUS -> StellarBeaconBlockEntity.this.radius;
                case DATA_LINKED -> StellarBeaconBlockEntity.this.linkedCount;
                case DATA_MISSING_TOOL -> StellarBeaconBlockEntity.this.missingToolCount;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case DATA_TASK -> StellarBeaconBlockEntity.this.task = WorkTask.byId(value);
                case DATA_RADIUS -> StellarBeaconBlockEntity.this.radius = Mth.clamp(value, MIN_RADIUS, MAX_RADIUS);
                case DATA_LINKED -> StellarBeaconBlockEntity.this.linkedCount = value;
                case DATA_MISSING_TOOL -> StellarBeaconBlockEntity.this.missingToolCount = value;
                default -> {
                }
            }
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    public StellarBeaconBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.STELLAR_BEACON.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, StellarBeaconBlockEntity beacon) {
        if (level.getGameTime() % 20L == 0L && level instanceof ServerLevel serverLevel) {
            beacon.refreshLinkStats(serverLevel);
        }
    }

    private void refreshLinkStats(ServerLevel level) {
        List<FaeluneEntity> linked = this.findLinkedFaelunes(level);
        int missing = 0;
        for (FaeluneEntity faelune : linked) {
            if (!this.task.hasRequiredTool(faelune.getToolStack())) {
                missing++;
            }
        }
        this.linkedCount = linked.size();
        this.missingToolCount = missing;
    }

    public List<FaeluneEntity> findLinkedFaelunes(Level level) {
        BlockPos pos = this.getBlockPos();
        return level.getEntitiesOfClass(
                FaeluneEntity.class,
                new AABB(pos).inflate(LINK_SCAN_RANGE),
                faelune -> faelune.isAlive() && pos.equals(faelune.getStationPos())
        );
    }

    public void unlinkAll(Level level) {
        for (FaeluneEntity faelune : this.findLinkedFaelunes(level)) {
            faelune.clearStation();
        }
        if (level instanceof ServerLevel serverLevel) {
            this.refreshLinkStats(serverLevel);
        }
    }

    public WorkTask getTask() {
        return this.task;
    }

    public void setTask(WorkTask task) {
        this.task = task;
        this.setChanged();
    }

    public int getRadius() {
        return this.radius;
    }

    public void setRadius(int radius) {
        this.radius = Mth.clamp(radius, MIN_RADIUS, MAX_RADIUS);
        this.setChanged();
    }

    /** Work area: horizontal radius around the beacon, a few blocks down and a tree's height up. */
    public AABB getWorkArea() {
        BlockPos pos = this.getBlockPos();
        return new AABB(
                pos.getX() - this.radius, pos.getY() - 4, pos.getZ() - this.radius,
                pos.getX() + this.radius + 1, pos.getY() + 16, pos.getZ() + this.radius + 1
        );
    }

    public boolean isInWorkArea(BlockPos target) {
        BlockPos pos = this.getBlockPos();
        return Math.abs(target.getX() - pos.getX()) <= this.radius
                && Math.abs(target.getZ() - pos.getZ()) <= this.radius
                && target.getY() >= pos.getY() - 4
                && target.getY() <= pos.getY() + 15;
    }

    /** Inserts into the beacon storage. Returns the leftover stack. */
    public ItemStack insert(ItemStack stack) {
        ItemStack remaining = stack.copy();
        for (int i = 0; i < CONTAINER_SIZE && !remaining.isEmpty(); i++) {
            ItemStack slot = this.items.get(i);
            if (!slot.isEmpty() && ItemStack.isSameItemSameComponents(slot, remaining)) {
                int canAdd = Math.min(remaining.getCount(), Math.min(slot.getMaxStackSize(), this.getMaxStackSize()) - slot.getCount());
                if (canAdd > 0) {
                    slot.grow(canAdd);
                    remaining.shrink(canAdd);
                }
            }
        }
        for (int i = 0; i < CONTAINER_SIZE && !remaining.isEmpty(); i++) {
            if (this.items.get(i).isEmpty()) {
                int put = Math.min(remaining.getCount(), Math.min(remaining.getMaxStackSize(), this.getMaxStackSize()));
                this.items.set(i, remaining.split(put));
            }
        }
        if (remaining.getCount() != stack.getCount()) {
            this.setChanged();
        }
        return remaining;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, this.items);
        output.putInt("Task", this.task.id());
        output.putInt("Radius", this.radius);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.items = NonNullList.withSize(CONTAINER_SIZE, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, this.items);
        this.task = WorkTask.byId(input.getIntOr("Task", 0));
        this.radius = Mth.clamp(input.getIntOr("Radius", DEFAULT_RADIUS), MIN_RADIUS, MAX_RADIUS);
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
        return new StellarBeaconMenu(containerId, inventory, this, this.dataAccess, this.getBlockPos());
    }
}
