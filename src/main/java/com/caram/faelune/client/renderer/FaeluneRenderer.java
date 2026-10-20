package com.caram.faelune.client.renderer;

import com.caram.faelune.client.model.FaeluneModel;
import com.caram.faelune.entity.FaeluneEntity;
import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.layer.builtin.ItemInHandGeoLayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

public class FaeluneRenderer<R extends LivingEntityRenderState & GeoRenderState>
        extends GeoEntityRenderer<FaeluneEntity, R> {
    public FaeluneRenderer(EntityRendererProvider.Context context) {
        super(context, new FaeluneModel());
        this.withRenderLayer(new ItemInHandGeoLayer<>(context, this, "right_hand", "left_hand"));
    }
}
