package me.shedaniel.clothconfig2.impl;

import me.shedaniel.clothconfig2.impl.builders.DoubleFieldBuilder;
import me.shedaniel.clothconfig2.impl.builders.EnumSelectorBuilder;
import me.shedaniel.math.impl.mixin.ExplosionVelocityAccessor;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;


public class PhysicsConfig extends ConfigCategoryImpl {
    public static PhysicsConfig INSTANCE;

    private final EnumSelectorBuilder mode = new EnumSelectorBuilder(
            "Mode",
            "No Knockback = zero KB | Reduce = % of KB",
            "Reduce",
            "No Knockback",
            "Reduce"
    );
    private final DoubleFieldBuilder horizontal = new DoubleFieldBuilder(
            "Horizontal %", "Knockback sideways (Reduce mode)", 0, 0, 100, 5
    );
    private final DoubleFieldBuilder vertical = new DoubleFieldBuilder(
            "Vertical %", "Knockback up/down (Reduce mode)", 0, 0, 100, 5
    );

    public PhysicsConfig() {
        super("Velocity", "Knockback control", Cat.MOVEMENT);
        INSTANCE = this;
        addSetting(mode);
        addSetting(horizontal);
        addSetting(vertical);

        horizontal.setVisibleWhen(() -> "Reduce".equals(mode.get()));
        vertical.setVisibleWhen(() -> "Reduce".equals(mode.get()));
    }

    public boolean isNoKnockbackMode() {
        return isEnabled() && "No Knockback".equals(mode.get());
    }

    public boolean isReduceMode() {
        return isEnabled() && "Reduce".equals(mode.get());
    }

    public boolean shouldCancelVelocityPacket() {
        return isNoKnockbackMode();
    }

    public double horizontalScale() {
        return horizontal.get() / 100.0;
    }

    public double verticalScale() {
        return vertical.get() / 100.0;
    }

    public void onExplosionPacket(ExplosionS2CPacket packet) {
        if (!isEnabled()) {
            return;
        }
        ExplosionVelocityAccessor accessor = (ExplosionVelocityAccessor) packet;
        if (isNoKnockbackMode()) {
            accessor.setPlayerVelocityX(0f);
            accessor.setPlayerVelocityY(0f);
            accessor.setPlayerVelocityZ(0f);
            return;
        }
        if (isReduceMode()) {
            accessor.setPlayerVelocityX((float) (accessor.getPlayerVelocityX() * horizontalScale()));
            accessor.setPlayerVelocityY((float) (accessor.getPlayerVelocityY() * verticalScale()));
            accessor.setPlayerVelocityZ((float) (accessor.getPlayerVelocityZ() * horizontalScale()));
        }
    }

    public double scaleClientKnockback(double value) {
        if (!isEnabled()) {
            return value;
        }
        if (isNoKnockbackMode()) {
            return 0.0;
        }
        if (isReduceMode()) {
            return value * horizontalScale();
        }
        return value;
    }

    public double scaleClientKnockbackVertical(double value) {
        if (!isEnabled()) {
            return value;
        }
        if (isNoKnockbackMode()) {
            return 0.0;
        }
        if (isReduceMode()) {
            return value * verticalScale();
        }
        return value;
    }
}
