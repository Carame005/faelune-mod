package com.caram.faelune.entity.ai;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Detects when a mob that is supposed to be walking somewhere has stopped making progress.
 * Uses the entity tick counter, so it works the same whether the goal ticks every tick or every other tick.
 */
final class StuckDetector {
    private static final int SAMPLE_INTERVAL = 20;
    private static final int MAX_STUCK_SAMPLES = 2;
    private static final double MIN_PROGRESS_SQR = 0.3 * 0.3;

    private Vec3 lastPos = Vec3.ZERO;
    private int lastSampleTick;
    private int stuckSamples;

    void reset(Entity entity) {
        this.lastPos = entity.position();
        this.lastSampleTick = entity.tickCount;
        this.stuckSamples = 0;
    }

    /** Returns true once the entity has barely moved for about two seconds. */
    boolean isStuck(Entity entity) {
        if (entity.tickCount - this.lastSampleTick < SAMPLE_INTERVAL) {
            return false;
        }
        this.lastSampleTick = entity.tickCount;
        Vec3 now = entity.position();
        if (now.distanceToSqr(this.lastPos) < MIN_PROGRESS_SQR) {
            this.stuckSamples++;
        } else {
            this.stuckSamples = 0;
        }
        this.lastPos = now;
        return this.stuckSamples >= MAX_STUCK_SAMPLES;
    }

    /** Whether a (possibly partial) path ends close enough to {@code target}. */
    static boolean pathEndsNear(@Nullable Path path, Vec3 target, double maxHorizontal, double minDy, double maxDy) {
        if (path == null) {
            return false;
        }
        Node end = path.getEndNode();
        if (end == null) {
            return false;
        }
        double dx = end.x + 0.5 - target.x;
        double dz = end.z + 0.5 - target.z;
        double dy = target.y - end.y;
        return dx * dx + dz * dz <= maxHorizontal * maxHorizontal && dy >= minDy && dy <= maxDy;
    }
}
