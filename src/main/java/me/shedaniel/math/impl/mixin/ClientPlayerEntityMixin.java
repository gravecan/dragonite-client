package me.shedaniel.math.impl.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import me.shedaniel.clothconfig2.impl.Config_BoatFly;
import me.shedaniel.clothconfig2.impl.Config_DropdownBox;
import me.shedaniel.clothconfig2.impl.Config_AirStuck;
import me.shedaniel.clothconfig2.impl.Config_AutoFirework;
import me.shedaniel.clothconfig2.impl.Config_FreeCam;
import me.shedaniel.clothconfig2.impl.Config_NoFall;
import me.shedaniel.clothconfig2.impl.Config_Hitsound;
import me.shedaniel.clothconfig2.impl.MovementPacketHooks;
import me.shedaniel.clothconfig2.internal.AuthGate;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.network.packet.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;


@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerEntityMixin {

    @ModifyExpressionValue(
            method = "sendMovementPackets",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;getYaw()F")
    )
    private float cloth$redirectPacketYaw(float original) {
        if (!AuthGate.mixinGate()) {
            return original;
        }
        Config_FreeCam fc = Config_FreeCam.INSTANCE;
        if (fc != null && fc.isEnabled()) {
            return fc.getBodyYaw();
        }
        return original;
    }

    @ModifyExpressionValue(
            method = "sendMovementPackets",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;getPitch()F")
    )
    private float cloth$redirectPacketPitch(float original) {
        if (!AuthGate.mixinGate()) {
            return original;
        }
        Config_FreeCam fc = Config_FreeCam.INSTANCE;
        if (fc != null && fc.isEnabled()) {
            return fc.getBodyPitch();
        }
        return original;
    }

    @Inject(method = "tickMovement", at = @At("HEAD"))
    private void cloth$freeCamFreezeHead(CallbackInfo ci) {
        if (!AuthGate.mixinGate()) {
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        Config_FreeCam fc = Config_FreeCam.INSTANCE;
        if (fc != null && fc.isEnabled() && mc.player != null) {
            fc.tick(mc);
            fc.freezeBody((ClientPlayerEntity) (Object) this);
        }
    }

    @Inject(method = "tickMovement", at = @At("RETURN"))
    private void cloth$freeCamFreezeBody(CallbackInfo ci) {
        if (!AuthGate.mixinGate()) {
            return;
        }
        Config_FreeCam fc = Config_FreeCam.INSTANCE;
        if (fc != null && fc.isEnabled()) {
            fc.freezeBody((ClientPlayerEntity) (Object) this);
        }
    }

    @Inject(method = "sendMovementPackets", at = @At("HEAD"))
    private void onSendMovementPacketsHead(CallbackInfo ci) {
        if (!AuthGate.mixinGate()) {
            return;
        }
        // Grim/Vulcan Post: HeldItemChange / Attack / UseItem after flying = flag.
        // Flush silent slot restores and combat actions BEFORE any move packet.
        me.shedaniel.clothconfig2.impl.HudConfigInit.onBeforeMovementPackets(
                MinecraftClient.getInstance());
    }

    @Inject(method = "sendMovementPackets", at = @At("TAIL"))
    private void onSendMovementPacketsTail(CallbackInfo ci) {
        
    }

    @Redirect(method = "sendMovementPackets", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayNetworkHandler;sendPacket(Lnet/minecraft/network/packet/Packet;)V"))
    private void redirectSendPacket(net.minecraft.client.network.ClientPlayNetworkHandler handler, Packet<?> packet) {
        packet = Config_BoatFly.sanitizeOutboundPacket(packet);
        if (!MovementPacketHooks.shouldIntercept(packet)) {
            handler.sendPacket(packet);
            return;
        }
        if (MovementPacketHooks.isNoFallActive()) {
            packet = Config_NoFall.INSTANCE.handleOutbound(packet);
        }
        if (MovementPacketHooks.isFreeCamActive()
                && !Config_FreeCam.isAnchorSend()
                && Config_FreeCam.INSTANCE.handleOutbound(packet)) {
            return;
        }
        if (MovementPacketHooks.isAirStuckActive()
                && !Config_AirStuck.isAnchorSend()
                && Config_AirStuck.INSTANCE.handleOutbound(packet)) {
            return;
        }
        handler.sendPacket(packet);
    }

  
    @Inject(method = "tickRiding", at = @At("HEAD"), require = 0)
    private void cloth$boatFlyBlockShiftDismount(CallbackInfo ci) {
        if (!AuthGate.mixinGate()) {
            return;
        }
        Config_BoatFly boatFly = Config_BoatFly.INSTANCE;
        if (boatFly == null || !boatFly.isBlockingShiftDismount()) {
            return;
        }
        ClientPlayerEntity self = (ClientPlayerEntity) (Object) this;
        if (self.getVehicle() instanceof BoatEntity) {
            self.setSneaking(false);
            if (self.input != null) {
                self.input.sneaking = false;
            }
        }
    }

    @Inject(method = "isSneaking", at = @At("HEAD"), cancellable = true, require = 0)
    private void cloth$boatFlyFakeNotSneaking(CallbackInfoReturnable<Boolean> cir) {
        if (!AuthGate.mixinGate()) {
            return;
        }
        Config_BoatFly boatFly = Config_BoatFly.INSTANCE;
        if (boatFly == null || !boatFly.isBlockingShiftDismount()) {
            return;
        }
        ClientPlayerEntity self = (ClientPlayerEntity) (Object) this;
        if (self.getVehicle() instanceof BoatEntity) {
            cir.setReturnValue(false);
        }
    }

    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayNetworkHandler;sendPacket(Lnet/minecraft/network/packet/Packet;)V"))
    private void cloth$boatFlySanitizeRidingInputPacket(
            net.minecraft.client.network.ClientPlayNetworkHandler handler,
            Packet<?> packet
    ) {
        handler.sendPacket(Config_BoatFly.sanitizeOutboundPacket(packet));
    }

    @Inject(method = "swingHand", at = @At("HEAD"))
    private void cloth$onSwingHand(net.minecraft.util.Hand hand, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.crosshairTarget instanceof net.minecraft.util.hit.EntityHitResult entityHit) {
            Config_Hitsound hitsound = Config_Hitsound.INSTANCE;
            if (hitsound != null) {
                hitsound.setLastAttacked(entityHit.getEntity());
                hitsound.playHitSound();
            }
        }
    }
}
