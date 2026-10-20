package com.caram.faelune.registry;

import com.caram.faelune.Faelune;
import com.caram.faelune.item.FaeluneGuideItem;
import com.caram.faelune.item.StellarTunerItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, Faelune.MODID);

    public static final RegistryObject<Item> FAELUNE_SPAWN_EGG = ITEMS.register(
            "faelune_spawn_egg",
            () -> new SpawnEggItem(new Item.Properties()
                    .setId(ITEMS.key("faelune_spawn_egg"))
                    .spawnEgg(ModEntities.FAELUNE.get()))
    );

    public static final RegistryObject<Item> STELLAR_BEACON = ITEMS.register(
            "stellar_beacon",
            () -> new BlockItem(ModBlocks.STELLAR_BEACON.get(), new Item.Properties()
                    .setId(ITEMS.key("stellar_beacon"))
                    .useBlockDescriptionPrefix()
                    .rarity(Rarity.UNCOMMON))
    );

    public static final RegistryObject<Item> STELLAR_HOPPER = ITEMS.register(
            "stellar_hopper",
            () -> new BlockItem(ModBlocks.STELLAR_HOPPER.get(), new Item.Properties()
                    .setId(ITEMS.key("stellar_hopper"))
                    .useBlockDescriptionPrefix()
                    .rarity(Rarity.UNCOMMON))
    );

    public static final RegistryObject<Item> STELLAR_TUNER = ITEMS.register(
            "stellar_tuner",
            () -> new StellarTunerItem(new Item.Properties()
                    .setId(ITEMS.key("stellar_tuner"))
                    .stacksTo(1)
                    .rarity(Rarity.UNCOMMON))
    );

    /** Tames on the first try and fully heals Faelune. */
    public static final RegistryObject<Item> STELLAR_COOKIE = ITEMS.register(
            "stellar_cookie",
            () -> new Item(new Item.Properties()
                    .setId(ITEMS.key("stellar_cookie"))
                    .rarity(Rarity.RARE))
    );

    public static final RegistryObject<Item> FAELUNE_GUIDE = ITEMS.register(
            "faelune_guide",
            () -> new FaeluneGuideItem(new Item.Properties()
                    .setId(ITEMS.key("faelune_guide"))
                    .stacksTo(1)
                    .rarity(Rarity.UNCOMMON))
    );

    private ModItems() {
    }
}
