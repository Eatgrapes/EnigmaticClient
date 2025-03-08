package com.github.eatgrapes.enigmaticclient.module.modules;

import com.github.eatgrapes.enigmaticclient.module.Module;
import net.minecraft.client.Minecraft;

public class FullBright extends Module {
    private float originalGamma = 1.0F;
    private boolean gammaStored = false;

    public FullBright() {
        super("FullBright");
    }

    @Override
    public void onEnable() {
        if (!gammaStored) {
            originalGamma = Minecraft.getMinecraft().gameSettings.gammaSetting;
            gammaStored = true;
        }
        Minecraft.getMinecraft().gameSettings.gammaSetting = 16.0F;
    }

    @Override
    public void onDisable() {
        Minecraft.getMinecraft().gameSettings.gammaSetting = originalGamma;
        gammaStored = false;
    }

    @Override
    public void onUpdate() {
        if (this.isEnabled()) {
            //Force setting of gamma
            Minecraft.getMinecraft().gameSettings.gammaSetting = 16.0F;
        }
    }
}