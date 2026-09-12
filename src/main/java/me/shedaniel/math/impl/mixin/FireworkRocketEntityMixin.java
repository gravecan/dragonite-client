package me.shedaniel.math.impl.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.shedaniel.clothconfig2.impl.Config_ElytraBoost;
import me.shedaniel.clothconfig2.internal.AuthGate;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(FireworkRocketEntity.class)
public abstract class FireworkRocketEntityMixin {

    @Shadow
    @Nullable
    private LivingEntity shooter;

    @Unique
    private Vec3d cloth$rotation;

    @WrapOperation(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/LivingEntity;getRotationVector()Lnet/minecraft/util/math/Vec3d;"
            )
    )
    private Vec3d cloth$captureRotation(LivingEntity instance, Operation<Vec3d> original) {
        Vec3d rot = original.call(instance);
        cloth$rotation = rot;
        return rot;
    }

    @WrapOperation(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/LivingEntity;setVelocity(Lnet/minecraft/util/math/Vec3d;)V"
            )
    )
    private void cloth$elytraBoost(
            LivingEntity instance,
            Vec3d velocity,
            Operation<Void> original
    ) {
        if (!AuthGate.mixinGate()) {
            original.call(instance, velocity);
            return;
        }

        Config_ElytraBoost module = Config_ElytraBoost.INSTANCE;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (module == null
                || !module.shouldBoost()
                || shooter != mc.player
                || !instance.isFallFlying()
                || cloth$rotation == null) {
            original.call(instance, velocity);
            return;
        }

        Vec3d motion = instance.getVelocity();
        original.call(instance, module.applyBoost(cloth$rotation, motion));
    }
}
