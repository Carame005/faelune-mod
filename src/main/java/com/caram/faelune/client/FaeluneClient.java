package com.caram.faelune.client;

import com.caram.faelune.client.renderer.FaeluneRenderer;
import com.caram.faelune.client.screen.FaeluneScreen;
import com.caram.faelune.client.screen.StellarBeaconScreen;
import com.caram.faelune.client.screen.StellarHopperScreen;
import com.caram.faelune.registry.ModEntities;
import com.caram.faelune.registry.ModMenus;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

public final class FaeluneClient {
    private FaeluneClient() {
    }

    public static void init(BusGroup modBusGroup) {
        EntityRenderersEvent.RegisterRenderers.BUS.addListener(FaeluneClient::registerRenderers);
        FMLClientSetupEvent.getBus(modBusGroup).addListener(FaeluneClient::onClientSetup);
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.FAELUNE.get(), FaeluneRenderer::new);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(ModMenus.FAELUNE_MENU.get(), FaeluneScreen::new);
            MenuScreens.register(ModMenus.STELLAR_BEACON_MENU.get(), StellarBeaconScreen::new);
            MenuScreens.register(ModMenus.STELLAR_HOPPER_MENU.get(), StellarHopperScreen::new);
        });
    }
}
