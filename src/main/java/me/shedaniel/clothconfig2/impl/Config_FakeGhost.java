package me.shedaniel.clothconfig2.impl;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;


public class Config_FakeGhost extends ConfigCategoryImpl {

    public static Config_FakeGhost INSTANCE;

    public Config_FakeGhost() {
        super("FakeGhost", "Shows a fake totem when you die", Cat.VISUALS);
        INSTANCE = this;
    }

    public static ItemStack overrideFirstPersonStack(ItemStack original, boolean playerAlive) {
        if (INSTANCE == null || !INSTANCE.isEnabled() || playerAlive) {
            return original;
        }
        return new ItemStack(Items.TOTEM_OF_UNDYING);
    }
}
