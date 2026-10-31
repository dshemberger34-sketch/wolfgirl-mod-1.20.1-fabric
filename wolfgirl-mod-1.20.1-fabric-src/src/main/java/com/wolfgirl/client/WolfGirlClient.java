package com.wolfgirl.client;

import com.wolfgirl.WolfGirlMod;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

public class WolfGirlClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(WolfGirlMod.WOLF_GIRL, WolfGirlRenderer::new);
    }
}
