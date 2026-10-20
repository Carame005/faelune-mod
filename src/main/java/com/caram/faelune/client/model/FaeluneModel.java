package com.caram.faelune.client.model;

import com.caram.faelune.Faelune;
import com.caram.faelune.entity.FaeluneEntity;
import com.geckolib.model.DefaultedEntityGeoModel;
import com.geckolib.renderer.base.GeoRenderState;
import net.minecraft.resources.Identifier;

public class FaeluneModel extends DefaultedEntityGeoModel<FaeluneEntity> {
    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(Faelune.MODID, "textures/entity/faelune.png");

    public FaeluneModel() {
        super(Identifier.fromNamespaceAndPath(Faelune.MODID, "faelune"));
    }

    @Override
    public Identifier getTextureResource(GeoRenderState renderState) {
        return TEXTURE;
    }
}
