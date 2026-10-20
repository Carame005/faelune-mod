package com.caram.faelune.entity.ai;

import com.caram.faelune.block.entity.StellarBeaconBlockEntity;
import com.caram.faelune.entity.FaeluneEntity;
import com.caram.faelune.work.WorkTask;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Harvests mature crops, replants them, and sows empty farmland with seeds she carries.
 */
public class FaeluneFarmerGoal extends FaeluneBlockWorkGoal {
    private static final int HARVEST_TICKS = 8;

    private enum Mode { HARVEST, SOW }

    private Mode mode = Mode.HARVEST;

    public FaeluneFarmerGoal(FaeluneEntity faelune, double speedModifier) {
        super(faelune, speedModifier);
    }

    @Override
    protected WorkTask task() {
        return WorkTask.FARMER;
    }

    @Override
    protected @Nullable BlockPos findTarget(ServerLevel level, StellarBeaconBlockEntity station) {
        boolean hasSeeds = this.faelune.findStorageSlot(WorkTask::isSeed) >= 0;
        BlockPos bestHarvest = null;
        BlockPos bestSow = null;
        double harvestDist = Double.MAX_VALUE;
        double sowDist = Double.MAX_VALUE;
        boolean needsSeeds = false;

        AABB area = station.getWorkArea();
        for (BlockPos pos : BlockPos.betweenClosed(
                Mth.floor(area.minX), Mth.floor(area.minY), Mth.floor(area.minZ),
                Mth.floor(area.maxX) - 1, Mth.floor(area.maxY) - 1, Mth.floor(area.maxZ) - 1)) {
            if (this.isIgnored(pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state)) {
                double dist = this.distanceSqrToCenter(pos);
                if (dist < harvestDist) {
                    harvestDist = dist;
                    bestHarvest = pos.immutable();
                }
            } else if (state.isAir() && level.getBlockState(pos.below()).is(Blocks.FARMLAND)) {
                if (!hasSeeds) {
                    needsSeeds = true;
                    continue;
                }
                double dist = this.distanceSqrToCenter(pos);
                if (dist < sowDist) {
                    sowDist = dist;
                    bestSow = pos.immutable();
                }
            }
        }

        if (needsSeeds) {
            this.faelune.requestSupply(Items.AIR);
        }
        if (bestHarvest != null) {
            this.mode = Mode.HARVEST;
            return bestHarvest;
        }
        this.mode = Mode.SOW;
        return bestSow;
    }

    @Override
    protected boolean isValidTarget(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (this.mode == Mode.HARVEST) {
            return state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state);
        }
        return state.isAir()
                && level.getBlockState(pos.below()).is(Blocks.FARMLAND)
                && this.faelune.findStorageSlot(WorkTask::isSeed) >= 0;
    }

    @Override
    protected boolean isInReachFrom(Vec3 from, BlockPos pos) {
        return distanceSqrToCenter(from, pos) <= 2.3 * 2.3;
    }

    @Override
    protected boolean isBreakingWork() {
        return this.mode == Mode.HARVEST;
    }

    @Override
    protected boolean workOn(ServerLevel level, BlockPos pos) {
        return this.mode == Mode.HARVEST ? this.harvest(level, pos) : this.sow(level, pos);
    }

    private boolean harvest(ServerLevel level, BlockPos pos) {
        if (this.workTicks++ == 0) {
            this.faelune.swing(InteractionHand.MAIN_HAND);
        }
        if (this.workTicks < HARVEST_TICKS) {
            return false;
        }

        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof CropBlock crop)) {
            return true;
        }

        List<ItemStack> drops = Block.getDrops(state, level, pos, null, this.faelune, this.faelune.getToolStack());
        Item seed = crop.asItem();
        boolean replanted = false;
        for (ItemStack drop : drops) {
            if (drop.is(seed)) {
                drop.shrink(1);
                replanted = true;
                break;
            }
        }

        level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, pos, Block.getId(state));
        level.setBlock(pos, replanted ? crop.getStateForAge(0) : Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);

        for (ItemStack drop : drops) {
            if (!drop.isEmpty()) {
                ItemStack leftover = this.faelune.insertIntoStorage(drop);
                if (!leftover.isEmpty()) {
                    Block.popResource(level, pos, leftover);
                }
            }
        }
        this.faelune.damageTool(1);
        return true;
    }

    private boolean sow(ServerLevel level, BlockPos pos) {
        int slot = this.faelune.findStorageSlot(WorkTask::isSeed);
        if (slot < 0) {
            return true;
        }
        ItemStack seeds = this.faelune.getInventory().getItem(slot);
        BlockState cropState = Block.byItem(seeds.getItem()).defaultBlockState();
        if (level.getBlockState(pos).isAir() && cropState.canSurvive(level, pos)) {
            level.setBlock(pos, cropState, Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 1.0f, 1.0f);
            this.faelune.swing(InteractionHand.MAIN_HAND);
            seeds.shrink(1);
            this.faelune.getInventory().setChanged();
        }
        return true;
    }
}
