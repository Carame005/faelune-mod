package com.caram.faelune;

import com.caram.faelune.client.FaeluneClient;
import com.caram.faelune.registry.ModBlockEntities;
import com.caram.faelune.registry.ModBlocks;
import com.caram.faelune.registry.ModCreativeTabs;
import com.caram.faelune.registry.ModEntities;
import com.caram.faelune.registry.ModItems;
import com.caram.faelune.registry.ModMenus;
import com.mojang.logging.LogUtils;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

@Mod(Faelune.MODID)
public final class Faelune {
    public static final String MODID = "faelune";
    private static final Logger LOGGER = LogUtils.getLogger();

    public Faelune(FMLJavaModLoadingContext context) {
        var modBusGroup = context.getModBusGroup();

        ModEntities.ENTITY_TYPES.register(modBusGroup);
        ModBlocks.BLOCKS.register(modBusGroup);
        ModBlockEntities.BLOCK_ENTITY_TYPES.register(modBusGroup);
        ModItems.ITEMS.register(modBusGroup);
        ModMenus.MENUS.register(modBusGroup);
        ModCreativeTabs.CREATIVE_TABS.register(modBusGroup);

        EntityAttributeCreationEvent.BUS.addListener(ModEntities::registerAttributes);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            FaeluneClient.init(modBusGroup);
        }

        LOGGER.info("Faelune mod loaded");
    }
}
