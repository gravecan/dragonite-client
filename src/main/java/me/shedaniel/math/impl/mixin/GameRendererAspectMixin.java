package me.shedaniel.math.impl.mixin;

import me.shedaniel.clothconfig2.impl.AspectRatioRenderGate;
import me.shedaniel.clothconfig2.impl.Config_AspectRatio;
import me.shedaniel.clothconfig2.internal.AuthGate;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;


@Mixin(GameRenderer.class)
public abstract class GameRendererAspectMixin {

    @Shadow @Final private MinecraftClient client;
    @Shadow private float zoom;
    @Shadow private float zoomX;
    @Shadow private float zoomY;

    @Shadow
    protected abstract float getFarPlaneDistance();

    @Inject(method = "renderHand", at = @At("HEAD"))
    private void clothAspectHandBegin(Camera camera, float tickDelta, Matrix4f viewMatrix, CallbackInfo ci) {
        AspectRatioRenderGate.beginHandPass();
    }

    @Inject(method = "renderHand", at = @At("RETURN"))
    private void clothAspectHandEnd(Camera camera, float tickDelta, Matrix4f viewMatrix, CallbackInfo ci) {
        AspectRatioRenderGate.endHandPass();
    }

    @Inject(method = "getBasicProjectionMatrix(D)Lorg/joml/Matrix4f;", at = @At("HEAD"), cancellable = true)
    private void clothCustomAspect(double fov, CallbackInfoReturnable<Matrix4f> cir) {
        if (!AuthGate.mixinGate()) {
            return;
        }
        if (AspectRatioRenderGate.isHandPass()) {
            return;
        }
        Config_AspectRatio mod = Config_AspectRatio.INSTANCE;
        if (mod == null || !mod.isEnabled()) {
            return;
        }

        Matrix4f matrix = new Matrix4f();
        if (zoom != 1.0F) {
            matrix.translate(zoomX, -zoomY, 0.0F);
            matrix.scale(zoom, zoom, 1.0F);
        }

        float aspect = mod.resolveWorldAspect(client);
        float fovRad = (float) fov * (float) (Math.PI / 180.0);
        cir.setReturnValue(matrix.perspective(fovRad, aspect, 0.05F, getFarPlaneDistance()));
    }
}
