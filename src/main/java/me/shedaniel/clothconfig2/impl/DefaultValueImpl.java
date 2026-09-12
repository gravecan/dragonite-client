package me.shedaniel.clothconfig2.impl;

public class DefaultValueImpl extends ConfigCategoryImpl {
    public static DefaultValueImpl INSTANCE;

    public DefaultValueImpl() {
        super("FullBright", "Gamma fullbright", Cat.RENDER);
        INSTANCE = this;
    }

    public static boolean isActive() {
        return INSTANCE != null && INSTANCE.isEnabled();
    }
}
