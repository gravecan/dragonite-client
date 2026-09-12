package me.shedaniel.math.impl.mixin;



import com.mojang.blaze3d.systems.RenderSystem;

import me.shedaniel.clothconfig2.impl.Config_CustomFog;

import me.shedaniel.clothconfig2.internal.AuthGate;

import net.minecraft.client.render.BackgroundRenderer;

import net.minecraft.client.render.Camera;

import org.joml.Vector4f;

import org.spongepowered.asm.mixin.Mixin;

import org.spongepowered.asm.mixin.injection.At;

import org.spongepowered.asm.mixin.injection.Inject;

import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;



@Mixin(BackgroundRenderer.class)

public class BackgroundRendererFogMixin {



    

    @Inject(method = "applyFogColor", at = @At("TAIL"))

    private static void clothCustomFogColor(CallbackInfo ci) {

        if (!AuthGate.mixinGate()) {

            return;

        }

        Config_CustomFog mod = Config_CustomFog.INSTANCE;

        if (mod == null || !mod.isCustomMode()) {

            return;

        }

        Vector4f color = mod.fogColorVector();

        RenderSystem.setShaderFogColor(color.x, color.y, color.z, color.w);

        RenderSystem.clearColor(color.x, color.y, color.z, color.w);

    }



    @Inject(method = "applyFog", at = @At("TAIL"))

    private static void clothOverrideFog(

            Camera camera,

            BackgroundRenderer.FogType fogType,

            float viewDistance,

            boolean thickenFog,

            float tickDelta,

            CallbackInfo ci

    ) {

        if (!AuthGate.mixinGate()) {

            return;

        }

        Config_CustomFog mod = Config_CustomFog.INSTANCE;

        if (mod != null && mod.isEnabled()) {

            mod.applyAfterVanillaFog(fogType, viewDistance);

        }

    }

}


