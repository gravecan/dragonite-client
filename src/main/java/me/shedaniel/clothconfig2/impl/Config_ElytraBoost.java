package me.shedaniel.clothconfig2.impl;

import me.shedaniel.clothconfig2.impl.builders.DoubleFieldBuilder;
import net.minecraft.util.math.Vec3d;


public class Config_ElytraBoost extends ConfigCategoryImpl {

    public static Config_ElytraBoost INSTANCE;

    private static final double VANILLA = 1.5;

    private final DoubleFieldBuilder boost;

    public Config_ElytraBoost() {
        super("Elytra Boost", "Stronger firework boost while gliding", Cat.MOVEMENT);
        INSTANCE = this;
        setTooltip("Use with manual rockets or AutoFirework. Vanilla boost is 1.5 — raise Speed to fly faster.");
        boost = new DoubleFieldBuilder("Speed", "Horizontal firework boost", 1.9, 1.5, 5.0, 0.05);
        addSetting(boost);
    }

    public boolean shouldBoost() {
        return isEnabled();
    }

    public Vec3d applyBoost(Vec3d rotation, Vec3d motion) {
        double speedXZ = boost.get();
        double speedY = speedXZ;
        return motion.add(
                rotation.x * 0.1 + (rotation.x * speedXZ - motion.x) * 0.5,
                rotation.y * 0.1 + (rotation.y * speedY - motion.y) * 0.5,
                rotation.z * 0.1 + (rotation.z * speedXZ - motion.z) * 0.5
        );
    }

    public double getBoostMultiplier() {
        return boost.get() / VANILLA;
    }
}
