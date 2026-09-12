package me.shedaniel.math.impl.mixin;



import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import com.llamalad7.mixinextras.sugar.Local;

import me.shedaniel.clothconfig2.impl.Config_FakeGhost;
import me.shedaniel.clothconfig2.impl.GlassHandsRender;

import me.shedaniel.clothconfig2.impl.HandViewHelper;

import net.minecraft.client.network.AbstractClientPlayerEntity;

import net.minecraft.client.render.VertexConsumerProvider;

import net.minecraft.client.render.item.HeldItemRenderer;

import net.minecraft.client.util.math.MatrixStack;

import net.minecraft.item.ItemStack;

import net.minecraft.util.Arm;

import net.minecraft.util.Hand;

import org.spongepowered.asm.mixin.Mixin;

import org.spongepowered.asm.mixin.Unique;

import org.spongepowered.asm.mixin.injection.At;

import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;



@Mixin(HeldItemRenderer.class)

public class HeldItemRendererMixin {



    @Unique

    private Hand cloth$hand;



    @Unique

    private AbstractClientPlayerEntity cloth$player;



    @Unique

    private float cloth$swingProgress;



    @Unique

    private float cloth$equipProgress;



    @Unique

    private boolean cloth$customSwing;



    @Inject(method = "renderFirstPersonItem", at = @At("HEAD"))

    private void clothCaptureHandContext(

            AbstractClientPlayerEntity player,

            float tickDelta,

            float pitch,

            Hand hand,

            float swingProgress,

            ItemStack item,

            float equipProgress,

            MatrixStack matrices,

            VertexConsumerProvider vertexConsumers,

            int light,

            CallbackInfo ci) {

        cloth$hand = hand;

        cloth$player = player;

        cloth$swingProgress = swingProgress;

        cloth$equipProgress = equipProgress;

        cloth$customSwing = false;

    }

    @ModifyArgs(
            method = "renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;Lnet/minecraft/client/network/ClientPlayerEntity;I)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/item/HeldItemRenderer;renderFirstPersonItem(Lnet/minecraft/client/network/AbstractClientPlayerEntity;FFLnet/minecraft/util/Hand;FLnet/minecraft/item/ItemStack;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
                    ordinal = 1
            )
    )
    private void clothFakeGhostItem(Args args) {
        AbstractClientPlayerEntity player = args.get(0);
        ItemStack stack = args.get(5);
        args.set(5, Config_FakeGhost.overrideFirstPersonStack(stack, player == null || player.isAlive()));
    }



    @Inject(

            method = "renderFirstPersonItem",

            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/math/MatrixStack;push()V", shift = At.Shift.AFTER))

    private void clothAfterHandPush(

            AbstractClientPlayerEntity player,

            float tickDelta,

            float pitch,

            Hand hand,

            float swingProgress,

            ItemStack item,

            float equipProgress,

            MatrixStack matrices,

            VertexConsumerProvider vertexConsumers,

            int light,

            CallbackInfo ci) {

        HandViewHelper.applyViewModel(matrices, hand, player);

        GlassHandsRender.setItemFallback(item);

    }



    @Inject(method = "renderFirstPersonItem", at = @At("RETURN"))

    private void clothClearHandItem(

            AbstractClientPlayerEntity player,

            float tickDelta,

            float pitch,

            Hand hand,

            float swingProgress,

            ItemStack item,

            float equipProgress,

            MatrixStack matrices,

            VertexConsumerProvider vertexConsumers,

            int light,

            CallbackInfo ci) {

        GlassHandsRender.clearItemFallback();

        cloth$hand = null;

        cloth$player = null;

        cloth$customSwing = false;

    }



    @WrapOperation(

            method = "renderFirstPersonItem",

            at = @At(

                    value = "INVOKE",

                    target = "Lnet/minecraft/client/render/item/HeldItemRenderer;applyEquipOffset(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/util/Arm;F)V"))

    private void clothWrapEquipOffset(

            HeldItemRenderer instance,

            MatrixStack matrices,

            Arm arm,

            float equipProgress,

            Operation<Void> original,

            @Local(argsOnly = true) AbstractClientPlayerEntity player,

            @Local(argsOnly = true) Hand hand,

            @Local(ordinal = 0, argsOnly = true) float swingProgress) {

        if (player.isUsingItem() && player.getActiveHand() == hand) {

            cloth$customSwing = false;

            original.call(instance, matrices, arm, equipProgress);

            return;

        }



        if (HandViewHelper.shouldApplyCustomSwing(player, hand, swingProgress)) {

            cloth$customSwing = true;

            HandViewHelper.applyCustomSwing(matrices, arm, swingProgress, equipProgress, player);

            return;

        }

        HandViewHelper.updateSwingState(player, swingProgress);



        cloth$customSwing = false;

        original.call(instance, matrices, arm, equipProgress);

    }



    @Inject(method = "applySwingOffset", at = @At("HEAD"), cancellable = true)

    private void clothSkipVanillaSwing(MatrixStack matrices, Arm arm, float swingProgress, CallbackInfo ci) {

        if (cloth$customSwing) {

            ci.cancel();

        }

    }

    @Inject(method = "applySwingOffset", at = @At("RETURN"))
    private void clothAfterVanillaSwing(MatrixStack matrices, Arm arm, float swingProgress, CallbackInfo ci) {
        if (!cloth$customSwing) {
            HandViewHelper.applyViewModelScale(matrices);
        }
    }

}

