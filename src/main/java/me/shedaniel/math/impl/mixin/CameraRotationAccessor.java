package me.shedaniel.math.impl.mixin;

import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Camera.class)
public interface CameraRotationAccessor {
    @Invoker("setRotation")
    void cloth$setRotation(float yaw, float pitch);
}
