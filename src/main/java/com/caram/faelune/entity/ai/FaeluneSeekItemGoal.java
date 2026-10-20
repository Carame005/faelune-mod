package com.caram.faelune.entity.ai;

import com.caram.faelune.block.entity.StellarBeaconBlockEntity;
import com.caram.faelune.entity.FaeluneEntity;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * Paths toward nearby ground items when pickup is enabled and storage has space.
 * While working at a station, it collects anything inside the station radius instead.
 * Items she can't actually reach (stuck on leaves, inside blocks...) are skipped for a while.
 */
public class FaeluneSeekItemGoal extends Goal {
    private static final double SEARCH_RANGE = 12.0;
    private static final double SEARCH_RANGE_Y = 4.0;
    private static final int GIVE_UP_TICKS = 200;
    private static final int IGNORE_TICKS = 600;

    private final FaeluneEntity faelune;
    private final double speedModifier;
    private final StuckDetector stuck = new StuckDetector();
    /** Entity id of unreachable items -> tick when they may be tried again. */
    private final Map<Integer, Integer> ignoredItems = new HashMap<>();
    private @Nullable ItemEntity targetItem;
    private int recalcPathDelay;
    private int startTick;

    public FaeluneSeekItemGoal(FaeluneEntity faelune, double speedModifier) {
        this.faelune = faelune;
        this.speedModifier = speedModifier;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!this.faelune.canPickUpLoot()
                || this.faelune.isOrderedToSit()
                || !this.faelune.isTame()
                || this.faelune.getTarget() != null) {
            return false;
        }

        this.targetItem = this.findNearestItem();
        return this.targetItem != null;
    }

    @Override
    public boolean canContinueToUse() {
        if (this.targetItem == null
                || !this.targetItem.isAlive()
                || this.targetItem.getItem().isEmpty()
                || this.targetItem.hasPickUpDelay()
                || !this.faelune.canPickUpLoot()
                || this.faelune.isOrderedToSit()
                || this.faelune.getTarget() != null
                || !this.faelune.hasStorageSpaceFor(this.targetItem.getItem())) {
            return false;
        }
        AABB area = this.searchArea();
        return area != null && area.inflate(2.0).contains(this.targetItem.position());
    }

    @Override
    public void start() {
        this.recalcPathDelay = 0;
        this.startTick = this.faelune.tickCount;
        this.stuck.reset(this.faelune);
    }

    @Override
    public void stop() {
        this.targetItem = null;
        this.faelune.getNavigation().stop();
    }

    @Override
    public void tick() {
        ItemEntity item = this.targetItem;
        if (item == null) {
            return;
        }

        if (this.faelune.isWorking()) {
            this.faelune.markWorkBusy();
        }
        this.faelune.getLookControl().setLookAt(item, 30.0f, 30.0f);

        if (this.faelune.tickCount - this.startTick > GIVE_UP_TICKS || this.stuck.isStuck(this.faelune)) {
            this.skip(item);
            return;
        }

        if (--this.recalcPathDelay <= 0) {
            this.recalcPathDelay = this.adjustedTickDelay(10);
            Path path = this.faelune.getNavigation().createPath(item, 1);
            // Mobs pick up items that overlap their (slightly inflated) hitbox, so the path has to end next to it.
            if (!StuckDetector.pathEndsNear(path, item.position(), 1.5, -0.5, 1.5)) {
                this.skip(item);
                return;
            }
            this.faelune.getNavigation().moveTo(path, this.speedModifier);
        }
    }

    private void skip(ItemEntity item) {
        this.ignoredItems.put(item.getId(), this.faelune.tickCount + IGNORE_TICKS);
        this.targetItem = null;
    }

    private boolean isIgnored(ItemEntity item) {
        Integer until = this.ignoredItems.get(item.getId());
        return until != null && this.faelune.tickCount < until;
    }

    private @Nullable AABB searchArea() {
        if (this.faelune.isWorking()) {
            StellarBeaconBlockEntity station = this.faelune.getStation();
            return station == null ? null : station.getWorkArea().inflate(1.0);
        }
        return this.faelune.getBoundingBox().inflate(SEARCH_RANGE, SEARCH_RANGE_Y, SEARCH_RANGE);
    }

    private @Nullable ItemEntity findNearestItem() {
        if (!(this.faelune.level() instanceof ServerLevel serverLevel)) {
            return null;
        }
        AABB searchBox = this.searchArea();
        if (searchBox == null) {
            return null;
        }

        int now = this.faelune.tickCount;
        this.ignoredItems.values().removeIf(until -> until <= now);

        List<ItemEntity> items = serverLevel.getEntitiesOfClass(
                ItemEntity.class,
                searchBox,
                item -> !item.getItem().isEmpty()
                        && !item.hasPickUpDelay()
                        && !this.isIgnored(item)
                        && this.faelune.wantsToPickUp(serverLevel, item.getItem())
        );

        ItemEntity nearest = null;
        double nearestDist = Double.MAX_VALUE;
        for (ItemEntity item : items) {
            double dist = this.faelune.distanceToSqr(item);
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = item;
            }
        }
        return nearest;
    }
}
