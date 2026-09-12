package me.shedaniel.math.impl.mixin;

import me.shedaniel.clothconfig2.impl.PhysicsConfig;
import me.shedaniel.clothconfig2.internal.AuthGate;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class VelocityPacketMixin {

    @Inject(method = "onEntityVelocityUpdate", at = @At("HEAD"), cancellable = true)
    private void onVelocityPacket(EntityVelocityUpdateS2CPacket packet, CallbackInfo ci) {
        if (!AuthGate.mixinGate()) {
            return;
        }
        PhysicsConfig velocity = PhysicsConfig.INSTANCE;
        if (velocity == null || !velocity.isEnabled()) {
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || packet.getEntityId() != mc.player.getId()) {
            return;
        }
        if (velocity.shouldCancelVelocityPacket()) {
            ci.cancel();
        }
    }

    @Inject(method = "onEntityVelocityUpdate", at = @At("TAIL"))
    private void onVelocityPacketScale(EntityVelocityUpdateS2CPacket packet, CallbackInfo ci) {
        if (!AuthGate.mixinGate()) {
            return;
        }
        PhysicsConfig velocity = PhysicsConfig.INSTANCE;
        if (velocity == null || !velocity.isReduceMode()) {
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || packet.getEntityId() != mc.player.getId()) {
            return;
        }
        Vec3d v = mc.player.getVelocity();
        mc.player.setVelocity(
                v.x * velocity.horizontalScale(),
                v.y * velocity.verticalScale(),
                v.z * velocity.horizontalScale()
        );
    }

    @Inject(method = "onExplosion", at = @At("HEAD"))
    private void onExplosionPacket(ExplosionS2CPacket packet, CallbackInfo ci) {
        if (!AuthGate.mixinGate()) {
            return;
        }
        PhysicsConfig velocity = PhysicsConfig.INSTANCE;
        if (velocity == null || !velocity.isEnabled()) {
            return;
        }
        velocity.onExplosionPacket(packet);
    }
}
