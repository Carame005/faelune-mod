package com.caram.faelune.registry;

import com.caram.faelune.Faelune;
import com.caram.faelune.menu.FaeluneMenu;
import com.caram.faelune.menu.StellarBeaconMenu;
import com.caram.faelune.menu.StellarHopperMenu;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, Faelune.MODID);

    public static final RegistryObject<MenuType<FaeluneMenu>> FAELUNE_MENU = MENUS.register(
            "faelune",
            () -> IForgeMenuType.create(FaeluneMenu::new)
    );

    public static final RegistryObject<MenuType<StellarBeaconMenu>> STELLAR_BEACON_MENU = MENUS.register(
            "stellar_beacon",
            () -> IForgeMenuType.create(StellarBeaconMenu::new)
    );

    public static final RegistryObject<MenuType<StellarHopperMenu>> STELLAR_HOPPER_MENU = MENUS.register(
            "stellar_hopper",
            () -> new MenuType<>(StellarHopperMenu::new, FeatureFlags.VANILLA_SET)
    );

    private ModMenus() {
    }
}
