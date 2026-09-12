package me.shedaniel.clothconfig2.impl;

import net.minecraft.client.MinecraftClient;


public class Config_NoHurtCam extends ConfigCategoryImpl {
    public static Config_NoHurtCam INSTANCE;

    public Config_NoHurtCam() {
        super("NoHurtCam", "Remove hurt camera shake", Cat.RENDER);
        INSTANCE = this;
    }

    public boolean shouldRemoveHurtCam() {
        return isEnabled();
    }

    public void tick(MinecraftClient mc) {
        
    }
}
