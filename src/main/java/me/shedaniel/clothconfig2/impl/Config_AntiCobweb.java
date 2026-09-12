package me.shedaniel.clothconfig2.impl;

import me.shedaniel.clothconfig2.impl.builders.DoubleFieldBuilder;

public class Config_AntiCobweb extends ConfigCategoryImpl {

    public static Config_AntiCobweb INSTANCE;
    
    private final DoubleFieldBuilder speed;

    public Config_AntiCobweb() {
        super("Anti Cobweb", "Bypasses cobweb slowdown", Cat.MOVEMENT);
        INSTANCE = this;
        speed = new DoubleFieldBuilder("Speed", "Cobweb movement multiplier", 1.0, 0.1, 1.0, 0.05);
        addSetting(speed);
    }
    
    public double getSpeed() {
        return speed.get();
    }
}
