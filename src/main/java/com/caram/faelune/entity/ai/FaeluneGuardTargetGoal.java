package com.caram.faelune.entity.ai;

import com.caram.faelune.block.entity.StellarBeaconBlockEntity;
import com.caram.faelune.entity.FaeluneEntity;
import com.caram.faelune.work.WorkTask;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.pathfinder.Path;
import org.jspecify.annotations.Nullable;

/**
 * While on guard duty, targets hostile mobs inside the station radius. Creepers are left alone,
 * and mobs she can't walk to (caves below, behind walls...) are skipped for a while.
 */
public class FaeluneGuardTargetGoal extends Goal {
    private static final int MAX_PATH_CHECKS = 3;
    private static final int IGNORE_TICKS = 300;
    private static final double MELEE_RANGE_SQR = 3.0 * 3.0;

    private final FaeluneEntity faelune;
    private final StuckDetector stuck = new StuckDetector();
    /** Entity id of unreachable mobs -> tick when they may be targeted again. */
    private final Map<Integer, Integer> ignored = new HashMap<>();
    private @Nullable LivingEntity guardTarget;
    private int scanCooldown;

    public FaeluneGuardTargetGoal(FaeluneEntity faelune) {
        this.faelune = faelune;
        this.setFlags(EnumSet.of(Goal.Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        if (!this.faelune.isWorkingOn(WorkTask.GUARD) || this.faelune.getTarget() != null) {
            return false;
        }
        if (this.scanCooldown > 0) {
            this.scanCooldown--;
            return false;
        }
        this.scanCooldown = reducedTickDelay(10);

        StellarBeaconBlockEntity station = this.faelune.getStation();
        if (station == null) {
            return false;
        }

        int now = this.faelune.tickCount;
        this.ignored.values().removeIf(until -> until <= now);

        List<LivingEntity> hostiles = this.faelune.level().getEntitiesOfClass(
                LivingEntity.class,
                station.getWorkArea().inflate(2.0),
                entity -> entity instanceof Enemy
                        && !(entity instanceof Creeper)
                        && entity.isAlive()
                        && !this.isIgnored(entity)
                        && this.faelune.canAttack(entity)
        );
        hostiles.sort(Comparator.comparingDouble(this.faelune::distanceToSqr));

        int checks = 0;
        for (LivingEntity hostile : hostiles) {
            if (checks++ >= MAX_PATH_CHECKS) {
                break;
            }
            if (this.canReach(hostile)) {
                this.guardTarget = hostile;
                return true;
            }
            this.ignore(hostile);
        }
        return false;
    }

    @Override
    public boolean canContinueToUse() {
        StellarBeaconBlockEntity station = this.faelune.getStation();
        return this.guardTarget != null
                && this.guardTarget.isAlive()
                && !this.isIgnored(this.guardTarget)
                && station != null
                && this.faelune.isWorkingOn(WorkTask.GUARD)
                && station.getWorkArea().inflate(6.0).contains(this.guardTarget.position());
    }

    @Override
    public void start() {
        this.faelune.setTarget(this.guardTarget);
        this.stuck.reset(this.faelune);
    }

    @Override
    public void tick() {
        LivingEntity target = this.guardTarget;
        if (target == null) {
            return;
        }
        if (this.faelune.distanceToSqr(target) <= MELEE_RANGE_SQR) {
            this.stuck.reset(this.faelune);
        } else if (this.stuck.isStuck(this.faelune)) {
            this.ignore(target);
        }
    }

    @Override
    public void stop() {
        if (this.faelune.getTarget() == this.guardTarget) {
            this.faelune.setTarget(null);
        }
        this.guardTarget = null;
    }

    private boolean canReach(LivingEntity target) {
        if (this.faelune.distanceToSqr(target) <= MELEE_RANGE_SQR) {
            return true;
        }
        Path path = this.faelune.getNavigation().createPath(target, 0);
        return StuckDetector.pathEndsNear(path, target.position(), 2.0, -1.5, 2.5);
    }

    private void ignore(LivingEntity entity) {
        this.ignored.put(entity.getId(), this.faelune.tickCount + IGNORE_TICKS);
    }

    private boolean isIgnored(LivingEntity entity) {
        Integer until = this.ignored.get(entity.getId());
        return until != null && this.faelune.tickCount < until;
    }
}
