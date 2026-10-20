package com.caram.faelune.entity.ai;

import com.caram.faelune.entity.FaeluneEntity;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;

/**
 * Regular follow/teleport-to-owner, paused while Faelune is working at a station.
 */
public class FaeluneFollowOwnerGoal extends FollowOwnerGoal {
    private final FaeluneEntity faelune;

    public FaeluneFollowOwnerGoal(FaeluneEntity faelune, double speedModifier, float startDistance, float stopDistance) {
        super(faelune, speedModifier, startDistance, stopDistance);
        this.faelune = faelune;
    }

    @Override
    public boolean canUse() {
        return !this.faelune.isWorking() && super.canUse();
    }

    @Override
    public boolean canContinueToUse() {
        return !this.faelune.isWorking() && super.canContinueToUse();
    }
}
