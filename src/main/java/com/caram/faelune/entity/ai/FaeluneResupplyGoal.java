package com.caram.faelune.entity.ai;

import com.caram.faelune.block.entity.StellarBeaconBlockEntity;
import com.caram.faelune.entity.FaeluneEntity;
import com.caram.faelune.work.WorkTask;
import java.util.EnumSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

/**
 * Walks to the beacon to pick up what she needs: a better tool for the current task or better armor
 * (what she was using is left in its place), supplies her work goals asked for (saplings, seeds) and
 * stellar cookies.
 */
public class FaeluneResupplyGoal extends Goal {
    private static final double REACH = 2.8;
    private static final int GIVE_UP_TICKS = 400;
    private static final int UNREACHABLE_COOLDOWN = 400;

    private final FaeluneEntity faelune;
    private final double speedModifier;
    private final StuckDetector stuck = new StuckDetector();
    private int scanCooldown;
    private int recalcPathDelay;
    private int ticksRunning;
    private boolean done;

    public FaeluneResupplyGoal(FaeluneEntity faelune, double speedModifier) {
        this.faelune = faelune;
        this.speedModifier = speedModifier;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (this.scanCooldown > 0) {
            this.scanCooldown--;
            return false;
        }
        this.scanCooldown = reducedTickDelay(20);

        if (!this.faelune.canManageWorkGear() || this.faelune.getTarget() != null) {
            return false;
        }
        StellarBeaconBlockEntity station = this.faelune.getStation();
        if (station == null) {
            return false;
        }
        // Better gear in her own bag is equipped on the spot, no need to walk anywhere.
        this.faelune.equipBestGearFromStorage();
        if (this.hasGearUpgrade(station)) {
            return true;
        }
        if (this.faelune.needsStellarCookies() && findCookieSlot(station) >= 0) {
            return true;
        }
        return this.faelune.isWorking() && this.findWantedSupply(station) >= 0;
    }

    @Override
    public boolean canContinueToUse() {
        return !this.done
                && this.ticksRunning < GIVE_UP_TICKS
                && this.faelune.canManageWorkGear()
                && this.faelune.getTarget() == null
                && this.faelune.getStation() != null;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        this.done = false;
        this.ticksRunning = 0;
        this.recalcPathDelay = 0;
        this.stuck.reset(this.faelune);
    }

    @Override
    public void stop() {
        this.faelune.getNavigation().stop();
        if (!this.done) {
            this.scanCooldown = reducedTickDelay(UNREACHABLE_COOLDOWN);
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

        if (this.faelune.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= REACH * REACH) {
            this.faelune.getNavigation().stop();
            this.takeFrom(station);
            this.done = true;
            return;
        }

        if (this.stuck.isStuck(this.faelune)) {
            this.ticksRunning = GIVE_UP_TICKS;
            return;
        }

        if (--this.recalcPathDelay <= 0) {
            this.recalcPathDelay = this.adjustedTickDelay(10);
            Path path = this.faelune.getNavigation().createPath(pos, 1);
            if (!StuckDetector.pathEndsNear(path, Vec3.atBottomCenterOf(pos), 2.5, -2.0, 2.0)) {
                this.ticksRunning = GIVE_UP_TICKS;
                return;
            }
            this.faelune.getNavigation().moveTo(path, this.speedModifier);
        }
    }

    private void takeFrom(StellarBeaconBlockEntity station) {
        boolean tookAny = false;

        // What she was using stays in the beacon slot the better piece came from.
        int toolSlot = this.faelune.findToolUpgrade(station, 0);
        if (toolSlot >= 0) {
            this.faelune.swapToolWith(station, toolSlot);
            tookAny = true;
        }
        for (int i = 0; i < FaeluneEntity.ARMOR_SLOTS.length; i++) {
            int armorSlot = this.faelune.findArmorUpgrade(station, 0, i);
            if (armorSlot >= 0) {
                this.faelune.swapArmorWith(station, armorSlot, i);
                tookAny = true;
            }
        }

        // One stack of each requested supply; more would only be carried back on the next deposit.
        if (this.faelune.isWorking()) {
            int slot;
            while ((slot = this.findWantedSupply(station)) >= 0) {
                ItemStack stored = station.getItem(slot);
                Item item = stored.getItem();
                ItemStack leftover = this.faelune.insertIntoStorage(stored.copy());
                if (leftover.getCount() == stored.getCount()) {
                    break;
                }
                station.setItem(slot, leftover);
                tookAny = true;
                Set<Item> requests = this.faelune.getSupplyRequests();
                if (!requests.remove(item)) {
                    requests.remove(Items.AIR);
                }
            }
            this.faelune.clearSupplyRequests();
        }

        int slot;
        while (this.faelune.missingStellarCookies() > 0 && (slot = findCookieSlot(station)) >= 0) {
            ItemStack stored = station.getItem(slot);
            int amount = Math.min(stored.getCount(), this.faelune.missingStellarCookies());
            ItemStack leftover = this.faelune.insertIntoStorage(stored.copyWithCount(amount));
            int taken = amount - leftover.getCount();
            if (taken <= 0) {
                break;
            }
            stored.shrink(taken);
            station.setChanged();
            tookAny = true;
        }

        if (tookAny && this.faelune.level() instanceof ServerLevel level) {
            BlockPos pos = station.getBlockPos();
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 1.0f, 1.3f);
            level.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 8, 0.25, 0.25, 0.25, 0.04);
            this.faelune.swing(InteractionHand.MAIN_HAND);
        }
    }

    private static int findCookieSlot(StellarBeaconBlockEntity station) {
        for (int i = 0; i < station.getContainerSize(); i++) {
            if (FaeluneEntity.isStellarCookie(station.getItem(i))) {
                return i;
            }
        }
        return -1;
    }

    /** A better tool for the task or a better armor piece is waiting in the beacon. */
    private boolean hasGearUpgrade(StellarBeaconBlockEntity station) {
        if (this.faelune.findToolUpgrade(station, 0) >= 0) {
            return true;
        }
        for (int i = 0; i < FaeluneEntity.ARMOR_SLOTS.length; i++) {
            if (this.faelune.findArmorUpgrade(station, 0, i) >= 0) {
                return true;
            }
        }
        return false;
    }

    /** Beacon slot with a supply one of her work goals asked for and she doesn't carry, or -1. */
    private int findWantedSupply(StellarBeaconBlockEntity station) {
        Set<Item> requests = this.faelune.getSupplyRequests();
        if (requests.isEmpty()) {
            return -1;
        }
        WorkTask task = this.faelune.getWorkTask();
        for (int i = 0; i < station.getContainerSize(); i++) {
            ItemStack stack = station.getItem(i);
            if (stack.isEmpty() || !task.isSupply(stack)) {
                continue;
            }
            boolean wanted = requests.contains(stack.getItem())
                    || requests.contains(Items.AIR) && this.faelune.findStorageSlot(task::isSupply) < 0;
            if (wanted && this.faelune.findStorageSlot(s -> s.is(stack.getItem())) < 0) {
                return i;
            }
        }
        return -1;
    }
}
