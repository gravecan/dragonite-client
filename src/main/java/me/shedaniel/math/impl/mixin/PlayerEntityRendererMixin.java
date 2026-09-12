package me.shedaniel.math.impl.mixin;

import me.shedaniel.clothconfig2.impl.Config_FloatList;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerEntityRendererMixin {

    private float cloth$origBodyYaw;
    private float cloth$origHeadYaw;
    private float cloth$origPrevBodyYaw;
    private float cloth$origPrevHeadYaw;
    private boolean cloth$appliedSilentYaw;

    @Inject(method = "render", at = @At("HEAD"))
    private void cloth$applySilentAimRotation(AbstractClientPlayerEntity player, float yaw, float tickDelta, MatrixStack matrices,
                                     VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || player != mc.player) return;
        if (mc.options.getPerspective().isFirstPerson()) return;
        
        Config_FloatList aimAssist = Config_FloatList.INSTANCE;
        
        
        float silentYaw = Float.NaN;
        
        
        if (aimAssist != null && aimAssist.isEnabled() && aimAssist.hasSilentRotation()) {
            silentYaw = aimAssist.getSilentYaw();
        }
        
        if (Float.isNaN(silentYaw)) return;
        
        
        cloth$appliedSilentYaw = true;
        cloth$origBodyYaw = player.bodyYaw;
        cloth$origHeadYaw = player.headYaw;
        cloth$origPrevBodyYaw = player.prevBodyYaw;
        cloth$origPrevHeadYaw = player.prevHeadYaw;

        player.bodyYaw = silentYaw;
        player.headYaw = silentYaw;
        player.prevBodyYaw = silentYaw;
        player.prevHeadYaw = silentYaw;
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void cloth$restoreSilentYaw(AbstractClientPlayerEntity player, float yaw, float tickDelta, MatrixStack matrices,
                                            VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) {
            return;
        }
        if (cloth$appliedSilentYaw && player == mc.player && !mc.options.getPerspective().isFirstPerson()) {
            player.bodyYaw = cloth$origBodyYaw;
            player.headYaw = cloth$origHeadYaw;
            player.prevBodyYaw = cloth$origPrevBodyYaw;
            player.prevHeadYaw = cloth$origPrevHeadYaw;
            cloth$appliedSilentYaw = false;
        }
    }
}
