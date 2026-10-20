package com.caram.faelune.block;

import com.caram.faelune.block.entity.StellarBeaconBlockEntity;
import com.caram.faelune.entity.FaeluneEntity;
import com.caram.faelune.item.StellarTunerItem;
import com.caram.faelune.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * Work station for Faelune. Link a Faelune with the Stellar Tuner, then pick a task in the GUI.
 */
public class StellarBeaconBlock extends BaseEntityBlock {
    public static final MapCodec<StellarBeaconBlock> CODEC = simpleCodec(StellarBeaconBlock::new);

    public StellarBeaconBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<StellarBeaconBlock> codec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new StellarBeaconBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide()
                ? null
                : createTickerHelper(type, ModBlockEntities.STELLAR_BEACON.get(), StellarBeaconBlockEntity::serverTick);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof StellarBeaconBlockEntity beacon) {
            serverPlayer.openMenu(beacon, buf -> buf.writeBlockPos(pos));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useItemOn(
            ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult
    ) {
        if (!(stack.getItem() instanceof StellarTunerItem)) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        UUID storedId = StellarTunerItem.getStoredFaelune(stack);
        if (storedId == null) {
            player.sendOverlayMessage(Component.translatable("message.faelune.tuner.empty"));
            return InteractionResult.SUCCESS_SERVER;
        }

        FaeluneEntity faelune = findOwnedFaelune(level, pos, player, storedId);
        if (faelune == null) {
            player.sendOverlayMessage(Component.translatable("message.faelune.tuner.not_found"));
            return InteractionResult.SUCCESS_SERVER;
        }

        faelune.setStation(pos);
        StellarTunerItem.clearStoredFaelune(stack);
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.0f, 1.2f);
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.END_ROD, faelune.getX(), faelune.getY() + 1.0, faelune.getZ(), 20, 0.3, 0.6, 0.3, 0.05);
            serverLevel.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 20, 0.2, 0.4, 0.2, 0.05);
        }
        player.sendOverlayMessage(Component.translatable("message.faelune.tuner.linked", faelune.getDisplayName()));
        return InteractionResult.SUCCESS_SERVER;
    }

    private static @Nullable FaeluneEntity findOwnedFaelune(Level level, BlockPos pos, Player player, UUID id) {
        return level.getEntitiesOfClass(
                        FaeluneEntity.class,
                        new AABB(pos).inflate(StellarBeaconBlockEntity.LINK_SCAN_RANGE),
                        faelune -> faelune.getUUID().equals(id) && faelune.isOwnedBy(player)
                ).stream()
                .findFirst()
                .orElse(null);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(3) == 0) {
            double x = pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.4;
            double y = pos.getY() + 1.05;
            double z = pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.4;
            level.addParticle(ParticleTypes.END_ROD, x, y, z, 0.0, 0.03 + random.nextDouble() * 0.03, 0.0);
        }
        if (random.nextInt(8) == 0) {
            level.addParticle(
                    ParticleTypes.REVERSE_PORTAL,
                    pos.getX() + random.nextDouble(), pos.getY() + random.nextDouble(), pos.getZ() + random.nextDouble(),
                    0.0, 0.0, 0.0
            );
        }
    }
}
