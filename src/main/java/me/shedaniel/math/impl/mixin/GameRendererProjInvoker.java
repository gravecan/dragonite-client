package me.shedaniel.math.impl.mixin;

import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(GameRenderer.class)
public interface GameRendererProjInvoker {

    @Invoker("getFov")
    double cloth$getFov(Camera camera, float tickDelta, boolean changingFov);

    @Invoker("getBasicProjectionMatrix")
    Matrix4f cloth$getBasicProjectionMatrix(double fov);

    @Invoker("loadProjectionMatrix")
    void cloth$loadProjectionMatrix(Matrix4f matrix);
}
