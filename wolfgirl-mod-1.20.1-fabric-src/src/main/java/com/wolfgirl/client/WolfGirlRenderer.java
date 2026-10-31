package com.wolfgirl.client;

import com.wolfgirl.WolfGirlEntity;
import com.wolfgirl.WolfGirlMod;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.util.Identifier;

public class WolfGirlRenderer extends MobEntityRenderer<WolfGirlEntity, WolfGirlModel> {
    private static final Identifier TEXTURE = new Identifier(WolfGirlMod.MOD_ID, "textures/entity/wolf_girl.png");

    public WolfGirlRenderer(EntityRendererFactory.Context context) {
        super(context, new WolfGirlModel(), 0.5f);
    }

    @Override
    public Identifier getTexture(WolfGirlEntity entity) {
        return TEXTURE;
    }
}
