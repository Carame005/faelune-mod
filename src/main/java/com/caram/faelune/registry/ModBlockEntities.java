package com.caram.faelune.registry;

import com.caram.faelune.Faelune;
import com.caram.faelune.block.entity.StellarBeaconBlockEntity;
import com.caram.faelune.block.entity.StellarHopperBlockEntity;
import java.util.Set;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, Faelune.MODID);

    public static final RegistryObject<BlockEntityType<StellarBeaconBlockEntity>> STELLAR_BEACON =
            BLOCK_ENTITY_TYPES.register(
                    "stellar_beacon",
                    () -> new BlockEntityType<>(StellarBeaconBlockEntity::new, Set.of(ModBlocks.STELLAR_BEACON.get()))
            );

    public static final RegistryObject<BlockEntityType<StellarHopperBlockEntity>> STELLAR_HOPPER =
            BLOCK_ENTITY_TYPES.register(
                    "stellar_hopper",
                    () -> new BlockEntityType<>(StellarHopperBlockEntity::new, Set.of(ModBlocks.STELLAR_HOPPER.get()))
            );

    private ModBlockEntities() {
    }
}
