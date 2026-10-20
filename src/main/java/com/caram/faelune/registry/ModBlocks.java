package com.caram.faelune.registry;

import com.caram.faelune.Faelune;
import com.caram.faelune.block.StellarBeaconBlock;
import com.caram.faelune.block.StellarHopperBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, Faelune.MODID);

    public static final RegistryObject<Block> STELLAR_BEACON = BLOCKS.register(
            "stellar_beacon",
            () -> new StellarBeaconBlock(BlockBehaviour.Properties.of()
                    .setId(BLOCKS.key("stellar_beacon"))
                    .mapColor(MapColor.COLOR_PURPLE)
                    .strength(3.5f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.AMETHYST)
                    .lightLevel(state -> 12))
    );

    public static final RegistryObject<Block> STELLAR_HOPPER = BLOCKS.register(
            "stellar_hopper",
            () -> new StellarHopperBlock(BlockBehaviour.Properties.of()
                    .setId(BLOCKS.key("stellar_hopper"))
                    .mapColor(MapColor.COLOR_PURPLE)
                    .strength(3.0f, 4.8f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .noOcclusion())
    );

    private ModBlocks() {
    }
}
