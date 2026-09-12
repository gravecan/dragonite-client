package me.shedaniel.math.impl.mixin;

import me.shedaniel.clothconfig2.impl.DefaultValueImpl;
import me.shedaniel.clothconfig2.internal.AuthGate;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LightmapTextureManager.class)
public class LightmapTextureManagerMixin {

    @Inject(method = "update", at = @At("HEAD"), cancellable = true)
    private void clothFullbrightLightmap(float tickDelta, CallbackInfo ci) {
        if (!AuthGate.mixinGate() || !DefaultValueImpl.isActive()) {
            return;
        }

        LightmapTextureManager self = (LightmapTextureManager) (Object) this;
        NativeImage image = ((LightmapTextureManagerAccessor) self).cloth$getImage();
        NativeImageBackedTexture texture = ((LightmapTextureManagerAccessor) self).cloth$getTexture();
        if (image == null || texture == null) {
            return;
        }

        for (int block = 0; block < 16; block++) {
            for (int sky = 0; sky < 16; sky++) {
                image.setColor(block, sky, 0xFFFFFFFF);
            }
        }
        texture.upload();
        ((LightmapTextureManagerAccessor) self).cloth$setDirty(false);
        ci.cancel();
    }

    @Inject(method = "getDarknessFactor", at = @At("HEAD"), cancellable = true)
    private void clothFullbrightNoDarkness(float tickDelta, CallbackInfoReturnable<Float> cir) {
        if (AuthGate.mixinGate() && DefaultValueImpl.isActive()) {
            cir.setReturnValue(0f);
        }
    }
}
