package com.caram.faelune.entity.ai;

import com.caram.faelune.block.entity.StellarBeaconBlockEntity;
import com.caram.faelune.entity.FaeluneEntity;
import com.caram.faelune.work.WorkTask;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Shared logic for tasks that walk to a block inside the station area and work on it.
 */
public abstract class FaeluneBlockWorkGoal extends Goal {
    private static final int GIVE_UP_TICKS = 200;
    private static final int PROGRESS_CHECK_TICKS = 20;
    private static final int MAX_STUCK_SAMPLES = 2;
    private static final double MIN_PROGRESS_SQR = 0.3 * 0.3;
    private static final int IGNORE_RESET_TICKS = 600;

    protected final FaeluneEntity faelune;
    private final double speedModifier;
    protected @Nullable BlockPos target;
    protected int workTicks;

    private int scanCooldown;
    private int recalcPathDelay;
    private int ticksWithoutReaching;
    private int progressCheckTimer;
    private int stuckSamples;
    private Vec3 lastSamplePos = Vec3.ZERO;
    private final Set<BlockPos> ignored = new HashSet<>();
    private int lastIgnoreReset;

    protected FaeluneBlockWorkGoal(FaeluneEntity faelune, double speedModifier) {
        this.faelune = faelune;
        this.speedModifier = speedModifier;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    protected abstract WorkTask task();

    protected abstract @Nullable BlockPos findTarget(ServerLevel level, StellarBeaconBlockEntity station);

    protected abstract boolean isValidTarget(ServerLevel level, BlockPos pos);

    /** Whether the block can be worked on by Faelune standing at {@code from} (feet position). */
    protected abstract boolean isInReachFrom(Vec3 from, BlockPos pos);

    /** Called every tick while in reach. Returns true when the work on this block is done. */
    protected abstract boolean workOn(ServerLevel level, BlockPos pos);

    /** Picks the next block right after finishing one, so work flows without pauses. */
    protected @Nullable BlockPos findNextTarget(ServerLevel level, StellarBeaconBlockEntity station, BlockPos finished) {
        return this.findTarget(level, station);
    }

    protected void onTargetAbandoned(ServerLevel level, BlockPos pos) {
    }

    /**
     * A block standing between Faelune (at {@code from}) and an unreachable target that she can clear
     * first. Returning a block makes it the new target; null means the target is really unreachable.
     */
    protected @Nullable BlockPos findObstruction(ServerLevel level, BlockPos pos, Vec3 from) {
        return null;
    }

    /** Whether the current target plays the block-breaking animation (planting/sowing doesn't). */
    protected abstract boolean isBreakingWork();

    protected boolean isIgnored(BlockPos pos) {
        return this.ignored.contains(pos);
    }

    @Override
    public boolean canUse() {
        if (!this.faelune.isWorkingOn(this.task())) {
            return false;
        }
        if (this.scanCooldown > 0) {
            this.scanCooldown--;
            return false;
        }
        this.scanCooldown = reducedTickDelay(20);

        StellarBeaconBlockEntity station = this.faelune.getStation();
        if (station == null || !(this.faelune.level() instanceof ServerLevel level)) {
            return false;
        }
        if (this.faelune.tickCount - this.lastIgnoreReset > IGNORE_RESET_TICKS) {
            this.ignored.clear();
            this.lastIgnoreReset = this.faelune.tickCount;
        }

        this.target = this.findTarget(level, station);
        return this.target != null;
    }

    @Override
    public boolean canContinueToUse() {
        return this.target != null
                && this.faelune.isWorkingOn(this.task())
                && this.faelune.level() instanceof ServerLevel level
                && this.isValidTarget(level, this.target);
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    /** Walking can be interrupted (e.g. to fetch supplies), but a block being worked on is finished first. */
    @Override
    public boolean isInterruptable() {
        return this.workTicks == 0;
    }

    @Override
    public void start() {
        this.resetApproach();
        this.workTicks = 0;
    }

    @Override
    public void stop() {
        if (this.target != null && this.faelune.level() instanceof ServerLevel level) {
            this.onTargetAbandoned(level, this.target);
        }
        this.target = null;
        this.workTicks = 0;
        this.faelune.setBreakingBlock(false);
        this.faelune.getNavigation().stop();
    }

    @Override
    public void tick() {
        BlockPos pos = this.target;
        if (pos == null || !(this.faelune.level() instanceof ServerLevel level)) {
            return;
        }

        this.faelune.markWorkBusy();
        this.faelune.getLookControl().setLookAt(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);

        if (this.isInReach(pos)) {
            this.faelune.getNavigation().stop();
            this.ticksWithoutReaching = 0;
            this.faelune.setBreakingBlock(this.isBreakingWork());
            if (this.workOn(level, pos)) {
                this.workTicks = 0;
                StellarBeaconBlockEntity station = this.faelune.getStation();
                this.target = station == null ? null : this.findNextTarget(level, station, pos);
                this.resetApproach();
            }
            return;
        }

        this.faelune.setBreakingBlock(false);
        if (++this.ticksWithoutReaching > GIVE_UP_TICKS) {
            this.handleUnreachable(level, pos, this.faelune.position());
            return;
        }

        if (--this.recalcPathDelay <= 0) {
            this.recalcPathDelay = this.adjustedTickDelay(10);
            Path path = this.faelune.getNavigation().createPath(pos, 1);
            if (path == null || !this.canWorkFromPathEnd(path, pos)) {
                Node end = path == null ? null : path.getEndNode();
                Vec3 from = end == null ? this.faelune.position() : new Vec3(end.x + 0.5, end.y, end.z + 0.5);
                this.handleUnreachable(level, pos, from);
                return;
            }
            this.faelune.getNavigation().moveTo(path, this.speedModifier);
        }

        if (++this.progressCheckTimer >= PROGRESS_CHECK_TICKS) {
            this.progressCheckTimer = 0;
            Vec3 now = this.faelune.position();
            if (now.distanceToSqr(this.lastSamplePos) < MIN_PROGRESS_SQR) {
                if (++this.stuckSamples >= MAX_STUCK_SAMPLES) {
                    this.handleUnreachable(level, pos, now);
                    return;
                }
            } else {
                this.stuckSamples = 0;
            }
            this.lastSamplePos = now;
        }
    }

    /** A partial path is fine as long as the block can be worked on from where it ends. */
    private boolean canWorkFromPathEnd(Path path, BlockPos pos) {
        Node end = path.getEndNode();
        if (end == null) {
            return this.isInReach(pos);
        }
        return this.isInReachFrom(new Vec3(end.x + 0.5, end.y, end.z + 0.5), pos);
    }

    private void handleUnreachable(ServerLevel level, BlockPos pos, Vec3 from) {
        BlockPos obstruction = this.findObstruction(level, pos, from);
        if (obstruction == null) {
            this.giveUp(level, pos);
            return;
        }
        this.target = obstruction;
        this.workTicks = 0;
        this.faelune.getNavigation().stop();
        this.resetApproach();
    }

    /** Skips an unreachable block and immediately moves on to the next one. */
    private void giveUp(ServerLevel level, BlockPos pos) {
        this.onTargetAbandoned(level, pos);
        this.ignored.add(pos.immutable());
        this.workTicks = 0;
        this.faelune.getNavigation().stop();
        this.resetApproach();

        StellarBeaconBlockEntity station = this.faelune.getStation();
        this.target = station == null ? null : this.findTarget(level, station);
    }

    private void resetApproach() {
        this.recalcPathDelay = 0;
        this.ticksWithoutReaching = 0;
        this.progressCheckTimer = 0;
        this.stuckSamples = 0;
        this.lastSamplePos = this.faelune.position();
    }

    protected final boolean isInReach(BlockPos pos) {
        return this.isInReachFrom(this.faelune.position(), pos);
    }

    protected static double horizontalDistanceSqr(Vec3 from, BlockPos pos) {
        double dx = from.x - (pos.getX() + 0.5);
        double dz = from.z - (pos.getZ() + 0.5);
        return dx * dx + dz * dz;
    }

    protected static double distanceSqrToCenter(Vec3 from, BlockPos pos) {
        return from.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    }

    protected double distanceSqrToCenter(BlockPos pos) {
        return distanceSqrToCenter(this.faelune.position(), pos);
    }
}
