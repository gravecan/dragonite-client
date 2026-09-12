package me.shedaniel.math.impl.mixin;

import me.shedaniel.clothconfig2.impl.GlassHandsRender;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(ItemRenderer.class)
public class ItemRendererGlassMixin {

    @Inject(
            method = "renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;IILnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/world/World;I)V",
            at = @At("HEAD")
    )
    private void clothGlassItemHead(
            ItemStack stack,
            ModelTransformationMode renderMode,
            int light,
            int overlay,
            net.minecraft.client.util.math.MatrixStack matrices,
            net.minecraft.client.render.VertexConsumerProvider vertexConsumers,
            net.minecraft.world.World world,
            int seed,
            CallbackInfo ci) {
        if (GlassHandsRender.isHandPass() && GlassHandsRender.isFirstPersonMode(renderMode)) {
            GlassHandsRender.setItemFallback(stack);
        }
    }

    @Inject(
            method = "renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;IILnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/world/World;I)V",
            at = @At("RETURN")
    )
    private void clothGlassItemReturn(
            ItemStack stack,
            ModelTransformationMode renderMode,
            int light,
            int overlay,
            net.minecraft.client.util.math.MatrixStack matrices,
            net.minecraft.client.render.VertexConsumerProvider vertexConsumers,
            net.minecraft.world.World world,
            int seed,
            CallbackInfo ci) {
        if (GlassHandsRender.isHandPass() && GlassHandsRender.isFirstPersonMode(renderMode)) {
            GlassHandsRender.clearItemFallback();
        }
    }
}
