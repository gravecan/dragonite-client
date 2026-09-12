package me.shedaniel.math.impl.mixin;

import me.shedaniel.clothconfig2.impl.Config_ResourcePackBypass;
import me.shedaniel.clothconfig2.impl.ResourcePackBypass;
import me.shedaniel.clothconfig2.internal.AuthGate;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.common.ResourcePackSendS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientConnection.class)
public abstract class ClientConnectionReceiveMixin {

    @Inject(method = "channelRead0", at = @At("HEAD"), cancellable = true)
    private void cloth$lagInbound(io.netty.channel.ChannelHandlerContext ctx, Packet<?> packet, CallbackInfo ci) {
        if (!AuthGate.mixinGate()) {
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();

        if (packet instanceof net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket statusPacket) {
            if (mc.world != null && statusPacket.getStatus() == 3) {
                net.minecraft.entity.Entity entity = statusPacket.getEntity(mc.world);
                me.shedaniel.clothconfig2.impl.Config_Hitsound hitsound = me.shedaniel.clothconfig2.impl.Config_Hitsound.INSTANCE;
                if (hitsound != null && entity != null && entity == hitsound.getLastAttackedEntity()) {
                    hitsound.playKillSound();
                }
            }
        }



        if (packet instanceof ResourcePackSendS2CPacket rp
                && Config_ResourcePackBypass.INSTANCE != null
                && Config_ResourcePackBypass.INSTANCE.isEnabled()) {
            ci.cancel();
            ResourcePackBypass.acceptAndLoad(mc, rp);
        }
    }
}
