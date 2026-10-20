package com.caram.faelune.entity.ai;

import com.caram.faelune.block.entity.StellarBeaconBlockEntity;
import com.caram.faelune.entity.FaeluneEntity;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * Walks back toward the beacon when she drifts out of the work radius.
 */
public class FaeluneStayNearStationGoal extends Goal {
    private final FaeluneEntity faelune;
    private final double speedModifier;
    private int recalcPathDelay;

    public FaeluneStayNearStationGoal(FaeluneEntity faelune, double speedModifier) {
        this.faelune = faelune;
        this.speedModifier = speedModifier;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        StellarBeaconBlockEntity station = this.faelune.getStation();
        return station != null && this.faelune.isWorking() && this.distanceSqrTo(station) > sqr(station.getRadius() + 1);
    }

    @Override
    public boolean canContinueToUse() {
        StellarBeaconBlockEntity station = this.faelune.getStation();
        return station != null
                && this.faelune.isWorking()
                && !this.faelune.getNavigation().isDone()
                && this.distanceSqrTo(station) > sqr(Math.max(2, station.getRadius() / 2));
    }

    @Override
    public void start() {
        this.recalcPathDelay = 0;
    }

    @Override
    public void stop() {
        this.faelune.getNavigation().stop();
    }

    @Override
    public void tick() {
        StellarBeaconBlockEntity station = this.faelune.getStation();
        if (station != null && --this.recalcPathDelay <= 0) {
            this.recalcPathDelay = this.adjustedTickDelay(20);
            BlockPos pos = station.getBlockPos();
            this.faelune.getNavigation().moveTo(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, this.speedModifier);
        }
    }

    private double distanceSqrTo(StellarBeaconBlockEntity station) {
        BlockPos pos = station.getBlockPos();
        return this.faelune.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    }

    private static double sqr(double value) {
        return value * value;
    }
}
