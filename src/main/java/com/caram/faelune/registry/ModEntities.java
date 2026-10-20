package com.caram.faelune.registry;

import com.caram.faelune.Faelune;
import com.caram.faelune.entity.FaeluneEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, Faelune.MODID);

    public static final RegistryObject<EntityType<FaeluneEntity>> FAELUNE = ENTITY_TYPES.register(
            "faelune",
            () -> EntityType.Builder.of(FaeluneEntity::new, MobCategory.CREATURE)
                    .sized(0.6f, 1.8f)
                    .clientTrackingRange(10)
                    .build(ENTITY_TYPES.key("faelune"))
    );

    private ModEntities() {
    }

    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(FAELUNE.get(), FaeluneEntity.createAttributes().build());
    }
}
