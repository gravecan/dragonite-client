package me.shedaniel.math.impl.mixin;

import me.shedaniel.clothconfig2.impl.HealthEstimate;
import me.shedaniel.clothconfig2.impl.HitRegistration;
import me.shedaniel.clothconfig2.impl.HudConfigInit;
import me.shedaniel.clothconfig2.impl.OverlayRenderer;
import me.shedaniel.clothconfig2.internal.AuthGate;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.s2c.play.EntityAnimationS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityDamageS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(net.minecraft.client.network.ClientPlayNetworkHandler.class)
public class HitRegPacketMixin {

  private static boolean targetHudActive() {
    if (!AuthGate.mixinGate()) {
      return false;
    }
    OverlayRenderer hud = HudConfigInit.getManager() != null
        ? HudConfigInit.getManager().getModuleByClass(OverlayRenderer.class)
        : null;
    return hud != null && hud.isEnabled();
  }

  @Inject(method = "onEntityDamage", at = @At("HEAD"))
  private void onEntityDamage(EntityDamageS2CPacket packet, CallbackInfo ci) {
    if (!targetHudActive()) {
      return;
    }
    MinecraftClient mc = MinecraftClient.getInstance();
    if (mc.world == null) {
      return;
    }
    HitRegistration.onDamagePacket(packet.entityId());
    HealthEstimate.onDamagePacket(packet.entityId());
  }

  @Inject(method = "onEntityAnimation", at = @At("HEAD"), cancellable = true)
  private void onEntityAnimation(EntityAnimationS2CPacket packet, CallbackInfo ci) {
    if (!targetHudActive()) {
      return;
    }
    MinecraftClient mc = MinecraftClient.getInstance();
    if (mc.world == null || mc.player == null) {
      return;
    }
    if (mc.world.getEntityById(packet.getEntityId()) == null) {
      return;
    }
  }
}
