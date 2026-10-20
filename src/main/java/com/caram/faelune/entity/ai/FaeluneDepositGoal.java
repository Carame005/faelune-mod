package com.caram.faelune.entity.ai;

import com.caram.faelune.block.entity.StellarBeaconBlockEntity;
import com.caram.faelune.entity.FaeluneEntity;
import com.caram.faelune.work.WorkTask;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

/**
 * Walks back to the beacon and unloads her storage when it is full or when there is nothing else to do.
 * Keeps one stack of each supply type (every sapling/seed kind) for replanting, and her stellar cookies.
 */
public class FaeluneDepositGoal extends Goal {
    private static final int IDLE_TICKS_BEFORE_DEPOSIT = 80;
    private static final int FULL_BEACON_COOLDOWN = 400;
    private static final int GIVE_UP_TICKS = 400;

    private final FaeluneEntity faelune;
    private final double speedModifier;
    private int recalcPathDelay;
    private int ticksRunning;
    private int cooldown;
    private final StuckDetector stuck = new StuckDetector();

    public FaeluneDepositGoal(FaeluneEntity faelune, double speedModifier) {
        this.faelune = faelune;
        this.speedModifier = speedModifier;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (this.cooldown > 0) {
            this.cooldown--;
            return false;
        }
        if (!this.faelune.isWorking() || this.faelune.getTarget() != null || this.faelune.getStation() == null) {
            return false;
        }
        if (!this.hasDepositableItems()) {
            return false;
        }
        return this.faelune.isStorageFull() || this.faelune.getWorkIdleTicks() > IDLE_TICKS_BEFORE_DEPOSIT;
    }

    @Override
    public boolean canContinueToUse() {
        return this.ticksRunning < GIVE_UP_TICKS
                && this.faelune.isWorking()
                && this.faelune.getTarget() == null
                && this.faelune.getStation() != null
                && this.hasDepositableItems();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        this.recalcPathDelay = 0;
        this.ticksRunning = 0;
        this.stuck.reset(this.faelune);
    }

    @Override
    public void stop() {
        this.faelune.getNavigation().stop();
        this.faelune.resetWorkIdle();
        if (this.ticksRunning >= GIVE_UP_TICKS) {
            this.cooldown = reducedTickDelay(FULL_BEACON_COOLDOWN);
        }
    }

    @Override
    public void tick() {
        StellarBeaconBlockEntity station = this.faelune.getStation();
        if (station == null) {
            return;
        }
        this.ticksRunning++;
        this.faelune.markWorkBusy();

        BlockPos pos = station.getBlockPos();
        this.faelune.getLookControl().setLookAt(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);

        if (this.faelune.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 2.8 * 2.8) {
            this.faelune.getNavigation().stop();
            if (!this.depositInto(station)) {
                // Beacon is full: don't keep walking back every few seconds.
                this.cooldown = reducedTickDelay(FULL_BEACON_COOLDOWN);
                this.ticksRunning = GIVE_UP_TICKS;
            }
            return;
        }

        if (this.stuck.isStuck(this.faelune)) {
            this.giveUp();
            return;
        }

        if (--this.recalcPathDelay <= 0) {
            this.recalcPathDelay = this.adjustedTickDelay(10);
            Path path = this.faelune.getNavigation().createPath(pos, 1);
            if (!StuckDetector.pathEndsNear(path, Vec3.atBottomCenterOf(pos), 2.5, -2.0, 2.0)) {
                this.giveUp();
                return;
            }
            this.faelune.getNavigation().moveTo(path, this.speedModifier);
        }
    }

    /** The beacon can't be reached right now: rest a while instead of standing there. */
    private void giveUp() {
        this.cooldown = reducedTickDelay(FULL_BEACON_COOLDOWN);
        this.ticksRunning = GIVE_UP_TICKS;
    }

    /** Returns false if nothing could be moved (beacon full). */
    private boolean depositInto(StellarBeaconBlockEntity station) {
        Container inventory = this.faelune.getInventory();
        WorkTask task = this.faelune.getWorkTask();
        Set<Item> keptSupply = new HashSet<>();
        boolean movedAny = false;

        for (int i = 1; i < FaeluneEntity.INVENTORY_SIZE; i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (this.keeps(task, keptSupply, stack)) {
                continue;
            }
            ItemStack leftover = station.insert(stack);
            if (leftover.getCount() != stack.getCount()) {
                movedAny = true;
                inventory.setItem(i, leftover);
            }
        }

        if (movedAny && this.faelune.level() instanceof ServerLevel level) {
            BlockPos pos = station.getBlockPos();
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 1.0f, 1.0f);
            level.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 12, 0.25, 0.25, 0.25, 0.04);
            this.faelune.swing(InteractionHand.MAIN_HAND);
        }
        return movedAny;
    }

    private boolean hasDepositableItems() {
        Container inventory = this.faelune.getInventory();
        WorkTask task = this.faelune.getWorkTask();
        Set<Item> keptSupply = new HashSet<>();
        for (int i = 1; i < FaeluneEntity.INVENTORY_SIZE; i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (this.keeps(task, keptSupply, stack)) {
                continue;
            }
            return true;
        }
        return false;
    }

    /** One stack of each supply, plus her stellar cookies while auto-heal is on. */
    private boolean keeps(WorkTask task, Set<Item> keptSupply, ItemStack stack) {
        if (this.faelune.isAutoHealEnabled() && FaeluneEntity.isStellarCookie(stack)) {
            return true;
        }
        return task.isSupply(stack) && keptSupply.add(stack.getItem());
    }
}
