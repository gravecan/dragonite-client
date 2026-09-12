package me.shedaniel.math.impl.mixin;



import me.shedaniel.clothconfig2.impl.GlassHandsRender;

import me.shedaniel.clothconfig2.impl.HandGlState;

import net.minecraft.client.render.Camera;

import net.minecraft.client.render.GameRenderer;

import org.joml.Matrix4f;

import org.spongepowered.asm.mixin.Mixin;

import org.spongepowered.asm.mixin.injection.At;

import org.spongepowered.asm.mixin.injection.Inject;

import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;





@Mixin(GameRenderer.class)

public class GameRendererGlassMixin {



    @Inject(method = "renderHand", at = @At("HEAD"))

    private void clothGlassHandHead(Camera camera, float tickDelta, Matrix4f matrix4f, CallbackInfo ci) {

        if (GlassHandsRender.isActive()) {

            HandGlState.push((GameRenderer) (Object) this, camera, tickDelta);

            GlassHandsRender.beginHandPass();

        }

    }



    @Inject(method = "renderHand", at = @At("RETURN"))

    private void clothGlassHandReturn(Camera camera, float tickDelta, Matrix4f matrix4f, CallbackInfo ci) {

        if (GlassHandsRender.isActive()) {

            GlassHandsRender.endHandPass();

            HandGlState.pop((GameRenderer) (Object) this);

        }

    }

}


