package me.shedaniel.math.impl.mixin;

import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Camera.class)
public interface CameraPosAccessor {
    @Invoker("setPos")
    void cloth$setPos(Vec3d pos);
}
