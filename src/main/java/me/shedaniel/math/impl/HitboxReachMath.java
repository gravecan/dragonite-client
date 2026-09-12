package me.shedaniel.math.impl;

import me.shedaniel.clothconfig2.impl.Config_Selector;
import me.shedaniel.clothconfig2.impl.DistanceConfig;
import net.minecraft.client.MinecraftClient;

/**
 * Extra ray pick padding / reach slack when {@link Config_Selector} expands target boxes.
 * Mirrors SystemDLC {@code stretch(...).expand(1.0)} + corner slack for {@code ensureTargetInRange}.
 */
public final class HitboxReachMath {

    private static final double VANILLA_HALF_WIDTH = 0.3D;
    private static final double VANILLA_HEIGHT = 1.8D;
    private static final double RANGE_EPSILON = 0.08D;

    private HitboxReachMath() {}

    public static double halfWidthDelta() {
        Config_Selector hitbox = Config_Selector.getInstance();
        if (hitbox == null || !hitbox.isEnabled()) {
            return 0.0D;
        }
        return VANILLA_HALF_WIDTH * (hitbox.getWidthScale() / 100.0D);
    }

    public static double heightDelta() {
        Config_Selector hitbox = Config_Selector.getInstance();
        if (hitbox == null || !hitbox.isEnabled()) {
            return 0.0D;
        }
        return VANILLA_HEIGHT * (hitbox.getHeightScale() / 100.0D);
    }

    /** Slack for {@code GameRenderer.ensureTargetInRange} so top/side expanded corners are not clipped. */
    public static double rangeSlack() {
        double width = halfWidthDelta();
        double height = heightDelta();
        if (width <= 0.0D && height <= 0.0D) {
            return 0.0D;
        }
        return Math.hypot(width, height) + RANGE_EPSILON;
    }

    /** Added to vanilla {@code Box.expand(1,1,1)} in {@code findCrosshairTarget}. */
    public static double pickBoxPadding() {
        return Math.max(halfWidthDelta(), heightDelta());
    }

    public static double effectiveEntityRayLength(MinecraftClient mc) {
        double length = 3.0D;
        if (mc.player != null) {
            length = mc.player.getEntityInteractionRange();
        }
        DistanceConfig reach = DistanceConfig.getInstance();
        if (reach != null && reach.isEnabled()) {
            length = Math.max(length, reach.getRange());
        }
        return length + rangeSlack();
    }
}
