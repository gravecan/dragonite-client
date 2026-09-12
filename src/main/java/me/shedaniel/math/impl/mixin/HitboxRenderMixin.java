package me.shedaniel.math.impl.mixin;

import me.shedaniel.clothconfig2.impl.Config_Selector;
import me.shedaniel.clothconfig2.impl.Config_SubCategoryList;
import me.shedaniel.clothconfig2.internal.AuthGate;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.Coerce;

@Mixin(net.minecraft.client.render.WorldRenderer.class)
public class HitboxRenderMixin {

    @Inject(method = "render", at = @At("HEAD"))
    private void onRenderHead(@Coerce Object frameGraph, boolean renderBlockOutline,
                              Camera camera, GameRenderer gameRenderer,
                              LightmapTextureManager lightmapTextureManager,
                              Matrix4f positionMatrix, Matrix4f projectionMatrix,
                              CallbackInfo ci) {
        if (!AuthGate.mixinGate()) {
            return;
        }
        applyExpandedBoundingBoxes();
    }

    private void applyExpandedBoundingBoxes() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) {
            return;
        }

        Config_Selector hitbox = Config_Selector.getInstance();
        Config_SubCategoryList staticHb = Config_SubCategoryList.getInstance();

        boolean hitboxOn = hitbox != null && hitbox.isEnabled();
        boolean staticOn = staticHb != null && staticHb.isEnabled();
        if (!hitboxOn && !staticOn) {
            return;
        }

        float tickDelta = mc.getRenderTickCounter().getTickDelta(true);

        for (net.minecraft.entity.Entity entity : mc.world.getEntities()) {
            if (entity == mc.player || !entity.isAlive() || !(entity instanceof net.minecraft.entity.LivingEntity living)) {
                continue;
            }

            if (living instanceof PlayerEntity player && staticOn && player.isFallFlying()) {
                Box staticBox = staticHb.getStaticBox(player, tickDelta);
                if (staticBox != null) {
                    player.setBoundingBox(staticBox);
                }
                continue;
            }

            if (hitboxOn) {
                Box expanded = hitbox.computeExpandedBox(living, tickDelta);
                if (expanded != null) {
                    living.setBoundingBox(expanded);
                }
            }
        }
    }
}
