package com.caram.faelune.block;

import com.caram.faelune.block.entity.StellarHopperBlockEntity;
import com.caram.faelune.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * A hopper with three filter slots: when any filter slot holds an item, it only picks up matching items.
 * Shape, placement, redstone locking and comparator output are inherited from the vanilla hopper.
 */
public class StellarHopperBlock extends HopperBlock {
    public static final MapCodec<HopperBlock> CODEC = simpleCodec(StellarHopperBlock::new);

    public StellarHopperBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<HopperBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new StellarHopperBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide()
                ? null
                : createTickerHelper(type, ModBlockEntities.STELLAR_HOPPER.get(), StellarHopperBlockEntity::pushItemsTick);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof StellarHopperBlockEntity hopper) {
            player.openMenu(hopper);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void entityInside(
            BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier, boolean isPrecise
    ) {
        if (level.getBlockEntity(pos) instanceof StellarHopperBlockEntity hopper) {
            StellarHopperBlockEntity.entityInside(level, pos, state, entity, hopper);
        }
    }
}
