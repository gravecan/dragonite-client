package me.shedaniel.clothconfig2.impl;

import me.shedaniel.clothconfig2.impl.builders.StringFieldBuilder;
import net.minecraft.client.MinecraftClient;

public class Config_NameProtect extends ConfigCategoryImpl {
    public static Config_NameProtect INSTANCE;
    
    private final StringFieldBuilder fakeName = new StringFieldBuilder("Fake Name", "Name to display", "Player", 16);
    
    public Config_NameProtect() {
        super("NameProtect", "Replaces your name with given one", Cat.MISC);
        INSTANCE = this;
        addSetting(fakeName);
    }
    
    public String getFakeName() {
        return fakeName.get();
    }
    
    public String getRealName() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            return mc.player.getName().getString();
        }
        return "";
    }
    
    public String protectName(String text) {
        if (!isEnabled()) return text;
        String real = getRealName();
        String fake = getFakeName();
        if (real.isEmpty() || fake.isEmpty()) return text;
        return text.replace(real, fake);
    }
}
