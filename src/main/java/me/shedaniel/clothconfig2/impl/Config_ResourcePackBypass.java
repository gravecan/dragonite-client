package me.shedaniel.clothconfig2.impl;

import net.minecraft.client.MinecraftClient;

public class Config_ResourcePackBypass extends ConfigCategoryImpl {
    public static Config_ResourcePackBypass INSTANCE;

    public Config_ResourcePackBypass() {
        super("AntiResourcePack", "Skips forced server resource packs", Cat.MISC);
        INSTANCE = this;
        setEnabled(false);
    }
}
