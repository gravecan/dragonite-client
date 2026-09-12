package me.shedaniel.math.impl.mixin;

import me.shedaniel.clothconfig2.impl.PhysicsConfig;
import me.shedaniel.clothconfig2.internal.AuthGate;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Entity.class)
public abstract class KnockbackVelocityMixin {

    @ModifyVariable(method = "setVelocityClient", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private double clothconfig$scaleKnockbackX(double x) {
        return scale(x);
    }

    @ModifyVariable(method = "setVelocityClient", at = @At("HEAD"), ordinal = 1, argsOnly = true)
    private double clothconfig$scaleKnockbackY(double y) {
        return scaleVertical(y);
    }

    @ModifyVariable(method = "setVelocityClient", at = @At("HEAD"), ordinal = 2, argsOnly = true)
    private double clothconfig$scaleKnockbackZ(double z) {
        return scale(z);
    }

    private double scale(double value) {
        if (!AuthGate.mixinGate()) {
            return value;
        }
        PhysicsConfig cfg = PhysicsConfig.INSTANCE;
        if (cfg == null || !cfg.isEnabled()) {
            return value;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || (Object) this != mc.player) {
            return value;
        }
        return cfg.scaleClientKnockback(value);
    }

    private double scaleVertical(double value) {
        if (!AuthGate.mixinGate()) {
            return value;
        }
        PhysicsConfig cfg = PhysicsConfig.INSTANCE;
        if (cfg == null || !cfg.isEnabled()) {
            return value;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || (Object) this != mc.player) {
            return value;
        }
        return cfg.scaleClientKnockbackVertical(value);
    }
}
