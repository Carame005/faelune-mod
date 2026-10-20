package com.caram.faelune.registry;

import com.caram.faelune.Faelune;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Faelune.MODID);

    public static final RegistryObject<CreativeModeTab> FAELUNE_TAB = CREATIVE_TABS.register(
            "faelune",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.faelune"))
                    .icon(() -> new ItemStack(ModItems.STELLAR_BEACON.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.FAELUNE_GUIDE.get());
                        output.accept(ModItems.FAELUNE_SPAWN_EGG.get());
                        output.accept(ModItems.STELLAR_COOKIE.get());
                        output.accept(ModItems.STELLAR_TUNER.get());
                        output.accept(ModItems.STELLAR_BEACON.get());
                        output.accept(ModItems.STELLAR_HOPPER.get());
                    })
                    .build()
    );

    private ModCreativeTabs() {
    }
}
