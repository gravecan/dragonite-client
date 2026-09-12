package me.shedaniel.clothconfig2.impl;

import me.shedaniel.clothconfig2.impl.builders.DoubleFieldBuilder;

public class Config_ViewModel extends ConfigCategoryImpl {
    public static Config_ViewModel INSTANCE;

    private final DoubleFieldBuilder x;
    private final DoubleFieldBuilder y;
    private final DoubleFieldBuilder z;
    private final DoubleFieldBuilder scale;

    public Config_ViewModel() {
        super("View Model", "Hand position and scale", Cat.VISUALS);
        INSTANCE = this;
        x = new DoubleFieldBuilder("X", "Hand X offset", 0, -2, 2, 0.01);
        y = new DoubleFieldBuilder("Y", "Hand Y offset", 0, -2, 2, 0.01);
        z = new DoubleFieldBuilder("Z", "Hand Z offset", 0, -2, 2, 0.01);
        scale = new DoubleFieldBuilder("Scale", "Hand scale", 1, 0.1, 3, 0.01);
        addSetting(x);
        addSetting(y);
        addSetting(z);
        addSetting(scale);
    }

    public double x() { return x.get(); }
    public double y() { return y.get(); }
    public double z() { return z.get(); }
    public double scale() { return scale.get(); }
}
